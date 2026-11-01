(ns domain.lesson
  (:require
   [clojure.string :as str]
   [domain.examples :as examples]
   [utils :as utils]))


(def default-vocab-per-lesson 3)


(defn lesson-words
  "The `size` words a lesson takes from `recalls`, `[word log-recall]` pairs (`domain.retention/log-recall`),
   the least likely recalled first. A word whose recall is unknown (nil) has never been reviewed and comes
   first; NaN comes last. Words that tie are ordered by `tie-breaker`, a function of a word to a number,
   smaller first — random in the app, so a tied vocabulary is never cut alphabetically. One pass keeps the
   first `size`; the vocabulary is not sorted."
  [recalls size tie-breaker]
  (let [;; How overdue a word is, the larger the more: minus its log-recall; unknown is the most, NaN the least.
        ;; `##Inf` is compared, never subtracted.
        overdue       (fn [log-recall]
                        (cond
                          (nil? log-recall) ##Inf
                          (js/Number.isNaN log-recall) ##-Inf
                          :else (- log-recall)))
        ;; The words kept so far, most overdue first, each as #js [word overdue tie-break].
        kept          #js []
        ranks-before? (fn [overdue tie-break ^js other]
                        (or (> overdue (aget other 1))
                            (and (== overdue (aget other 1)) (< tie-break (aget other 2)))))]
    (doseq [[word log-recall] recalls
            :let  [word-overdue   (overdue log-recall)
                   word-tie-break (tie-breaker word)]
            :when (and (pos? size)
                       (or (< (.-length kept) size)
                           (ranks-before? word-overdue word-tie-break (aget kept (dec (.-length kept))))))]
      (let [insert-position (loop [position 0]
                              (if (and (< position (.-length kept))
                                       (not (ranks-before? word-overdue word-tie-break (aget kept position))))
                                (recur (inc position))
                                position))]
        (.splice kept insert-position 0 #js [word word-overdue word-tie-break])
        (when (> (.-length kept) size)
          (.pop kept))))
    (mapv #(aget % 0) kept)))


(def trial-type-word "word")


(def trial-type-phrase "phrase")


(def trial-type-example "example")


(defn example-trial?
  [trial]
  (= (:type trial) trial-type-example))


(defn word-trial?
  [trial]
  (= (:type trial) trial-type-word))


(defn phrase-trial?
  [trial]
  (= (:type trial) trial-type-phrase))


(defn vocab-trial?
  "The trial an entry itself is asked by — a word trial for a word, a phrase
   trial for a phrase. It is the one whose `:word-id` an example hangs off,
   so it is the one that unlocks examples."
  [trial]
  (not (example-trial? trial)))


(defn generate-trials
  "The trials of a lesson over `vocab` and `lesson-examples` (those a read in the lesson's collection sees): one trial
   per word or phrase, and one example trial per word that has examples. A word with several examples takes
   the one with the smallest id, the same every time."
  [vocab lesson-examples]
  (let [example-of (update-vals (group-by :word-id lesson-examples) examples/lesson-example)]
    (into
     ;; A word or a phrase becomes the trial its kind calls for. `:word-id` keeps its name: reviews and examples are stored under it.
     (mapv (fn [entry]
             {:type    (if (= "phrase" (:kind entry))
                         trial-type-phrase
                         trial-type-word)
              :word-id (:id entry)
              :prompt  (->> (:translation entry)
                            (filter #(= "ru" (:lang %)))
                            (map :value)
                            (str/join ", "))
              :answer  (:value entry)
              :locked? false})
           vocab)
     (comp (map :word-id)
           (distinct)
           (map example-of)
           (map (fn [example]
                  {:type      trial-type-example
                   :word-id   (:word-id example)
                   :prompt    (:translation example)
                   :answer    (:value example)
                   :structure (:structure example)
                   :locked?   true})))
     lesson-examples)))


(defn- locked-trial?
  [trial]
  (true? (:locked? trial)))


(defn- unlock-example-trials
  [trials word-id]
  (mapv #(cond-> %
           (and (example-trial? %) (-> % :word-id (= word-id)))
           (assoc :locked? false))
        trials))


(defn random-trial
  "One of `trials`, picked with `random`, a function of nothing that returns
   a number in [0, 1)."
  [random trials]
  (nth trials (js/Math.floor (* (random) (count trials)))))


(defn initial-state
  "A lesson over `vocab` and `lesson-examples`. `select-trial`, a function of the
   trials to pick from, chooses the first and every next trial."
  [vocab lesson-examples select-trial]
  (let [trials (generate-trials vocab lesson-examples)]
    {:options          {:trial-selector select-trial}
     :trials           trials
     :remaining-trials trials
     :current-trial    (select-trial (remove locked-trial? trials))
     :last-result      nil}))


(defn expected-answer
  [state]
  (-> state :current-trial :answer))


(defn normalized-answer
  [answer]
  (utils/normalize-german (or answer "")))


(defn- trial-normalized
  "Phrase trials grade through the typography-forgiving normalization; word
   and example trials keep the id-grade one."
  [trial answer]
  (if (phrase-trial? trial)
    (utils/normalize-for-grading (or answer ""))
    (normalized-answer answer)))


(defn remove-trial
  [remaining-trials trial]
  (vec (remove #(= % trial) remaining-trials)))


(def max-answer-length
  1000)


(defn check-answer
  "The lesson after `answer` to its current trial. A blank answer is graded
   like any wrong one. A wrong example is remembered, beside the trials, since
   `remove-trial` compares them whole."
  [state answer-given]
  (let [given          (or answer-given "")
        answer         (subs given 0 (min max-answer-length (count given)))
        current-trial  (:current-trial state)
        correct-answer (expected-answer state)
        correct?       (= (trial-normalized current-trial answer)
                          (trial-normalized current-trial correct-answer))
        unlock?        (and correct? (vocab-trial? current-trial))
        remaining      (cond-> (:remaining-trials state)
                         correct? (remove-trial current-trial)
                         unlock?  (unlock-example-trials (:word-id current-trial)))]
    (assoc state
           :last-result {:answer answer :correct? correct?}
           :remaining-trials remaining
           :trials      (cond-> (:trials state)
                          unlock? (unlock-example-trials (:word-id current-trial))))))


(defn advance
  [state]
  (let [select-trial (-> state :options :trial-selector)
        unlocked     (filterv (complement locked-trial?) (:remaining-trials state))
        available    (if (> (count unlocked) 1)
                       (remove-trial unlocked (:current-trial state))
                       unlocked)]
    (when (seq available)
      (assoc state
             :current-trial (select-trial available)
             :last-result   nil))))


(defn progress
  [state]
  (let [total     (count (:trials state))
        remaining (count (:remaining-trials state))
        completed (- total remaining)]
    (if (pos? total)
      (* 100 (/ completed total))
      0)))


(defn finished?
  [state]
  (empty? (:remaining-trials state)))


(defn current-trial
  [state]
  (:current-trial state))


(defn last-result
  [state]
  (:last-result state))


(defn answer-segments
  [trial]
  (let [structure-by-index (into {}
                                 (map (juxt :wordIndex identity))
                                 (:structure trial))]
    (->> (str/split (str/trim (or (:answer trial) "")) #"\s+")
         (map-indexed
          (fn [word-index word]
            (if-let [item (get structure-by-index word-index)]
              {:type        :annotated-word
               :text        word
               :used-form   (:usedForm item)
               :dictionary-form (:dictionaryForm item)
               :translation (:translation item)
               :word-index  word-index}
              {:type       :plain-word
               :text       word
               :word-index word-index})))
         vec)))

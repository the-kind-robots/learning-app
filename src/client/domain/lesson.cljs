(ns domain.lesson
  (:require
   [clojure.string :as str]
   [utils :as utils]))


(def default-vocab-per-lesson 3)


(def default-trial-selector rand-nth)


(defn pick-vocab
  "The `n` most due of `rows`, most due first, ranked by `urgency-of`. Rows
   that tie are taken in random order, so a tied vocabulary is never cut
   alphabetically (#431).

   One pass keeps the top `n` in a small array, each with a random tie key;
   the vocabulary is not sorted. `##Inf` compares, it is never subtracted. An
   urgency that is not a number ranks last."
  [rows urgency-of n]
  (let [n       (or n 0)
        top     #js []
        before? (fn [u t ^js kept]
                  (or (> u (aget kept 1))
                      (and (== u (aget kept 1)) (< t (aget kept 2)))))]
    (doseq [row   rows
            :let  [u (let [u (urgency-of row)] (if (js/Number.isNaN u) ##-Inf u))
                   t (js/Math.random)]
            :when (and (pos? n)
                       (or (< (.-length top) n)
                           (before? u t (aget top (dec (.-length top))))))]
      (let [at (loop [i 0]
                 (if (and (< i (.-length top)) (not (before? u t (aget top i))))
                   (recur (inc i))
                   i))]
        (.splice top at 0 #js [row u t])
        (when (> (.-length top) n)
          (.pop top))))
    (mapv #(aget % 0) top)))


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


(defn- vocab->trial
  "A word or a phrase becomes the trial its kind calls for. `:word-id` keeps
   its name because reviews and examples are stored under it."
  [vocab]
  {:type    (if (= "phrase" (:kind vocab))
              trial-type-phrase
              trial-type-word)
   :word-id (:id vocab)
   :prompt  (->> (:translation vocab)
                 (filter #(= "ru" (:lang %)))
                 (map :value)
                 (str/join ", "))
   :answer  (:value vocab)
   :locked? false})


(defn- example->trial
  [example]
  {:type      trial-type-example
   :word-id   (:word-id example)
   :prompt    (:translation example)
   :answer    (:value example)
   :structure (:structure example)
   :locked?   true})


(defn generate-trials
  "The trials of a lesson over `vocab`, its words and phrases, and
   `examples`, the examples a read in the lesson's collection sees: one
   trial per word or phrase, and one example trial per word or phrase that
   has examples. A word may have several examples; until the lesson can
   show more than one, it takes the example with the smallest id, so the
   same one every time."
  [vocab examples]
  (let [example-of (update-vals (group-by :word-id examples) #(first (sort-by :id %)))]
    (into (mapv vocab->trial vocab)
          (comp (map :word-id) (distinct) (map example-of) (map example->trial))
          examples)))


(defn trial-id
  [trial]
  (str (:type trial) ":" (:word-id trial)))


(defn- locked-trial?
  [trial]
  (true? (:locked? trial)))


(defn- unlock-example-trial
  [word-id trial]
  (cond-> trial
    (and (example-trial? trial) (-> trial :word-id (= word-id)))
    (assoc :locked? false)))


(defn- unlock-example-trials
  [trials word-id]
  (mapv #(unlock-example-trial word-id %) trials))


(defn- select-trial
  [trials trial-selector]
  (let [trial-selector (case trial-selector
                         :first  first
                         :random rand-nth
                         default-trial-selector)]
    (trial-selector trials)))


(defn initial-state
  [vocab examples trial-selector]
  (let [trials (generate-trials vocab examples)]
    {:options          {:trial-selector trial-selector}
     :trials           trials
     :remaining-trials trials
     :current-trial    (select-trial (remove locked-trial? trials) trial-selector)
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
    (cond-> (assoc state
                   :last-result      {:answer answer :correct? correct?}
                   :remaining-trials remaining
                   :trials           (cond-> (:trials state)
                                       unlock? (unlock-example-trials (:word-id current-trial))))
      (and (not correct?) (example-trial? current-trial))
      (update :failed-trials (fnil conj #{}) (trial-id current-trial)))))


(defn- available-trials
  [remaining-trials current-trial]
  (let [unlocked-trials (filterv (complement locked-trial?) remaining-trials)]
    (if (> (count unlocked-trials) 1)
      (remove-trial unlocked-trials current-trial)
      unlocked-trials)))


(defn advance
  [state]
  (let [remaining      (:remaining-trials state)
        trial-selector (-> state :options :trial-selector)
        available      (available-trials remaining (:current-trial state))]
    (when (seq available)
      (assoc state
             :current-trial (select-trial available trial-selector)
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


(defn- split-answer-words
  [answer]
  (str/split (str/trim (or answer "")) #"\s+"))


(defn answer-segments
  [trial]
  (let [structure-by-index (into {}
                                 (map (juxt :wordIndex identity))
                                 (:structure trial))]
    (->> (split-answer-words (:answer trial))
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


(defn failed-before?
  "Whether the current trial is an example that was answered wrongly before."
  [state]
  (contains? (:failed-trials state) (trial-id (current-trial state))))

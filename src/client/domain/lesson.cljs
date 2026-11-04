(ns domain.lesson
  (:require
   [clojure.string :as str]
   [domain.examples :as examples]
   [domain.retention :as retention]
   [utils :as utils]))


(def default-vocab-per-lesson 3)


(defn lesson-words
  "The `size` words a lesson takes from `cards`, `{:word :reviews}` of the words in scope, for a learner of `pace`
   (`domain.retention/pace`) at `now-ms`. A word with no answer is new; when there is one, one place goes to a new
   word drawn at random. Each other place goes, with probability 0.1, to a known word drawn at random, and
   otherwise to the known word a review now strengthens most (`domain.retention/stability-gain`); a place no known
   word is left for goes to a new word. Equal gains are taken at random, never in the order of `cards`. `random`
   is a function of nothing that returns a number in [0, 1)."
  [{:keys [cards now-ms pace random size]}]
  (let [;; The cards in random order (Fisher–Yates), so neither new words nor ties follow the order of `cards`.
        shuffled    (let [cards (to-array cards)]
                      (doseq [position (range (dec (alength cards)) 0 -1)
                              :let     [other (js/Math.floor (* (random) (inc position)))
                                        card  (aget cards position)]]
                        (aset cards position (aget cards other))
                        (aset cards other card))
                      cards)
        ;; Each word with what a review of it gains now; nil for a new word.
        gains       (for [{:keys [reviews word]} shuffled]
                      [word
                       (when-let [memory (retention/memory
                                          {:history reviews :pace pace :weights retention/default-weights :word word})]
                         (retention/stability-gain
                          {:memory memory :now-ms now-ms :weights retention/default-weights}))])
        new-words   (->> gains (remove second) (mapv first))
        ;; `sort-by` is stable: equal gains keep their shuffled order.
        known-words (->> gains (filter second) (sort-by (comp - second)) (mapv first))]
    ;; The first place goes to a new word, if there is one: the first of them, as they are shuffled.
    (loop [chosen      (vec (take (min size 1) new-words))
           known-words known-words
           new-words   (rest new-words)]
      (cond
        ;; The lesson is full, or the vocabulary is used up.
        (or (>= (count chosen) size) (and (empty? known-words) (empty? new-words)))
        chosen

        ;; No known word is left: new words fill the lesson.
        (empty? known-words)
        (recur (conj chosen (first new-words)) known-words (rest new-words))

        ;; A known word: one drawn at random one time in ten, otherwise the one a review strengthens most.
        :else
        (let [position (if (< (random) 0.1)
                         (js/Math.floor (* (random) (count known-words)))
                         0)]
          (recur (conj chosen (known-words position))
                 (into (subvec known-words 0 position) (subvec known-words (inc position)))
                 new-words))))))


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
     :last-result      nil
     :misses           {}
     :past-trials      []}))


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
                   :last-result {:answer answer :correct? correct?}
                   :remaining-trials remaining
                   :trials      (cond-> (:trials state)
                                  unlock? (unlock-example-trials (:word-id current-trial))))
      (not correct?) (update-in [:misses current-trial] (fnil inc 0)))))


(defn advance
  "The lesson on its next trial, the one answered right kept among the past trials; nil when none is left."
  [state]
  (let [select-trial (-> state :options :trial-selector)
        unlocked     (filterv (complement locked-trial?) (:remaining-trials state))
        current      (:current-trial state)
        available    (if (> (count unlocked) 1)
                       (remove-trial unlocked current)
                       unlocked)]
    (when (seq available)
      (cond-> (assoc state
                     :current-trial (select-trial available)
                     :last-result   nil)
        (-> state :last-result :correct?)
        (update :past-trials
                conj
                {:attempts (inc (get (:misses state) current 0))
                 :trial    current})))))


(defn past-trials
  "The trials answered right and left behind, in order: `{:trial :attempts}`, the right answer counted."
  [state]
  (:past-trials state))


(defn progress
  [state]
  (let [total     (count (:trials state))
        remaining (count (:remaining-trials state))
        completed (- total remaining)]
    (if (pos? total)
      (* 100 (/ completed total))
      0)))


(defn past-progress
  "The progress as the past trial at `index` was asked: each trial before it was answered right."
  [state index]
  (* 100 (/ index (count (:trials state)))))


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

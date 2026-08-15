(ns domain.lesson
  (:require
   [clojure.string :as str]
   [utils :as utils]))


(def lesson-id "lesson")


(def default-learnables-per-lesson 3)


(def default-trial-selector rand-nth)


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


(defn- learnable->trial
  "A word or a phrase becomes the trial its type calls for. `:word-id` keeps
   its name because reviews and examples are stored under it."
  [learnable]
  {:type    (if (= "phrase" (:kind learnable))
              trial-type-phrase
              trial-type-word)
   :word-id (:id learnable)
   :prompt  (->> (:translation learnable)
                 (filter #(= "ru" (:lang %)))
                 (map :value)
                 (str/join ", "))
   :answer  (:value learnable)
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
  [learnables examples]
  (into (mapv learnable->trial learnables)
        (map example->trial examples)))


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
  [learnables examples trial-selector]
  (let [trials (generate-trials learnables examples)]
    {:_id           lesson-id
     :type          "lesson"
     :options       {:trial-selector trial-selector}
     :trials        trials
     :remaining-trials trials
     :current-trial (select-trial (remove locked-trial? trials) trial-selector)
     :last-result   nil}))


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


(defn phrase-review-due?
  "True when this graded attempt should write a review: phrase trials write
   one per trial per lesson (the first attempt decides), tracked in state
   rather than on the trial — `remove-trial` compares trials by value."
  [state trial]
  (and (phrase-trial? trial)
       (not (some #{(trial-id trial)} (:reviewed-trial-ids state)))))


(defn mark-trial-reviewed
  [state trial]
  (update state :reviewed-trial-ids (fnil conj []) (trial-id trial)))


(defn check-answer
  [state answer]
  (let [current-trial  (:current-trial state)
        correct-answer (expected-answer state)
        correct?       (= (trial-normalized current-trial answer)
                          (trial-normalized current-trial correct-answer))
        remaining      (cond-> (:remaining-trials state)
                         correct? (remove-trial current-trial))
        remaining      (if (and correct? (word-trial? current-trial))
                         (unlock-example-trials remaining (:word-id current-trial))
                         remaining)
        trials         (if (and correct? (word-trial? current-trial))
                         (unlock-example-trials (:trials state) (:word-id current-trial))
                         (:trials state))
        last-result    {:correct? correct?
                        :answer   answer}]
    (-> state
        (assoc :trials trials)
        (assoc :remaining-trials remaining)
        (assoc :last-result last-result))))


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

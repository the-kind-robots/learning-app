(ns domain.lesson
  "Pure lesson logic - no side effects, no platform-specific code."
  (:require
   [clojure.string :as str]
   [utils :as utils]))


(def lesson-id "lesson")


(def default-words-per-lesson 3)


(def default-trial-selector rand-nth)


(def trial-type-word "word")


(def trial-type-example "example")


(defn example-trial?
  "Returns true if trial is an example trial."
  [trial]
  (= (:type trial) trial-type-example))


(defn word-trial?
  "Returns true if trial is a word trial."
  [trial]
  (= (:type trial) trial-type-word))


(defn- word->trial
  "Convert a word summary to a word trial.
   Word summary has :id, :value, :translation, :retention-level."
  [word]
  {:type    trial-type-word
   :word-id (:id word)
   :prompt  (->> (:translation word)
                 (filter #(= "ru" (:lang %)))
                 (map :value)
                 (str/join ", "))
   :answer  (:value word)
   :locked? false})


(defn- example->trial
  "Convert an example document to an example trial.
   Example doc has :word-id, :value, :translation."
  [example]
  {:type           trial-type-example
   :word-id        (:word-id example)
   :prompt         (:translation example)
   :answer         (:value example)
   :gloss-mismatch (:gloss-mismatch example)
   :locked?        true})


(defn generate-trials
  "Generate denormalized trial objects from words and examples.
   Each word produces a word trial. Each example produces an example trial.
   Trials have :type, :word-id, :prompt, :answer."
  [words examples]
  (into (mapv word->trial words)
        (map example->trial examples)))


(defn trial-id
  "Return a stable identifier for a trial (composite of type + word-id)."
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
  "Create a new lesson state from words and examples.
    - trial-selector - :first, :random, unknown defaults to random
    - started-at - timestamp
    Returns lesson document without :words (trials are denormalized)."
  [words examples trial-selector started-at]
  (let [trials (generate-trials words examples)]
    {:_id           lesson-id
     :type          "lesson"
     :started-at    started-at
     :options       {:trial-selector trial-selector}
     :trials        trials
     :remaining-trials trials
     :current-trial (select-trial (remove locked-trial? trials) trial-selector)
     :last-result   nil}))


(defn expected-answer
  "Get the expected answer for the current trial."
  [state]
  (-> state :current-trial :answer))


(defn normalized-answer
  "Normalize answer for comparison: lowercase, normalize German chars, collapse whitespace."
  [answer]
  (utils/normalize-german (or answer "")))


(defn remove-trial
  "Remove a specific trial from the remaining-trials vector."
  [remaining-trials trial]
  (vec (remove #(= % trial) remaining-trials)))


(defn check-answer
  "Check the user's answer for the current trial.
    Returns {:result {:correct? :correct-answer :is-finished?}
             :lesson-state <updated-state>}.
    Pure logic only; persistence handled by caller."
  [state answer]
  (let [current-trial  (:current-trial state)
        correct-answer (expected-answer state)
        correct?       (= (normalized-answer answer)
                          (normalized-answer correct-answer))
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
  "Returns trials available for next selection.
   Excludes current trial to avoid immediate repetition (unless only one remains)."
  [remaining-trials current-trial]
  (let [unlocked-trials (filterv (complement locked-trial?) remaining-trials)]
    (if (> (count unlocked-trials) 1)
      (remove-trial unlocked-trials current-trial)
      unlocked-trials)))


(defn advance
  "Select the next trial from remaining trials.
    Excludes current trial from selection to avoid immediate repetition.
    Returns updated state or nil if no trials remain."
  [state]
  (let [remaining      (:remaining-trials state)
        trial-selector (-> state :options :trial-selector)
        available      (available-trials remaining (:current-trial state))]
    (when (seq available)
      (assoc state
             :current-trial (select-trial available trial-selector)
             :last-result   nil))))


(defn progress
  "Calculate lesson progress as percentage (0-100)."
  [state]
  (let [total     (count (:trials state))
        remaining (count (:remaining-trials state))
        completed (- total remaining)]
    (if (pos? total)
      (* 100 (/ completed total))
      0)))


(defn finished?
  "Returns true if the lesson has no remaining trials."
  [state]
  (empty? (:remaining-trials state)))


(defn current-trial
  "Get the current trial from lesson state."
  [state]
  (:current-trial state))


(defn last-result
  "Get the last answer result from lesson state."
  [state]
  (:last-result state))

(ns use-cases.lesson
  (:require
   [domain.collections :as collections]
   [domain.examples :as examples]
   [domain.lesson :as domain]
   [domain.retention :as retention]
   [domain.vocabulary :as vocabulary-domain]
   [ports.learner :as learner]
   [use-cases.vocabulary :as vocabulary]))


(defn start
  "A new lesson out of the learner's words: `{:lesson-state ..}` or `{:error :no-words-available}`, over the most due
   words, ties in random order. opts: `:random`, `:vocab-per-lesson` (default 3), `:trial-selector`
   (`:first`, otherwise random)."
  [learner collection
   {:keys [random vocab-per-lesson trial-selector]
    :or   {vocab-per-lesson domain/default-vocab-per-lesson}}
   now-ms]
  ;; Every word in scope, unranked: `pick-vocab` owns the whole selection policy, ties included.
  (let [cards    (learner/collection-cards learner collection)
        selected (map :word
                      (domain/pick-vocab cards
                                         #(retention/urgency (:reviews %) now-ms)
                                         vocab-per-lesson
                                         random))]
    (if-not (seq selected)
      {:error :no-words-available}
      (let [vocab    (mapv #(select-keys % [:id :kind :translation :value]) selected)
            word-ids (set (map :id vocab))]
        ;; Which examples a read in this collection sees is one rule, in `domain.examples`.
        {:lesson-state (domain/initial-state
                        vocab
                        (->> (learner/examples-of learner word-ids)
                             (examples/visible-in (collections/family (learner/collections learner)
                                                                      (:id collection))))
                        (if (= :first trial-selector)
                          first
                          (partial domain/random-trial random)))}))))


(defn next-trial
  "The lesson on its next trial, or nil when none is left."
  [lesson-state]
  (domain/advance lesson-state))


(defn ^:async record-review!
  "Writes the review of the answer just checked in `lesson-state`."
  [learner world lesson-state]
  (let [trial (domain/current-trial lesson-state)]
    (await (vocabulary/add-review
            learner
            world
            (:word-id trial)
            (-> lesson-state domain/last-result :correct?)
            (:prompt trial)))))


(defn token-info
  "Return token info for lesson answer annotation card."
  [learner dictionary-form translation]
  (let [existing (vocabulary/find-duplicate learner dictionary-form)]
    {:dictionary-form dictionary-form
     :translation translation
     :state       (vocabulary-domain/translation-state existing translation)}))


(defn check-answer!
  "Grades `answer` at once: `{:lesson-state .. :answer-hints ..}` for an example, `{:lesson-state .. :review ..}`
   for a word, its review being written (`:review` resolves once it is). Nil once the current trial is answered.
   The hints are the vocabulary state per annotated word of the answer, `{word-index :word-state/..}`."
  [learner world lesson-state answer]
  (when (nil? (domain/last-result lesson-state))
    (let [checked (domain/check-answer lesson-state answer)
          trial   (domain/current-trial checked)]
      (if (domain/example-trial? trial)
        {:answer-hints (->> (domain/answer-segments trial)
                            (filter #(= :annotated-word (:type %)))
                            (map (fn [{:keys [word-index dictionary-form translation]}]
                                   [word-index (:state (token-info learner dictionary-form translation))]))
                            (into {}))
         :lesson-state checked}
        {:lesson-state checked
         :review       (record-review! learner world checked)}))))

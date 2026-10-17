(ns use-cases.lesson
  (:require
   [domain.examples :as examples]
   [domain.lesson :as domain]
   [domain.retention :as retention]
   [domain.vocabulary :as vocabulary-domain]
   [use-cases.vocabulary :as vocabulary]))


(defn start
  "A new lesson out of the learner's data in memory. Returns {:lesson-state
   ...} or {:error :no-words-available}. Pure but for ties: the words are
   the most due ones (`domain/pick-vocab`), equally due ones in random order.

   opts:
     :random            — a function of nothing that returns a number in [0, 1)
     :vocab-per-lesson  — how many words and phrases to include (default 3)
     :trial-selector    — strategy for picking the next trial (:first, otherwise random)"
  [learner memory collection
   {:keys [random vocab-per-lesson trial-selector]
    :or   {vocab-per-lesson domain/default-vocab-per-lesson}}
   now-ms]
  ;; Every word in scope, unranked: `pick-vocab` owns the whole selection
  ;; policy, ties included.
  (let [cards    ((:learner/collection-cards learner) memory collection)
        selected (map :word
                      (domain/pick-vocab cards
                                         #(retention/urgency (:reviews %) now-ms)
                                         vocab-per-lesson
                                         random))]
    (if-not (seq selected)
      {:error :no-words-available}
      (let [vocab    (mapv #(select-keys % [:id :kind :translation :value]) selected)
            word-ids (set (map :id vocab))]
        ;; Which examples a read in this collection sees is one rule, and it
        ;; lives in `domain.examples`.
        {:lesson-state (domain/initial-state
                        vocab
                        (->> ((:learner/examples-of learner) memory word-ids)
                             (examples/visible-in (:id collection)))
                        (if (= :first trial-selector)
                          first
                          (partial domain/random-trial random)))}))))


(defn ^:async record-review!
  "Writes the review of the answer just checked in `lesson-state`."
  [capabilities lesson-state]
  (let [trial (domain/current-trial lesson-state)]
    (await (vocabulary/add-review
            capabilities
            (:word-id trial)
            (-> lesson-state domain/last-result :correct?)
            (:prompt trial)))))


(defn token-info
  "Return token info for lesson answer annotation card."
  [capabilities dictionary-form translation]
  (let [existing (vocabulary/find-duplicate capabilities dictionary-form)]
    {:dictionary-form dictionary-form
     :translation translation
     :state       (vocabulary-domain/translation-state existing translation)}))


(defn answer-annotations
  "Vocabulary state per annotated word of the trial's answer:
   {word-index :word-state/unknown-word | :word-state/known-missing-translation | :word-state/known-with-translation}."
  [capabilities trial]
  (let [segments (filterv #(= :annotated-word (:type %))
                          (domain/answer-segments trial))
        infos    (map #(token-info capabilities (:dictionary-form %) (:translation %))
                      segments)]
    (zipmap (map :word-index segments)
            (map :state infos))))

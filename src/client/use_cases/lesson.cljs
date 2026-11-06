(ns use-cases.lesson
  (:require
   [domain.collections :as collections]
   [domain.examples :as examples]
   [domain.lesson :as lesson]
   [domain.retention :as retention]
   [domain.statistics :as statistics]
   [domain.vocabulary :as vocabulary]
   [ports.learner :as learner]
   [ports.page :as page]
   [ports.world :as world]))


(defn start
  "A new lesson out of the learner's words: `{:lesson-state ..}` or `{:error :no-words-available}`, over the words
   `domain.lesson/lesson-words` takes, with the examples in `language`. opts: `:random`, `:vocab-per-lesson`
   (default 3), `:trial-selector` (`:first`, otherwise random)."
  [learner language collection
   {:keys [random vocab-per-lesson trial-selector]
    :or   {vocab-per-lesson lesson/default-vocab-per-lesson}}
   now-ms]
  (let [words (lesson/lesson-words {:cards  (learner/collection-cards learner collection)
                                    :now-ms now-ms
                                    :pace   (retention/pace (learner/collection-cards learner nil))
                                    :random random
                                    :size   vocab-per-lesson})]
    (if-not (seq words)
      {:error :no-words-available}
      ;; Which examples a read in this collection sees is one rule, in `domain.examples`.
      (let [lesson-examples (->> (learner/examples-of learner (set (map :id words)))
                                 (examples/in-language language)
                                 (examples/visible-in (collections/family (learner/collections learner)
                                                                          (:id collection))))
            select-trial    (if (= :first trial-selector)
                              first
                              (partial lesson/random-trial random))]
        {:lesson-state (lesson/initial-state words lesson-examples select-trial)}))))


(defn next-trial
  "The lesson on its next trial, or nil when none is left."
  [lesson-state]
  (lesson/advance lesson-state))


(defn ^:async record-review!
  "Writes the review of the answer just checked in `lesson-state`, which took `answer-ms` (nil: unknown). Resolves
   once memory has it."
  [learner world lesson-state answer-ms]
  (let [trial  (lesson/current-trial lesson-state)
        review (assoc (vocabulary/new-review (:word-id trial)
                                             (-> lesson-state lesson/last-result :correct?)
                                             (:prompt trial)
                                             answer-ms)
                      :created-at
                      (world/clock world :now-iso))]
    (await (learner/insert! learner :entity/review review))))


(defn lesson-days
  "The learner's lessons by local day, from the first lesson's to that of `now-ms`
   (`domain.statistics/lesson-days`)."
  [learner now-ms]
  (statistics/lesson-days (learner/lesson-times learner) now-ms))


(defn token-info
  "Return token info for lesson answer annotation card."
  [learner dictionary-form translation]
  (let [word (learner/word learner (vocabulary/vocab-id dictionary-form))]
    {:dictionary-form dictionary-form
     :state       (vocabulary/translation-state word translation)
     :translation translation}))


(defn check-answer!
  "Grades `answer` at once: `{:lesson-state .. :answer-hints ..}` for an example, `{:lesson-state .. :review ..}`
   for a word, its review being written (`:review` resolves once it is). Nil once the current trial is answered.
   The answer that ends the lesson also writes the lesson passed, `:passed` resolving once it is written.
   The hints are the vocabulary state per annotated word of the answer, `{word-index :word-state/..}`.
   The review's time to answer counts from `answer-started`, `{:uptime-ms :visibility-changes}` as the trial
   appeared; a page hidden or shown since leaves the time unknown."
  [learner world page lesson-state answer answer-started]
  (when (nil? (lesson/last-result lesson-state))
    (let [checked (lesson/check-answer lesson-state answer)
          trial   (lesson/current-trial checked)]
      (cond-> (if (lesson/example-trial? trial)
                {:answer-hints (->> (lesson/answer-segments trial)
                                    (filter #(= :annotated-word (:type %)))
                                    (map (fn [{:keys [word-index dictionary-form translation]}]
                                           [word-index (:state (token-info learner dictionary-form translation))]))
                                    (into {}))
                 :lesson-state checked}
                (let [answer-ms (when (= (:visibility-changes answer-started) (page/visibility-changes page))
                                  (js/Math.round (- (world/clock world :uptime-ms) (:uptime-ms answer-started))))]
                  {:lesson-state checked
                   :review       (record-review! learner world checked answer-ms)}))
        (lesson/finished? checked)
        (assoc :passed (learner/insert! learner :entity/lesson {:passed-at (world/clock world :now-iso)}))))))

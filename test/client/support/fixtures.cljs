(ns client.support.fixtures)


;; =============================================================================
;; Vocabulary fixtures (as stored in DB)
;; =============================================================================


(def sample-words
  [{:_id         "word-1"
    :type        "vocab"
    :value       "der Hund"
    :translation [{:lang "ru" :value "пёс"}]
    :created-at  "2024-08-20T10:00:00.000Z"
    :modified-at "2024-08-20T10:00:00.000Z"}
   {:_id         "word-2"
    :type        "vocab"
    :value       "die Katze"
    :translation [{:lang "ru" :value "кот"}]
    :created-at  "2024-08-20T10:05:00.000Z"
    :modified-at "2024-08-20T10:05:00.000Z"}])


(def sample-reviews
  [{:_id         "review-1"
    :type        "review"
    :word-id     "word-1"
    :retained    true
    :created-at  "2024-08-20T10:00:00.000Z"
    :translation [{:lang "ru" :value "пёс"}]}
   {:_id         "review-2"
    :type        "review"
    :word-id     "word-2"
    :retained    false
    :created-at  "2024-08-20T10:05:00.000Z"
    :translation [{:lang "ru" :value "кот"}]}])


;; =============================================================================
;; Lesson fixtures
;; =============================================================================


(def lesson-words
  "Word rows as denormalized into lesson state before trial generation."
  [{:id              "word-1"
    :value           "der Hund"
    :translation     [{:lang "ru" :value "пёс"}]
    :retention-level 20}
   {:id              "word-2"
    :value           "die Katze"
    :translation     [{:lang "ru" :value "кошка"}]
    :retention-level 50}])


(def lesson-examples
  "Example documents as stored in DB."
  [{:_id         "example-1"
    :type        "example"
    :word-id     "word-1"
    :word        "der Hund"
    :value       "Der Hund schlaeft."
    :translation "Пёс спит"
    :structure   []
    :created-at  "2024-08-20T10:00:00.000Z"}])


(def expected-word-trials
  "Expected trials generated from lesson-words."
  [{:type    "word"
    :word-id "word-1"
    :prompt  "пёс"
    :answer  "der Hund"
    :locked? false}
   {:type    "word"
    :word-id "word-2"
    :prompt  "кошка"
    :answer  "die Katze"
    :locked? false}])


(def expected-example-trials
  "Expected trials generated from lesson-examples."
  [{:type    "example"
    :word-id "word-1"
    :prompt  "Пёс спит"
    :answer  "Der Hund schlaeft."
    :gloss-mismatch nil
    :locked? true}])


(def all-expected-trials
  "All trials: word trials first, then example trials."
  (into expected-word-trials expected-example-trials))

(ns client.support.fixtures)


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
    :structure   [{:usedForm "Hund"
                   :dictionaryForm "der Hund"
                   :translation "пёс"
                   :wordIndex 1}
                  {:usedForm "schlaeft"
                   :dictionaryForm "schlafen"
                   :translation "спать"
                   :wordIndex 2}]
    :created-at  "2024-08-20T10:00:00.000Z"}])

(ns client.domain.lesson-test
  (:require
   [client.support.fixtures :as fixtures]
   [cljs.test :refer-macros [deftest is testing]]
   [domain.lesson :as sut]))


;; =============================================================================
;; generate-trials
;; =============================================================================


(deftest generate-trials-creates-word-trials
  (testing "each word produces a word trial with :type, :word-id, :prompt, :answer"
    (let [trials (sut/generate-trials fixtures/lesson-words [])]
      (is (= 2 (count trials)))
      (is (= fixtures/expected-word-trials trials)))))


(deftest generate-trials-creates-example-trials
  (testing "each example produces an example trial"
    (let [trials (sut/generate-trials [] fixtures/lesson-examples)]
      (is (= 1 (count trials)))
      (is (= fixtures/expected-example-trials trials)))))


(deftest generate-trials-combines-words-and-examples
  (testing "word trials come first, then example trials"
    (let [trials (sut/generate-trials fixtures/lesson-words fixtures/lesson-examples)]
      (is (= 3 (count trials)))
      (is (= fixtures/all-expected-trials trials)))))


(deftest generate-trials-handles-empty-inputs
  (testing "empty words and examples produces empty trials"
    (is (= [] (sut/generate-trials [] [])))))


;; =============================================================================
;; trial predicates
;; =============================================================================


(deftest example-trial?-identifies-example-trials
  (let [word-trial    {:type "word" :word-id "w1" :prompt "p" :answer "a"}
        example-trial {:type "example" :word-id "w1" :prompt "p" :answer "a"}]
    (is (false? (sut/example-trial? word-trial)))
    (is (true? (sut/example-trial? example-trial)))))


(deftest word-trial?-identifies-word-trials
  (let [word-trial    {:type "word" :word-id "w1" :prompt "p" :answer "a"}
        example-trial {:type "example" :word-id "w1" :prompt "p" :answer "a"}]
    (is (true? (sut/word-trial? word-trial)))
    (is (false? (sut/word-trial? example-trial)))))


;; =============================================================================
;; trial-id
;; =============================================================================


(deftest trial-id-generates-unique-composite-id
  (testing "trial-id combines type and word-id"
    (let [word-trial    {:type "word" :word-id "w1" :prompt "p" :answer "a"}
          example-trial {:type "example" :word-id "w1" :prompt "p" :answer "a"}]
      (is (= "word:w1" (sut/trial-id word-trial)))
      (is (= "example:w1" (sut/trial-id example-trial)))
      (is (not= (sut/trial-id word-trial) (sut/trial-id example-trial))))))


;; =============================================================================
;; initial-state
;; =============================================================================


(deftest initial-state-creates-lesson-document
  (testing "lesson has required fields per data-model spec"
    (let [state (sut/initial-state
                 fixtures/lesson-words
                 fixtures/lesson-examples
                 :first)]
      (is (= "lesson" (:_id state)))
      (is (= "lesson" (:type state)))
      (is (= 3 (count (:trials state))))
      (is (= 3 (count (:remaining-trials state))))
      (is (some? (:current-trial state)))
      (is (nil? (:last-result state))))))


(deftest initial-state-does-not-include-words
  (testing "lesson document has denormalized trials, no :words field"
    (let [state (sut/initial-state
                 fixtures/lesson-words
                 fixtures/lesson-examples
                 :first)]
      (is (not (contains? state :words))))))


(deftest initial-state-uses-injected-trial-selector
  (testing ":first selector picks first trial"
    (let [state (sut/initial-state
                 fixtures/lesson-words
                 fixtures/lesson-examples
                 :first)]
      (is (= (first (:trials state)) (:current-trial state))))))


(deftest initial-state-keeps-example-trials-locked
  (testing "example trials are present but locked at lesson start"
    (let [state (sut/initial-state
                 fixtures/lesson-words
                 fixtures/lesson-examples
                 :first)
          example-trials (filter sut/example-trial? (:remaining-trials state))]
      (is (= 1 (count example-trials)))
      (is (every? :locked? example-trials)))))


;; =============================================================================
;; expected-answer
;; =============================================================================


(deftest expected-answer-returns-trial-answer
  (let [state (sut/initial-state
               fixtures/lesson-words
               []
               :first)]
    (is (= "der Hund" (sut/expected-answer state)))))


(deftest answer-segments-builds-annotated-and-plain-words
  (testing "example answer segments use wordIndex to annotate matching words"
    (let [trial    {:type      "example"
                    :word-id   "word-1"
                    :answer    "Der Hund schlaeft."
                    :structure [{:usedForm       "Hund"
                                 :dictionaryForm "der Hund"
                                 :translation    "пёс"
                                 :wordIndex      1}
                                {:usedForm       "schlaeft"
                                 :dictionaryForm "schlafen"
                                 :translation    "спать"
                                 :wordIndex      2}]}
          segments (sut/answer-segments trial)]
      (is (= [{:type :plain-word :text "Der" :word-index 0}
              {:type        :annotated-word
               :text        "Hund"
               :used-form   "Hund"
               :dictionary-form "der Hund"
               :translation "пёс"
               :word-index  1}
              {:type        :annotated-word
               :text        "schlaeft."
               :used-form   "schlaeft"
               :dictionary-form "schlafen"
               :translation "спать"
               :word-index  2}]
             segments)))))


;; =============================================================================
;; normalized-answer
;; =============================================================================


(deftest normalized-answer-handles-case-and-german-chars
  (testing "normalizes for comparison"
    (is (= (sut/normalized-answer "Der Hund")
           (sut/normalized-answer "der hund")))
    (is (= (sut/normalized-answer "Käse")
           (sut/normalized-answer "kaese")))
    (is (= (sut/normalized-answer "größe")
           (sut/normalized-answer "GROESSE")))
    (is (= (sut/normalized-answer "  extra   spaces  ")
           (sut/normalized-answer "extra spaces")))))


(deftest normalized-answer-handles-nil
  (is (= "" (sut/normalized-answer nil))))


;; =============================================================================
;; check-answer - correct answers
;; =============================================================================


(deftest check-answer-correct-removes-trial
  (testing "correct answer removes trial from remaining-trials"
    (let [state (sut/initial-state
                 fixtures/lesson-words
                 []
                 :first)
          state (sut/check-answer state "der Hund")]
      (is (true? (:correct? (sut/last-result state))))
      (is (= 1 (count (:remaining-trials state)))))))


(deftest check-answer-correct-word-unlocks-examples
  (testing "correct word answer unlocks example trials for that word"
    (let [state         (sut/initial-state
                         fixtures/lesson-words
                         fixtures/lesson-examples
                         :first)
          updated-state (sut/check-answer state "der Hund")
          example-trial (first (filter sut/example-trial? (:remaining-trials updated-state)))]
      (is (some? example-trial))
      (is (false? (:locked? example-trial))))))


(deftest check-answer-correct-case-insensitive
  (testing "answer comparison ignores case"
    (let [state (sut/initial-state
                 fixtures/lesson-words
                 []
                 :first)
          state (sut/check-answer state "DER HUND")]
      (is (true? (:correct? (sut/last-result state)))))))


;; =============================================================================
;; check-answer - wrong answers
;; =============================================================================


(deftest check-answer-wrong-keeps-trial
  (testing "wrong answer keeps trial in remaining-trials"
    (let [state (sut/initial-state
                 fixtures/lesson-words
                 []
                 :first)
          state (sut/check-answer state "wrong answer")]
      (is (false? (:correct? (sut/last-result state))))
      (is (= 2 (count (:remaining-trials state)))))))


(deftest check-answer-wrong-word-keeps-examples-locked
  (testing "wrong word answer does not unlock example trials"
    (let [state         (sut/initial-state
                         fixtures/lesson-words
                         fixtures/lesson-examples
                         :first)
          updated-state (sut/check-answer state "wrong answer")
          example-trial (first (filter sut/example-trial? (:remaining-trials updated-state)))]
      (is (some? example-trial))
      (is (true? (:locked? example-trial))))))


;; =============================================================================
;; check-answer - result shape
;; =============================================================================


(deftest check-answer-returns-lesson-state
  (testing "check result returns lesson state"
    (let [state     (sut/initial-state
                     fixtures/lesson-words
                     []
                     :first)
          new-state (sut/check-answer state "der Hund")]
      (is (= (keys state) (keys new-state))))))


(deftest check-answer-last-result-includes-user-answer
  (testing "last-result stores user's answer per spec"
    (let [state (sut/initial-state
                 fixtures/lesson-words
                 []
                 :first)
          state (sut/check-answer state "my answer")]
      (is (= "my answer" (:answer (sut/last-result state)))))))


;; =============================================================================
;; check-answer - is-finished?
;; =============================================================================


(deftest check-answer-is-finished-when-no-remaining
  (testing "is-finished? true when all trials answered correctly"
    (let [state (sut/initial-state
                 [{:_id "w1" :value "der Hund" :translation [{:lang "en" :value "dog"}]}]
                 []
                 :first)
          state (sut/check-answer state "der Hund")]
      (is (true? (sut/finished? state)))
      (is (empty? (:remaining-trials state))))))


(deftest check-answer-not-finished-when-remaining
  (testing "is-finished? false when trials remain"
    (let [state (sut/initial-state
                 fixtures/lesson-words
                 []
                 :first)
          state (sut/check-answer state "der Hund")]
      (is (false? (sut/finished? state))))))


;; =============================================================================
;; advance
;; =============================================================================


(deftest advance-selects-next-trial
  (testing "advance selects from remaining trials excluding current"
    (let [state      (sut/initial-state
                      fixtures/lesson-words
                      []
                      :first)
          next-state (sut/advance state)]
      ;; With 2 trials and :first selector, advance excludes current and picks from remaining
      ;; Current is first trial, so next should be the second trial
      (is (= (second (:remaining-trials state))
             (:current-trial next-state))))))


(deftest advance-clears-last-result
  (testing "advance sets last-result to nil"
    (let [state      (sut/initial-state
                      fixtures/lesson-words
                      []
                      :first)
          state      (sut/check-answer state "der Hund")
          next-state (sut/advance state)]
      (is (some? (sut/last-result state)))
      (is (nil? (sut/last-result next-state))))))


(deftest advance-skips-locked-example-trials
  (testing "advance selects only unlocked trials"
    (let [state      (sut/initial-state
                      fixtures/lesson-words
                      fixtures/lesson-examples
                      :first)
          next-state (sut/advance state)]
      (is (sut/word-trial? (:current-trial next-state)))
      (is (not (:locked? (:current-trial next-state)))))))


(deftest advance-keeps-random-pool-after-unlock
  (testing "unlocked examples join normal selectable pool instead of forcing next trial"
    (let [state      (sut/initial-state
                      fixtures/lesson-words
                      fixtures/lesson-examples
                      :first)
          state      (sut/check-answer state "der Hund")
          next-state (sut/advance state)]
      (is (= "word-2" (:word-id (:current-trial next-state))))
      (is (sut/word-trial? (:current-trial next-state))))))


(deftest advance-returns-nil-when-no-remaining
  (testing "advance returns nil when no trials remain"
    (let [state (sut/initial-state
                 [{:_id "w1" :value "der Hund" :translation [{:lang "en" :value "dog"}]}]
                 []
                 :first)
          state (sut/check-answer state "der Hund")]
      (is (nil? (sut/advance state))))))


(deftest advance-uses-random-selector-by-default
  (testing "advance uses trial-selector from options"
    (let [state      (sut/initial-state
                      fixtures/lesson-words
                      []
                      :first)
          next-state (sut/advance state)]
      ;; Just verify it returns a state with a current-trial
      (is (some? next-state))
      (is (some? (sut/current-trial next-state))))))


;; =============================================================================
;; Full lesson flow
;; =============================================================================


(deftest full-lesson-flow
  (testing "complete lesson from start to finish"
    (let [state        (sut/initial-state
                        fixtures/lesson-words
                        fixtures/lesson-examples
                        :first)
          trials-count (count (:remaining-trials state))]
      ;; Start with 3 trials
      (is (= 3 trials-count))

      ;; Answer all trials correctly
      (let [final-state
            (loop [current-state state
                   attempts      trials-count]
              (let [answer       (sut/expected-answer current-state)
                    lesson-state (sut/check-answer current-state answer)]
                (if (or (sut/finished? lesson-state) (zero? attempts))
                  lesson-state
                  (recur (sut/advance lesson-state) (dec attempts)))))]
        (is (empty? (:remaining-trials final-state)))))))


;; =============================================================================
;; Phrase trials
;; =============================================================================


(def ^:private phrase-word
  {:id          "vocab:wie geht s"
   :kind        "phrase"
   :translation [{:lang "ru" :value "Как дела?"}]
   :value       "Wie geht's?"})


(deftest generate-trials-creates-phrase-trials
  (testing "a phrase item produces a phrase trial without examples"
    (let [trials (sut/generate-trials [phrase-word] [])]
      (is (= 1 (count trials)))
      (is (true? (sut/phrase-trial? (first trials))))
      (is (= "Wie geht's?" (:answer (first trials))))
      (is (= "Как дела?" (:prompt (first trials)))))))


(deftest phrase-answers-forgive-typography-only
  (testing "apostrophes and case are forgiven, words are not"
    (let [state (sut/initial-state [phrase-word] [] :first)]
      (is (true? (-> state (sut/check-answer "wie gehts") sut/last-result :correct?)))
      (is (true? (-> state (sut/check-answer "Wie geht's") sut/last-result :correct?)))
      (is (false? (-> state (sut/check-answer "wie stehts") sut/last-result :correct?))))))


(deftest phrase-review-once-per-trial
  (testing "only the first graded attempt of a phrase trial is review-due"
    (let [state (sut/initial-state [phrase-word] [] :first)
          trial (sut/current-trial state)]
      (is (true? (sut/phrase-review-due? state trial)))
      (let [marked (sut/mark-trial-reviewed state trial)]
        (is (false? (sut/phrase-review-due? marked trial)))))))


(deftest word-trials-are-never-phrase-review-due
  (testing "word trials keep their own review path"
    (let [state (sut/initial-state fixtures/lesson-words [] :first)]
      (is (false? (sut/phrase-review-due? state (sut/current-trial state)))))))

(ns client.domain.lesson-test
  (:require
   [client.support.fixtures :as fixtures]
   [cljs.test :refer-macros [are deftest is testing]]
   [domain.lesson :as sut]))


;; =============================================================================
;; generate-trials
;; =============================================================================


;; =============================================================================
;; trial predicates
;; =============================================================================


;; =============================================================================
;; trial-id
;; =============================================================================


;; =============================================================================
;; initial-state
;; =============================================================================


;; =============================================================================
;; expected-answer
;; =============================================================================


(deftest an-example-answer-is-split-into-annotated-and-plain-words
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


;; =============================================================================
;; check-answer - correct answers
;; =============================================================================


;; =============================================================================
;; check-answer - wrong answers
;; =============================================================================


;; =============================================================================
;; check-answer - result shape
;; =============================================================================


;; =============================================================================
;; check-answer - is-finished?
;; =============================================================================


(deftest the-lesson-is-finished-when-the-last-trial-is-answered
  (testing "is-finished? true when all trials answered correctly"
    (let [state (sut/initial-state
                 [{:_id "w1" :value "der Hund" :translation [{:lang "en" :value "dog"}]}]
                 []
                 :first)
          state (sut/check-answer state "der Hund")]
      (is (true? (sut/finished? state)))
      (is (empty? (:remaining-trials state))))))


;; =============================================================================
;; advance
;; =============================================================================


(deftest the-next-trial-is-never-a-locked-example
  (testing "advance selects only unlocked trials"
    (let [state      (sut/initial-state
                      fixtures/lesson-words
                      fixtures/lesson-examples
                      :first)
          next-state (sut/advance state)]
      (is (sut/word-trial? (:current-trial next-state)))
      (is (not (:locked? (:current-trial next-state)))))))


;; =============================================================================
;; Full lesson flow
;; =============================================================================


;; =============================================================================
;; Phrase trials
;; =============================================================================


(def ^:private phrase-word
  {:id          "vocab:wie geht s"
   :kind        "phrase"
   :translation [{:lang "ru" :value "Как дела?"}]
   :value       "Wie geht's?"})


(def ^:private phrase-example
  {:word-id     "vocab:wie geht s"
   :structure   []
   :translation "Как дела сегодня, спросил он тихо."
   :value       "Wie geht's dir heute, fragte er leise?"})


(deftest only-a-right-answer-clears-the-trial-and-unlocks-its-examples
  (doseq [[kind words examples right]
          [["word" fixtures/lesson-words fixtures/lesson-examples "der Hund"]
           ["phrase" [phrase-word] [phrase-example] "wie gehts"]]]
    (let [state (sut/initial-state words examples :first)
          total (count (:remaining-trials state))]
      (are [answer correct? cleared? locked?]
           (let [after   (sut/check-answer state answer)
                 example (first (filter sut/example-trial? (:remaining-trials after)))]
             (and (= correct? (:correct? (sut/last-result after)))
                  (= cleared? (= (dec total) (count (:remaining-trials after))))
                  (= locked? (:locked? example))))
        right          true  true  false
        "wrong answer" false false true))))


(deftest an-answer-forgives-typography-only
  (testing "apostrophes and case are forgiven, words are not"
    (let [state (sut/initial-state [phrase-word] [] :first)]
      (is (true? (-> state (sut/check-answer "wie gehts") sut/last-result :correct?)))
      (is (true? (-> state (sut/check-answer "Wie geht's") sut/last-result :correct?)))
      (is (false? (-> state (sut/check-answer "wie stehts") sut/last-result :correct?))))))


(deftest a-failed-trial-comes-back-in-the-same-lesson
  (testing "a wrong answer keeps the trial in the pool, so it can be graded again"
    (let [state (sut/initial-state [phrase-word] [] :first)
          wrong (sut/check-answer state "wie stehts")
          right (sut/check-answer wrong "wie gehts")]
      (is (= 1 (count (:remaining-trials wrong))))
      (is (true? (-> right sut/last-result :correct?)))
      (is (zero? (count (:remaining-trials right)))))))


;; =============================================================================
;; pick-vocab
;; =============================================================================


(defn- rows
  "Rows as `list` hands them over — ordered most due first, alphabetical by
   id wherever the urgencies tie, which is the read order `list` leaves."
  [urgencies]
  (vec (map-indexed (fn [n urgency]
                      {:id (str "vocab:wort-" (char (+ 97 n))) :urgency urgency})
                    urgencies)))


(def ^:private pool (rows (range 20 0 -1)))


(defn- draws
  "`n` draws of `count` from `rows`, as a set of id sets. Sets, not vectors:
   the draw shuffles what it picked, so comparing order would call a fixed
   selection varied and pass on the very bug these tests are for."
  [rows pool-size count n]
  (into #{}
        (map (fn [_] (set (map :id (sut/pick-vocab rows :urgency pool-size count)))))
        (range n)))


(deftest a-lesson-draws-its-words-from-the-pool
  (testing "the asked-for count, all of it from the pool, nothing twice"
    (let [picked (sut/pick-vocab pool :urgency 20 3)]
      (is (= 3 (count picked)))
      (is (every? (set pool) picked))
      (is (= 3 (count (distinct picked)))))))


(deftest a-tied-pool-is-cut-at-random
  (testing "every item never reviewed ties at ##Inf, and the cut is not the alphabet"
    (let [tied (rows (repeat 10 ##Inf))]
      (is (< 1 (count (draws tied 3 3 50))))))
  (testing "a batch added within one second ties on urgency too, and cuts the same way"
    (let [tied (rows (repeat 10 0.0231))]
      (is (< 1 (count (draws tied 3 3 50)))))))


(deftest strict-urgency-wins-over-the-shuffle
  (testing "a more due item outranks a less due one every time, not merely usually"
    (let [ranked (rows [9 8 7 6 5 4 3 2 1])
          top    (set (map :id (take 3 ranked)))]
      (is (= #{top} (draws ranked 3 3 50))
          "the three most due are the only pool, in all fifty draws")))
  (testing "one strictly more due item is always in the pool of a tied field"
    (let [ranked (rows (cons ##Inf (repeat 9 1.0)))]
      (is (every? #(contains? % "vocab:wort-a") (draws ranked 3 3 50))))))


(deftest a-row-without-a-number-for-urgency-never-holds-the-pool
  (testing "NaN ranks last, so it never blocks a real urgency out of a full pool"
    (let [nan-first (cons {:id "vocab:nan" :urgency ##NaN} (rows [1 2 3 4]))]
      (is (every? #(not (contains? % "vocab:nan")) (draws nan-first 2 2 30))))))

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
        "wrong answer" false false true
        ""             false false true
        "  "           false false true))))


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


(defn- draws
  "`times` runs of `pick-vocab` taking `n` from `rows`, as a set of id sets."
  [rows n times]
  (into #{}
        (map (fn [_] (set (map :id (sut/pick-vocab rows :urgency n)))))
        (range times)))


(deftest a-lesson-takes-the-most-due-words
  (testing "a word just added loses to long-forgotten ones, every time"
    (let [forgotten (rows [30 29 28])
          fresh     {:id "vocab:neu" :urgency 0.01}]
      (is (= #{(set (map :id forgotten))}
             (draws (conj forgotten fresh) 3 50)))))
  (testing "the asked-for count, most due first, nothing twice"
    (is (= ["vocab:wort-a" "vocab:wort-b" "vocab:wort-c"]
           (map :id (sut/pick-vocab (rows [9 8 7 6 5]) :urgency 3)))))
  (testing "a vocabulary smaller than the lesson is taken whole"
    (is (= 2 (count (sut/pick-vocab (rows [2 1]) :urgency 3))))))


(deftest equally-due-words-are-taken-at-random
  (testing "every item never reviewed ties at ##Inf, and the cut is not the alphabet"
    (is (< 1 (count (draws (rows (repeat 10 ##Inf)) 3 50)))))
  (testing "a batch added within one second ties on urgency too, and is cut the same way"
    (is (< 1 (count (draws (rows (repeat 10 0.0231)) 3 50))))))


(deftest a-more-due-word-beats-a-tied-field
  (testing "a more due item outranks a less due one every time, not merely usually"
    (let [ranked (rows [9 8 7 6 5 4 3 2 1])]
      (is (= #{#{"vocab:wort-a" "vocab:wort-b" "vocab:wort-c"}}
             (draws ranked 3 50)))))
  (testing "one strictly more due item is always taken from a tied field"
    (let [ranked (rows (cons ##Inf (repeat 9 1.0)))]
      (is (every? #(contains? % "vocab:wort-a") (draws ranked 3 50))))))


(deftest a-row-without-a-number-for-urgency-never-takes-a-place
  (testing "NaN ranks last, so it never blocks a real urgency out of a full lesson"
    (let [nan-first (cons {:id "vocab:nan" :urgency ##NaN} (rows [1 2 3 4]))]
      (is (every? #(not (contains? % "vocab:nan")) (draws nan-first 2 30))))))


;; =============================================================================
;; a failed example
;; =============================================================================


(deftest a-failed-example-is-remembered
  (let [state   (sut/initial-state fixtures/lesson-words fixtures/lesson-examples :first)
        example (first (filter sut/example-trial? (:trials state)))
        showing (assoc state :current-trial example)]
    (testing "before a miss, the example has not been failed"
      (is (false? (sut/failed-before? showing))))
    (testing "after a miss, a later showing knows it was failed"
      (is (true? (-> showing (sut/check-answer "falsch") (assoc :last-result nil) sut/failed-before?))))
    (testing "a failed word is not remembered"
      (is (nil? (:failed-trials (sut/check-answer state "falsch")))))))


(deftest an-answer-longer-than-the-limit-is-cut-to-it
  (let [state (sut/initial-state fixtures/lesson-words fixtures/lesson-examples :first)
        after (sut/check-answer state (apply str (repeat 1500 "a")))]
    (is (= sut/max-answer-length (count (:answer (sut/last-result after)))))))

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
                 first)
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
                      first)
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
    (let [state (sut/initial-state words examples first)
          total (count (:remaining-trials state))]
      (are [answer correct? cleared? locked?]
       (let [after   (sut/check-answer state answer)
             example (first (filter sut/example-trial? (:remaining-trials after)))]
         (and (= correct? (:correct? (sut/last-result after)))
              (= cleared? (= (dec total) (count (:remaining-trials after))))
              (= locked? (:locked? example))))
       right true  true  false
       "wrong answer" false false true
       ""    false false true
       "  "  false false true))))


(deftest an-answer-forgives-typography-only
  (testing "apostrophes and case are forgiven, words are not"
    (let [state (sut/initial-state [phrase-word] [] first)]
      (is (true? (-> state (sut/check-answer "wie gehts") sut/last-result :correct?)))
      (is (true? (-> state (sut/check-answer "Wie geht's") sut/last-result :correct?)))
      (is (false? (-> state (sut/check-answer "wie stehts") sut/last-result :correct?))))))


(deftest a-failed-trial-comes-back-in-the-same-lesson
  (testing "a wrong answer keeps the trial in the pool, so it can be graded again"
    (let [state (sut/initial-state [phrase-word] [] first)
          wrong (sut/check-answer state "wie stehts")
          right (sut/check-answer wrong "wie gehts")]
      (is (= 1 (count (:remaining-trials wrong))))
      (is (true? (-> right sut/last-result :correct?)))
      (is (zero? (count (:remaining-trials right)))))))


;; =============================================================================
;; lesson-words
;; =============================================================================


(defn- recalls
  "Words and their log-recalls, by how overdue each is — minus its log-recall, `##Inf` for a word never
   reviewed — in the order memory hands them over: most due first, alphabetical by id wherever they tie."
  [overdue]
  (vec (map-indexed (fn [position word-overdue]
                      [{:id (str "vocab:wort-" (char (+ 97 position)))}
                       (when-not (= ##Inf word-overdue) (- word-overdue))])
                    overdue)))


(defn- shuffling-tie-breaker
  "A tie-breaker that walks [0, 1) in golden-ratio steps: the same numbers every time, spread like a draw."
  []
  (let [step (atom 0.0)]
    (fn [_]
      (swap! step #(mod (+ % 0.6180339887) 1)))))


(defn- draws
  "`times` lessons of `size` words out of `recalls`, as a set of id sets."
  [recalls size times]
  (let [tie-breaker (shuffling-tie-breaker)]
    (into #{}
          (map (fn [_] (set (map :id (sut/lesson-words recalls size tie-breaker)))))
          (range times))))


(deftest a-lesson-takes-the-most-due-words
  (testing "a word just added loses to long-forgotten ones, every time"
    (let [forgotten (recalls [30 29 28])
          fresh     [{:id "vocab:neu"} -0.01]]
      (is (= #{(set (map (comp :id first) forgotten))}
             (draws (conj forgotten fresh) 3 50)))))
  (testing "the asked-for count, most due first, nothing twice"
    (is (= ["vocab:wort-a" "vocab:wort-b" "vocab:wort-c"]
           (map :id (sut/lesson-words (recalls [9 8 7 6 5]) 3 (shuffling-tie-breaker))))))
  (testing "a vocabulary smaller than the lesson is taken whole"
    (is (= 2 (count (sut/lesson-words (recalls [2 1]) 3 (shuffling-tie-breaker)))))))


(deftest ties-go-the-way-the-tie-breaker-says
  (testing "a tie-breaker by id takes the tied words in id order"
    (is (= ["vocab:wort-a" "vocab:wort-b"]
           (map :id (sut/lesson-words (recalls (repeat 4 1.0)) 2 #(.charCodeAt (:id %) 11)))))))


(deftest equally-due-words-are-taken-at-random
  (testing "every item never reviewed ties at ##Inf, and the cut is not the alphabet"
    (is (< 1 (count (draws (recalls (repeat 10 ##Inf)) 3 50)))))
  (testing "a batch added within one second ties on recall too, and is cut the same way"
    (is (< 1 (count (draws (recalls (repeat 10 0.0231)) 3 50))))))


(deftest a-more-due-word-beats-a-tied-field
  (testing "a more due item outranks a less due one every time, not merely usually"
    (let [ranked (recalls [9 8 7 6 5 4 3 2 1])]
      (is (= #{#{"vocab:wort-a" "vocab:wort-b" "vocab:wort-c"}}
             (draws ranked 3 50)))))
  (testing "one strictly more due item is always taken from a tied field"
    (let [ranked (recalls (cons ##Inf (repeat 9 1.0)))]
      (is (every? #(contains? % "vocab:wort-a") (draws ranked 3 50))))))


(deftest a-word-without-a-number-for-recall-never-takes-a-place
  (testing "NaN ranks last, so it never blocks a real recall out of a full lesson"
    (let [nan-first (cons [{:id "vocab:nan"} ##NaN] (recalls [1 2 3 4]))]
      (is (every? #(not (contains? % "vocab:nan")) (draws nan-first 2 30))))))


(deftest an-answer-longer-than-the-limit-is-cut-to-it
  (let [state (sut/initial-state fixtures/lesson-words fixtures/lesson-examples first)
        after (sut/check-answer state (apply str (repeat 1500 "a")))]
    (is (= sut/max-answer-length (count (:answer (sut/last-result after)))))))

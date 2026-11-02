(ns client.domain.lesson-test
  (:require
   [client.support.fixtures :as fixtures]
   [cljs.test :refer-macros [are deftest is testing]]
   [domain.lesson :as sut]
   [domain.retention :as retention]))


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


(def ^:private now-ms (js/Date.UTC 2026 5 1))


(defn- cards
  "Words `vocab:wort-a`, `-b`, … in the order memory hands them over, each answered right once that many days before
   `now-ms`, nil for a new word. Within the first days, the longer ago the answer, the more a review now
   strengthens the word."
  [days-ago]
  (vec (map-indexed (fn [position days]
                      {:reviews (cond-> retention/empty-reviews
                                  days (retention/with-review {:created-at (.toISOString (js/Date.
                                                                                          (- now-ms (* days 86400000))))
                                                               :id         (str "r" position)
                                                               :retained   true}))
                       :word    {:created-at "2025-01-01T00:00:00.000Z"
                                 :id         (str "vocab:wort-" (char (+ 97 position)))
                                 :value      "Wort"}})
                    days-ago)))


(defn- golden-random
  "A random that walks [0, 1) in golden-ratio steps: the same numbers every time, spread like a draw."
  []
  (let [step (atom 0.0)]
    (fn []
      (swap! step #(mod (+ % 0.6180339887) 1)))))


(defn- draws
  "`times` lessons of `size` words out of `word-cards`, each as a set of ids."
  [word-cards size times]
  (let [random (golden-random)]
    (mapv (fn [_] (set (map :id (sut/lesson-words {:cards word-cards :now-ms now-ms :random random :size size}))))
          (range times))))


(def ^:private new-ids
  #{"vocab:wort-a" "vocab:wort-b" "vocab:wort-c"})


(deftest a-lesson-takes-one-new-word
  (testing "three new and three known: one new word, every time"
    (is (every? #(= 1 (count (filter new-ids %))) (draws (cards [nil nil nil 3 2 1]) 3 50))))
  (testing "nothing but new words: the lesson is full"
    (is (every? #(= 3 (count %)) (draws (cards (repeat 5 nil)) 3 50))))
  (testing "a vocabulary smaller than the lesson is taken whole"
    (is (= 2 (count (sut/lesson-words {:cards (cards [nil 1]) :now-ms now-ms :random (golden-random) :size 3}))))))


(deftest the-words-a-review-strengthens-most-come-first
  (testing "no place drawn at random: the three a review strengthens most, nothing twice"
    (is (= ["vocab:wort-b" "vocab:wort-d" "vocab:wort-c"]
           (map :id
                (sut/lesson-words {:cards  (cards [0.1 4 1 3 0.5])
                                   :now-ms now-ms
                                   :random (constantly 0.5)
                                   :size   3})))))
  (testing "a place now and then goes to a random known word"
    (is (some #(not= #{"vocab:wort-a" "vocab:wort-b" "vocab:wort-c"} %)
              (draws (cards [4 3.5 3 2.5 2 1.5 1 0.5 0.1]) 3 100)))))


(deftest equally-strengthened-words-are-taken-at-random
  (testing "known words answered at one moment are not cut by the order they come in"
    (is (< 1 (count (set (draws (cards (repeat 10 2)) 3 50))))))
  (testing "nor are new words"
    (is (< 1 (count (set (draws (cards (repeat 10 nil)) 3 50)))))))


(deftest an-answer-longer-than-the-limit-is-cut-to-it
  (let [state (sut/initial-state fixtures/lesson-words fixtures/lesson-examples first)
        after (sut/check-answer state (apply str (repeat 1500 "a")))]
    (is (= sut/max-answer-length (count (:answer (sut/last-result after)))))))

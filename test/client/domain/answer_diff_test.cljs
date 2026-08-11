(ns client.domain.answer-diff-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [domain.answer-diff :as sut]))


(defn- statuses
  [segments]
  (mapv :status segments))


(deftest identical-answers-match
  (testing "typography differences produce a full match"
    (let [{:keys [answer expected]} (sut/answer-diff "wie gehts" "Wie geht's?")]
      (is (every? #(= :match (:status %)) answer))
      (is (every? #(= :match (:status %)) expected)))))


(deftest wrong-word-in-the-middle
  (testing "a substituted word is :wrong in the answer, :missing in the expected"
    (let [{:keys [answer expected]} (sut/answer-diff
                                     "auf keinen Fall heute"
                                     "auf jeden Fall heute")]
      (is (= [:match :wrong :match :match] (statuses answer)))
      (is (= [:match :missing :match :match] (statuses expected)))
      (is (= "keinen" (:text (second answer))))
      (is (= "jeden" (:text (second expected)))))))


(deftest extra-word-in-answer
  (testing "a surplus word with no counterpart is :extra"
    (let [{:keys [answer expected]} (sut/answer-diff
                                     "auf jeden guten Fall"
                                     "auf jeden Fall")]
      (is (= [:match :match :extra :match] (statuses answer)))
      (is (= [:match :match :match] (statuses expected))))))


(deftest missing-word-in-answer
  (testing "an unmet expected word is :missing"
    (let [{:keys [answer expected]} (sut/answer-diff
                                     "auf Fall"
                                     "auf jeden Fall")]
      (is (= [:match :match] (statuses answer)))
      (is (= [:match :missing :match] (statuses expected))))))


(deftest empty-answer-is-all-missing
  (testing "an empty answer leaves the whole expected side missing"
    (let [{:keys [answer expected]} (sut/answer-diff "" "auf jeden Fall")]
      (is (= [] answer))
      (is (= [:missing :missing :missing] (statuses expected))))))

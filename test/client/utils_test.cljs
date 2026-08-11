(ns client.utils-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [utils :as sut]))


(deftest normalize-for-grading-forgives-typography
  (testing "apostrophe variants vanish instead of splitting the word"
    (is (= "wie gehts" (sut/normalize-for-grading "Wie geht's?")))
    (is (= "wie gehts" (sut/normalize-for-grading "Wie geht’s?")))
    (is (= "wie gehts" (sut/normalize-for-grading "Wie gehtʼs?"))))
  (testing "unicode quotes and dashes read as spaces"
    (is (= "na gut sagte er" (sut/normalize-for-grading "«Na gut» — sagte er"))))
  (testing "umlauts and case fold like the id normalization"
    (is (= "schoene gruesse" (sut/normalize-for-grading "Schöne Grüße")))))


(deftest normalize-for-grading-keeps-words-strict
  (testing "different words stay different"
    (is (not= (sut/normalize-for-grading "auf jeden Fall")
              (sut/normalize-for-grading "auf keinen Fall")))))

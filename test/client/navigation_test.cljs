(ns client.navigation-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [ports.navigation :as sut]))


(deftest move-keeps-the-history-at-home-and-one-screen
  (testing "home to home writes nothing"
    (is (nil? (sut/move {:at-home? true :over-home? false :to-home? true}))))
  (testing "a screen opened from home is pushed and marked"
    (is (= {:over-home? true :write :push}
           (sut/move {:at-home? true :over-home? false :to-home? false}))))
  (testing "a screen opened from a screen takes its entry and its mark"
    (is (= {:over-home? true :write :replace}
           (sut/move {:at-home? false :over-home? true :to-home? false}))))
  (testing "home from a screen standing on home steps back onto it"
    (is (= {:write :back}
           (sut/move {:at-home? false :over-home? true :to-home? true}))))
  (testing "home from an unmarked screen replaces it"
    (is (= {:over-home? false :write :replace}
           (sut/move {:at-home? false :over-home? false :to-home? true}))))
  (testing "a screen opened from an unmarked screen stays unmarked"
    (is (= {:over-home? false :write :replace}
           (sut/move {:at-home? false :over-home? false :to-home? false})))))


(deftest home-beneath-is-written-only-under-a-fresh-landing-on-a-screen
  (testing "a screen opened directly"
    (is (true? (sut/home-beneath-needed? {:home? false :over-home? false :route? true}))))
  (testing "home needs nothing beneath"
    (is (false? (sut/home-beneath-needed? {:home? true :over-home? false :route? true}))))
  (testing "a reload or back/forward onto a marked screen needs nothing"
    (is (false? (sut/home-beneath-needed? {:home? false :over-home? true :route? true}))))
  (testing "an unknown path is the router's"
    (is (not (sut/home-beneath-needed? {:home? false :over-home? false :route? false})))))

(ns client.domain.examples-test
  (:require
   [cljs.test :refer-macros [are deftest is testing]]
   [domain.examples :as sut]))


(deftest a-word-shows-the-example-with-the-smallest-id
  (is (= {:id "a"} (sut/lesson-example [{:id "b"} {:id "a"}])))
  (is (nil? (sut/lesson-example []))))


(deftest an-example-is-complete-with-its-sentence-and-its-translation
  (are [example complete?] (= complete? (sut/complete? example))
   {:value "Er isst." :translation "Он ест."} true
   {:value "Er isst." :translation ""} false
   {:value "Er isst." :translation "   "} false
   {:value "  " :translation "Он ест."} false
   {:value "" :translation ""} false))

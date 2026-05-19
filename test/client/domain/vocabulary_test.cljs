(ns client.domain.vocabulary-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [domain.vocabulary :as sut]))


(deftest new-word-doc-creates-vocab-document
  (testing "user adds a new word"
    (let [word (sut/new-word "der Hund" [{:lang "ru" :value "пёс"}])]
      (is (= "vocab" (:type word)))
      (is (= "der Hund" (:value word)))
      (is (= "пёс" (-> word :translation first :value)))
      (is (= "ru" (-> word :translation first :lang))))))


(deftest update-word-doc-updates-values
  (testing "user updates an existing word"
    (let [word    (sut/new-word "der Hund" [{:lang "ru" :value "пёс"}])
          updated (sut/update-word word "лиса")]
      (is (= "der Hund" (:value updated)))
      (is (= "лиса" (-> updated :translation first :value))))))


(deftest new-review-doc-creates-review-document
  (testing "user reviews a word"
    (let [review (sut/new-review "word-1" true "пёс")]
      (is (= "review" (:type review)))
      (is (= "word-1" (:word-id review)))
      (is (true? (:retained review)))
      (is (= "пёс" (-> review :translation first :value)))
      (is (= "ru" (-> review :translation first :lang))))))

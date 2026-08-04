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


(deftest normalize-value-is-a-frozen-contract
  (testing "lowercase, umlaut fold, punctuation to space — changing this remaps ids"
    (is (= "gross" (sut/normalize-value "GROSS")))
    (is (= "fussball" (sut/normalize-value "Fußball")))
    (is (= "tuer" (sut/normalize-value "Tür")))
    (is (= "maedchen" (sut/normalize-value "Mädchen")))
    (is (= "der hund" (sut/normalize-value "der Hund")))))


(deftest new-word-is-content-addressed
  (testing "vocab _id is the content-addressed id of its value"
    (let [value "der Hund"]
      (is (= (sut/vocab-id value)
             (:_id (sut/new-word value [{:lang "ru" :value "пёс"}])))))))


(deftest vocab-id-is-case-insensitive
  (testing "the same word yields the same id regardless of case"
    (is (= (sut/vocab-id "der Hund") (sut/vocab-id "DER HUND")))))


(deftest merge-translations-unions-by-value
  (testing "keeps existing translations and adds only unseen ones (conflict union)"
    (is (= [{:lang "ru" :value "пёс"} {:lang "ru" :value "собака"}]
           (sut/merge-translations [{:lang "ru" :value "пёс"}]
                                   [{:lang "ru" :value "пёс"}
                                    {:lang "ru" :value "собака"}])))))

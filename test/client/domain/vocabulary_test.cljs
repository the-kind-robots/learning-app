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


(deftest a-translation-is-kept-as-it-was-typed
  (testing "punctuation belongs to the translation, not to a separator (GH-365)"
    (is (= [{:lang "ru" :value "без того, чтобы"}]
           (sut/parse-translations "без того, чтобы")))
    (is (= [{:lang "ru" :value "хотя..,но.."}]
           (sut/parse-translations "хотя..,но..")))
    (is (= [{:lang "ru" :value "решать; решить"}]
           (sut/parse-translations "решать; решить")))
    (is (= [{:lang "ru" :value "и/или"}]
           (sut/parse-translations "и/или"))))
  (testing "several lines are one translation"
    (is (= [{:lang "ru" :value "пёс\nсобака"}]
           (sut/parse-translations "пёс\nсобака"))))
  (testing "surrounding whitespace is dropped"
    (is (= [{:lang "ru" :value "пёс"}]
           (sut/parse-translations "  пёс  "))))
  (testing "nothing typed is no translation, so the add form can refuse it"
    (is (= [] (sut/parse-translations "")))
    (is (= [] (sut/parse-translations "   ")))
    (is (= [] (sut/parse-translations nil)))))


(deftest editing-keeps-the-translation-as-it-was-typed
  (testing "the edit path shares parse-translations, so it inherits the rule"
    (let [word    (sut/new-word "ohne" [{:lang "ru" :value "без"}])
          updated (sut/update-word word "без того, чтобы")]
      (is (= [{:lang "ru" :value "без того, чтобы"}] (:translation updated))))))


(deftest a-word-written-before-the-rule-is-read-as-it-is
  (testing "several stored entries survive a merge untouched — no migration"
    (let [stored [{:lang "ru" :value "пёс"} {:lang "ru" :value "собака"}]]
      (is (= [{:lang "ru" :value "пёс"}
              {:lang "ru" :value "собака"}
              {:lang "ru" :value "пёс, собака"}]
             (sut/merge-translations stored (sut/parse-translations "пёс, собака")))))))


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

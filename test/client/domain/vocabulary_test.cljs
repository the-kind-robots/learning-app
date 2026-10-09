(ns client.domain.vocabulary-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [domain.vocabulary :as sut]))


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


(deftest a-word-written-before-the-rule-is-read-as-it-is
  (testing "several stored entries survive a merge untouched — no migration"
    (let [stored [{:lang "ru" :value "пёс"} {:lang "ru" :value "собака"}]]
      (is (= [{:lang "ru" :value "пёс"}
              {:lang "ru" :value "собака"}
              {:lang "ru" :value "пёс, собака"}]
             (sut/merge-translations stored (sut/parse-translations "пёс, собака")))))))


(deftest normalize-value-is-a-frozen-contract
  (testing "lowercase, umlaut fold, punctuation to space — changing this remaps ids"
    (is (= "gross" (sut/normalize-value "GROSS")))
    (is (= "fussball" (sut/normalize-value "Fußball")))
    (is (= "tuer" (sut/normalize-value "Tür")))
    (is (= "maedchen" (sut/normalize-value "Mädchen")))
    (is (= "der hund" (sut/normalize-value "der Hund")))))


(deftest merge-translations-unions-by-value
  (testing "keeps existing translations and adds only unseen ones (conflict union)"
    (is (= [{:lang "ru" :value "пёс"} {:lang "ru" :value "собака"}]
           (sut/merge-translations [{:lang "ru" :value "пёс"}]
                                   [{:lang "ru" :value "пёс"}
                                    {:lang "ru" :value "собака"}])))))


(def ^:private issue-438-words
  ["der Hund" "die Katze" "das Auto" "der Zug" "die Bank" "aufstehen"])


(deftest a-word-is-filed-without-its-article
  ;; The issue's own illustration lists `das Auto` before `aufstehen`; `auf`
  ;; sorts before `aut`, so the alphabet puts the verb first. The article is
  ;; what this proves — Auto under A, Bank under B, Zug under Z.
  (testing "the reader looks for der Zug under Z, not under D (#438)"
    (is (= ["aufstehen" "das Auto" "die Bank" "der Hund" "die Katze" "der Zug"]
           (->> issue-438-words
                (sort-by (comp sut/filed-under sut/vocab-id))
                vec))))
  (testing "only a whole article is dropped, so a word that merely starts like one keeps its letter"
    (is (= ["dasselbe" "der Dieb" "diebisch"]
           (->> ["diebisch" "der Dieb" "dasselbe"]
                (sort-by (comp sut/filed-under sut/vocab-id))
                vec))))
  (testing "the article is only ignored, never removed from what is stored"
    (is (= "vocab:der zug" (sut/vocab-id "der Zug"))))
  (testing "an article-less word and the same noun with one tie on the first element"
    (is (= [["zug" "vocab:der zug"] ["zug" "vocab:zug"]]
           (->> ["Zug" "der Zug"]
                (sort-by (comp sut/filed-under sut/vocab-id))
                (mapv (comp sut/filed-under sut/vocab-id)))))))

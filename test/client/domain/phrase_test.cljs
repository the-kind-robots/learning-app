(ns client.domain.phrase-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [domain.phrase :as sut]))


(deftest a-document-without-a-kind-is-a-word
  (testing "documents written before phrases existed are words"
    (is (true? (sut/phrase? {:kind "phrase" :type "vocab"})))
    (is (false? (sut/phrase? {:type "vocab"})))))


(deftest the-input-is-a-phrase-when-it-has-more-than-a-word-and-its-article
  (testing "single word is not a phrase"
    (is (false? (sut/phrase-value? "Fenster" nil))))
  (testing "multi-word input is a phrase"
    (is (true? (sut/phrase-value? "auf jeden Fall" nil)))
    (is (true? (sut/phrase-value? "Entschuldigung dass ich zu spät komme" nil))))
  (testing "article plus one word stays a word"
    (is (false? (sut/phrase-value? "der Tisch" nil)))
    (is (false? (sut/phrase-value? "eine Frau" nil))))
  (testing "sich plus one word stays a word"
    (is (false? (sut/phrase-value? "sich freuen" nil))))
  (testing "article plus two words is a phrase"
    (is (true? (sut/phrase-value? "der frühe Vogel" nil))))
  (testing "a non-phrase part of speech keeps a word"
    (is (false? (sut/phrase-value? "Guten Morgen" "noun"))))
  (testing "pos=phrase does not veto a phrase"
    (is (true? (sut/phrase-value? "auf jeden Fall" "phrase"))))
  (testing "pos=phrase beats the article exception"
    (is (true? (sut/phrase-value? "das heißt" "phrase")))
    (is (true? (sut/phrase-value? "ein paar" "phrase")))))


(deftest the-dictionary-gives-a-value-the-part-of-speech-of-its-own-lemma
  (testing "a shown completion whose lemma is the value"
    (is (= "noun" (sut/dictionary-pos "Guten Morgen" nil [{:lemma "guten Morgen" :pos "noun"}]))))
  (testing "a completion whose lemma is not the value decides nothing"
    (is (nil? (sut/dictionary-pos "der Tisch" nil [{:lemma "der Tisch ist rund" :pos "phrase"}]))))
  (testing "the picked suggestion while the value is its lemma"
    (is (= "phrase" (sut/dictionary-pos "das heißt" {:lemma "das heißt" :pos "phrase"} []))))
  (testing "a pick the value has typed on from decides nothing"
    (is (nil? (sut/dictionary-pos "das heißt so" {:lemma "das heißt" :pos "phrase"} [])))))


(deftest only-a-multi-word-phrase-lemma-is-a-phrase-suggestion
  (testing "multi-word pos=phrase lemma flips to phrase"
    (is (true? (sut/phrase-suggestion? {:lemma "auf jeden Fall" :pos "phrase"}))))
  (testing "single-word phrase lemmas stay words"
    (is (false? (sut/phrase-suggestion? {:lemma "hallo" :pos "phrase"}))))
  (testing "multi-word non-phrase lemmas stay words"
    (is (false? (sut/phrase-suggestion? {:lemma "sich freuen" :pos "verb"})))))

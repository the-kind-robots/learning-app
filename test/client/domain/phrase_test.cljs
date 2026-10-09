(ns client.domain.phrase-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [domain.phrase :as sut]))


(deftest a-document-without-a-kind-is-a-word
  (testing "documents written before phrases existed are words"
    (is (true? (sut/phrase-doc? {:kind "phrase" :type "vocab"})))
    (is (false? (sut/phrase-doc? {:type "vocab"})))))


(deftest the-input-is-a-phrase-when-it-has-more-than-a-word-and-its-article
  (testing "single word is not a phrase"
    (is (false? (sut/phrase-value? "Fenster" []))))
  (testing "multi-word input is a phrase"
    (is (true? (sut/phrase-value? "auf jeden Fall" [])))
    (is (true? (sut/phrase-value? "Entschuldigung dass ich zu spät komme" []))))
  (testing "article plus one word stays a word"
    (is (false? (sut/phrase-value? "der Tisch" [])))
    (is (false? (sut/phrase-value? "eine Frau" []))))
  (testing "sich plus one word stays a word"
    (is (false? (sut/phrase-value? "sich freuen" []))))
  (testing "article plus two words is a phrase"
    (is (true? (sut/phrase-value? "der frühe Vogel" []))))
  (testing "a non-phrase dictionary lemma among completions keeps word mode"
    (is (false? (sut/phrase-value? "Guten Morgen"
                                   [{:lemma "guten Morgen" :pos "noun"}]))))
  (testing "a pos=phrase completion does not veto phrase mode"
    (is (true? (sut/phrase-value? "auf jeden Fall"
                                  [{:lemma "auf jeden Fall" :pos "phrase"}]))))
  (testing "a pos=phrase lemma beats the article exception"
    (is (true? (sut/phrase-value? "das heißt"
                                  [{:lemma "das heißt" :pos "phrase"}])))
    (is (true? (sut/phrase-value? "ein paar"
                                  [{:lemma "ein paar" :pos "phrase"}]))))
  (testing "a phrase lemma that is not the value itself decides nothing"
    (is (false? (sut/phrase-value? "der Tisch"
                                   [{:lemma "der Tisch ist rund" :pos "phrase"}])))))


(deftest only-a-multi-word-phrase-lemma-is-a-phrase-suggestion
  (testing "multi-word pos=phrase lemma flips to phrase"
    (is (true? (sut/phrase-suggestion? {:lemma "auf jeden Fall" :pos "phrase"}))))
  (testing "single-word phrase lemmas stay words"
    (is (false? (sut/phrase-suggestion? {:lemma "hallo" :pos "phrase"}))))
  (testing "multi-word non-phrase lemmas stay words"
    (is (false? (sut/phrase-suggestion? {:lemma "sich freuen" :pos "verb"})))))



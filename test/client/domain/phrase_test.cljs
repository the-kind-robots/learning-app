(ns client.domain.phrase-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [domain.phrase :as sut]))


(deftest phrase-id-is-content-addressed
  (testing "id keeps spaces and normalizes like vocab ids"
    (is (= "phrase:auf jeden fall" (sut/phrase-id "Auf jeden Fall")))
    ;; the frozen id normalization turns the apostrophe into a space —
    ;; typography forgiveness lives only in grading, not in ids (ADR-0011)
    (is (= "phrase:wie geht s" (sut/phrase-id "Wie geht's?")))))


(deftest new-phrase-collapses-whitespace-and-keeps-translation-whole
  (testing "value whitespace collapses, translation stays one entry"
    (let [doc (sut/new-phrase "auf  jeden\n Fall" "во всяком случае, обязательно.")]
      (is (= "phrase:auf jeden fall" (:_id doc)))
      (is (= "phrase" (:type doc)))
      (is (= "auf jeden Fall" (:value doc)))
      (is (= [{:lang "ru" :value "во всяком случае, обязательно."}] (:translation doc))))))


(deftest phrase-value-detection
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


(deftest phrase-suggestion-detection
  (testing "multi-word pos=phrase lemma flips to phrase"
    (is (true? (sut/phrase-suggestion? {:lemma "auf jeden Fall" :pos "phrase"}))))
  (testing "single-word phrase lemmas stay words"
    (is (false? (sut/phrase-suggestion? {:lemma "hallo" :pos "phrase"}))))
  (testing "multi-word non-phrase lemmas stay words"
    (is (false? (sut/phrase-suggestion? {:lemma "sich freuen" :pos "verb"})))))


(deftest add-mode-override-wins
  (testing "override beats the heuristic in both directions"
    (is (= :word (sut/add-mode "auf jeden Fall" [] :word)))
    (is (= :phrase (sut/add-mode "Fenster" [] :phrase)))
    (is (= :phrase (sut/add-mode "auf jeden Fall" [] nil)))
    (is (= :word (sut/add-mode "Fenster" [] nil)))))


(deftest update-phrase-replaces-translation-whole
  (testing "edit keeps the sentence as one entry"
    (let [doc (sut/new-phrase "auf jeden Fall" "обязательно")]
      (is (= [{:lang "ru" :value "во всяком случае, точно."}]
             (:translation (sut/update-phrase doc "во всяком случае, точно.")))))))

(ns client.domain.phrase-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [domain.phrase :as sut]))


(deftest new-phrase-shares-the-vocabulary-namespace
  (testing "id is the value, so a phrase and a word cannot both hold it"
    (is (= "vocab:auf jeden fall" (:_id (sut/new-phrase "Auf jeden Fall" "перевод"))))
    ;; the frozen id normalization turns the apostrophe into a space —
    ;; typography forgiveness lives only in grading, not in ids (ADR-0008)
    (is (= "vocab:wie geht s" (:_id (sut/new-phrase "Wie geht's?" "перевод"))))))


(deftest new-phrase-collapses-whitespace-and-keeps-translation-whole
  (testing "value whitespace collapses, translation stays one entry"
    (let [doc (sut/new-phrase "auf  jeden\n Fall" "во всяком случае, обязательно.")]
      (is (= "vocab:auf jeden fall" (:_id doc)))
      (is (= "vocab" (:type doc)))
      (is (= "phrase" (:kind doc)))
      (is (= "auf jeden Fall" (:value doc)))
      (is (= [{:lang "ru" :value "во всяком случае, обязательно."}] (:translation doc))))))


(deftest phrase-doc-detection-treats-a-missing-kind-as-a-word
  (testing "documents written before phrases existed are words"
    (is (true? (sut/phrase-doc? {:kind "phrase" :type "vocab"})))
    (is (false? (sut/phrase-doc? {:type "vocab"})))))


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


(deftest add-mode-without-a-pick-follows-the-value
  (testing "nothing picked leaves the heuristic alone"
    (is (= :phrase (sut/add-mode "auf jeden Fall" [] nil)))
    (is (= :word (sut/add-mode "Fenster" [] nil)))
    (is (= :word (sut/add-mode "" [] nil)))))


(deftest add-mode-pick-beats-the-heuristic-on-its-own-lemma
  (testing "a pick decides both directions for the lemma it was made for"
    (is (= :word (sut/add-mode "auf jeden Fall" [] {:mode :word :value "auf jeden Fall"})))
    ;; the suggestions are cleared by the pick, so nothing but the pick knows
    ;; this article pair is a phrase
    (is (= :phrase (sut/add-mode "das heißt" [] {:mode :phrase :value "das heißt"}))))
  (testing "the value is the vocabulary identity, not the typed characters"
    (is (= :phrase (sut/add-mode "Das Heißt " [] {:mode :phrase :value "das heißt"})))))


(deftest add-mode-pick-expires-when-the-value-is-edited
  (testing "typing a phrase onto a picked word re-runs the heuristic (GH-358)"
    (is (= :phrase (sut/add-mode "das Haus ist gross" [] {:mode :word :value "das Haus"}))))
  (testing "the picked lemma itself still decides while it is unchanged"
    (is (= :word (sut/add-mode "das Haus" [] {:mode :word :value "das Haus"}))))
  (testing "shortening away from a picked phrase falls back to the value"
    (is (= :word (sut/add-mode "das" [] {:mode :phrase :value "das heißt"})))))


(deftest update-phrase-replaces-translation-whole
  (testing "edit keeps the sentence as one entry"
    (let [doc (sut/new-phrase "auf jeden Fall" "обязательно")]
      (is (= [{:lang "ru" :value "во всяком случае, точно."}]
             (:translation (sut/update-phrase doc "во всяком случае, точно.")))))))

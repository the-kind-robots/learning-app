(ns client.domain.vocabulary-test
  (:require
   [cljs.test :refer-macros [are deftest is testing]]
   [clojure.test.check :as tc]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop :include-macros true]
   [domain.vocabulary :as sut]))


(deftest a-line-is-a-translation-kept-as-it-was-typed
  (testing "punctuation belongs to the translation, not to a separator (GH-365)"
    (is (= [{:lang "ru" :value "без того, чтобы"}]
           (sut/parse-translations "без того, чтобы")))
    (is (= [{:lang "ru" :value "хотя..,но.."}]
           (sut/parse-translations "хотя..,но..")))
    (is (= [{:lang "ru" :value "решать; решить"}]
           (sut/parse-translations "решать; решить")))
    (is (= [{:lang "ru" :value "и/или"}]
           (sut/parse-translations "и/или"))))
  (testing "every line is a translation"
    (is (= [{:lang "ru" :value "пёс"} {:lang "ru" :value "собака"}]
           (sut/parse-translations "пёс\nсобака"))))
  (testing "surrounding whitespace and blank lines are dropped"
    (is (= [{:lang "ru" :value "пёс"}]
           (sut/parse-translations "  пёс  ")))
    (is (= [{:lang "ru" :value "пёс"} {:lang "ru" :value "собака"}]
           (sut/parse-translations "\n пёс \r\n\n  \nсобака\n"))))
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


(def ^:private haus
  "A completion for `häu` with its matched forms."
  {:lemma "das Haus" :matched-forms ["haeuser" "haeusern"]})


(def ^:private gehen
  {:lemma "gehen" :matched-forms ["ging" "gingen" "gingst" "gingt"]})


(def ^:private ruecken
  {:lemma "der Rücken" :matched-forms ["der ruecken" "ruecken" "rueckens"]})


(deftest a-completion-still-completes-the-typed-text-by-its-lemma-or-a-matched-form
  (testing "lemma, article-less lemma and matched forms complete the typed text"
    (are [typed completion] (sut/completes? typed completion)
     "rück"     {:lemma "der Rücken"} ; umlaut and article do not count against the row
     "der Rück" {:lemma "der Rücken"} ; the article typed in full is part of the lemma
     "d"        {:lemma "der Rücken"}
     ""         {:lemma "der Rücken"}
     "Häus"     haus                  ; the inflected form the reader is typing
     "gingst"   gehen
     "Rücken"   ruecken
     "fur"      {:lemma "für" :matched-forms ["fuer" "fur"]})
    (are [typed completion] (not (sut/completes? typed completion))
     "Rückenk" ruecken ; typed past the lemma and every matched form
     "ginge"   {:lemma "gehen" :matched-forms ["ging"]}
     "hu"      {:lemma "das Haus" :matched-forms ["haus"]}
     "rücke"   {:lemma "die Rücksicht"})))


(deftest the-leading-row-is-the-first-whose-lemma-the-typed-text-begins
  (let [sondern {:lemma "sondern" :matched-forms ["gesondert"]}]
    (are [typed completions leading] (= leading (sut/leading-index typed completions))
     "rück" [{:lemma "der Rücken"}] 0
     "der Rück" [{:lemma "der Rücken"}] 0
     "Häus" [haus] nil ; a word form completes the row, it does not lead
     "gesondert" [sondern] nil
     "gesondert" [sondern {:lemma "gesondert"}] 1)))


(deftest the-words-filter-finds-a-word-typed-with-or-without-its-umlauts
  (let [word {:value "für" :translation [{:lang "ru" :value "для"}]}
        text (sut/search-text word)]
    (are [typed] (sut/matches? text (sut/query typed))
     "für"
     "fuer"
     "fur"
     "для")
    (is (not (sut/matches? text (sut/query "fir"))))))


(deftest a-word-without-umlauts-has-one-search-line-per-text
  (is (= "hund\nпёс" (sut/search-text {:value "Hund" :translation [{:lang "ru" :value "пёс"}]}))))


(deftest conflicting-versions-merge-into-the-last-written-with-every-translation
  (let [older {:_rev "1-a" :modified-at "2026-01-01" :translation [{:lang "ru" :value "пёс"}] :value "Hund"}
        newer {:_rev "1-b" :modified-at "2026-02-01" :translation [{:lang "ru" :value "собака"}] :value "der Hund"}]
    (is (= {:_rev        "1-b"
            :modified-at "2026-02-01"
            :translation [{:lang "ru" :value "пёс"} {:lang "ru" :value "собака"}]
            :value       "der Hund"}
           (sut/merged-word :_rev [newer older]))
        "the newer fields win; the translations of both are kept, the older first")
    (is (= "b" (:value (sut/merged-word :_rev [(assoc older :_rev "1-b" :value "b") (assoc older :value "a")])))
        "equal dates: the higher revision")))


(def ^:private gen-versions-in-any-order
  "Versions of one word, few times so that ties are common, and the same versions shuffled."
  (gen/let [versions (gen/vector-distinct-by
                      :_rev
                      (gen/hash-map :_rev        (gen/fmap #(str "1-" %) gen/string-alphanumeric)
                                    :modified-at (gen/elements ["2026-01-01" "2026-01-02"])
                                    :translation (gen/vector (gen/hash-map :lang  (gen/return "ru")
                                                                           :value (gen/elements ["a" "b" "c"]))
                                                             1
                                                             3)
                                    :value       gen/string-alphanumeric)
                      {:max-elements 4 :min-elements 2})
            shuffled (gen/shuffle versions)]
    [versions shuffled]))


(deftest conflicting-versions-merge-the-same-whatever-order-they-come-in
  (is (:pass? (tc/quick-check 300
                              (prop/for-all [[versions shuffled] gen-versions-in-any-order]
                                            (= (sut/merged-word :_rev versions)
                                               (sut/merged-word :_rev shuffled)))))))


(deftest the-edit-dialog-text-is-read-as-the-kind-of-the-word
  (is (= {:created-at  "2026-01-01T00:00:00.000Z"
          :id          "vocab:der hundd"
          :modified-at "2026-02-02T00:00:00.000Z"
          :translation [{:lang "ru" :value "пёс, собака"}]
          :value       "der Hundd"}
         (sut/edited-word {:created-at  "2026-01-01T00:00:00.000Z"
                           :id          "vocab:der hundd"
                           :translation [{:lang "ru" :value "пёс"}]
                           :value       "der Hund"}
                          " der Hundd "
                          "пёс, собака"
                          "2026-02-02T00:00:00.000Z"))
      "a word keeps its translation whole and its value trimmed, and is modified now")
  (is (= {:created-at  "2026-02-02T00:00:00.000Z"
          :id          "vocab:auf jeden fall"
          :kind        "phrase"
          :modified-at "2026-02-02T00:00:00.000Z"
          :translation [{:lang "ru" :value "в любом случае"} {:lang "ru" :value "обязательно"}]
          :value       "auf jeden Fall"}
         (sut/edited-word {:id "vocab:auf jeden fall" :kind "phrase" :translation [] :value "auf jeden fall"}
                          "auf  jeden\nFall"
                          " в любом случае \n\nобязательно"
                          "2026-02-02T00:00:00.000Z"))
      "a phrase collapses its value to single spaces and keeps one translation a line"))


(deftest a-phrase-added-keeps-one-translation-a-line
  (is (= [{:lang "ru" :value "в любом случае"} {:lang "ru" :value "обязательно, точно"}]
         (:translation (sut/entered-entry :phrase
                                          "auf jeden Fall"
                                          "в любом случае\n обязательно, точно \n"
                                          nil
                                          "2026-02-02T00:00:00.000Z")))))


(deftest a-word-that-is-written-is-stamped-by-the-function-that-builds-it
  (testing "an entry made from what was typed is created and modified now"
    (are [kind value translation pos expected]
     (= expected
        (select-keys (sut/entered-entry kind value translation pos "2026-02-02T00:00:00.000Z")
                     [:created-at :modified-at :pos]))
     :word   "der Hund"       "пёс" nil
                                           {:created-at  "2026-02-02T00:00:00.000Z"
                                            :modified-at "2026-02-02T00:00:00.000Z"}

     :word   "der Hund"       "пёс" "noun"
                                           {:created-at  "2026-02-02T00:00:00.000Z"
                                            :modified-at "2026-02-02T00:00:00.000Z"
                                            :pos         "noun"}

     :phrase "auf jeden Fall" "в любом случае" nil
                                           {:created-at  "2026-02-02T00:00:00.000Z"
                                            :modified-at "2026-02-02T00:00:00.000Z"}))
  (testing "no translation makes no entry to stamp"
    (is (nil? (sut/entered-entry :word "der Hund" "  " nil "2026-02-02T00:00:00.000Z"))))
  (testing "a word merged or edited is modified now; its creation time stays, and is set when there was none"
    (are [stamped-word expected] (= expected (select-keys stamped-word [:created-at :modified-at]))
     (sut/merged-entry {:created-at "2026-01-01T00:00:00.000Z" :modified-at "2026-01-05T00:00:00.000Z" :translation []}
                       {:translation [{:lang "ru" :value "собака"}]}
                       "2026-02-02T00:00:00.000Z")
      {:created-at "2026-01-01T00:00:00.000Z" :modified-at "2026-02-02T00:00:00.000Z"}

     (sut/merged-entry {:translation []} {:translation []} "2026-02-02T00:00:00.000Z")
      {:created-at "2026-02-02T00:00:00.000Z" :modified-at "2026-02-02T00:00:00.000Z"}

     (sut/edited-word {:created-at "2026-01-01T00:00:00.000Z" :modified-at "2026-01-05T00:00:00.000Z" :translation []}
                      "der Hund"
                      "пёс"
                      "2026-02-02T00:00:00.000Z")
      {:created-at "2026-01-01T00:00:00.000Z" :modified-at "2026-02-02T00:00:00.000Z"}

     (sut/edited-word {:translation []} "der Hund" "пёс" "2026-02-02T00:00:00.000Z")
      {:created-at "2026-02-02T00:00:00.000Z" :modified-at "2026-02-02T00:00:00.000Z"})))

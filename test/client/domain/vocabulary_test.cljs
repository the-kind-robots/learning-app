(ns client.domain.vocabulary-test
  (:require
   [cljs.test :refer-macros [are deftest is testing]]
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


(deftest the-words-filter-finds-a-word-typed-with-or-without-its-umlauts
  (let [word {:value "für" :translation [{:lang "ru" :value "для"}]}
        text (sut/search-text word)]
    (are [typed] (sut/matches? text (sut/query typed))
      "für" "fuer" "fur" "для")
    (is (not (sut/matches? text (sut/query "fir"))))))


(deftest a-word-without-umlauts-has-one-search-line-per-text
  (is (= "hund\nпёс" (sut/search-text {:value "Hund" :translation [{:lang "ru" :value "пёс"}]}))))


(deftest conflicting-versions-merge-into-the-last-written-with-every-translation
  (let [older {:modified-at "2026-01-01" :translation [{:lang "ru" :value "пёс"}] :value "Hund"}
        newer {:modified-at "2026-02-01" :translation [{:lang "ru" :value "собака"}] :value "der Hund"}]
    (is (= {:modified-at "2026-02-01"
            :translation [{:lang "ru" :value "пёс"} {:lang "ru" :value "собака"}]
            :value       "der Hund"}
           (sut/merged-word [older newer]))
        "the newer fields win; the translations of both are kept")
    (is (= #{{:lang "ru" :value "пёс"} {:lang "ru" :value "собака"}}
           (set (:translation (sut/merged-word [newer older]))))
        "whatever the order")
    (is (= "b" (:value (sut/merged-word [(assoc older :value "a") (assoc older :value "b")])))
        "equal dates: the later version")))


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
          :translation [{:lang "ru" :value "в любом случае"}]
          :value       "auf jeden Fall"}
         (sut/edited-word {:id "vocab:auf jeden fall" :kind "phrase" :translation [] :value "auf jeden fall"}
                          "auf  jeden\nFall"
                          "в любом\nслучае"
                          "2026-02-02T00:00:00.000Z"))
      "a phrase collapses both texts to single spaces"))


(deftest a-word-that-is-written-is-stamped-by-the-function-that-builds-it
  (testing "an entry made from what was typed is created and modified now"
    (are [kind value translation pos expected]
         (= expected (select-keys (sut/entered-entry kind value translation pos "2026-02-02T00:00:00.000Z")
                                  [:created-at :modified-at :pos]))
      :word "der Hund" "пёс" nil
      {:created-at "2026-02-02T00:00:00.000Z" :modified-at "2026-02-02T00:00:00.000Z"}

      :word "der Hund" "пёс" "noun"
      {:created-at "2026-02-02T00:00:00.000Z" :modified-at "2026-02-02T00:00:00.000Z" :pos "noun"}

      :phrase "auf jeden Fall" "в любом случае" nil
      {:created-at "2026-02-02T00:00:00.000Z" :modified-at "2026-02-02T00:00:00.000Z"}))
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
                       "der Hund" "пёс" "2026-02-02T00:00:00.000Z")
      {:created-at "2026-01-01T00:00:00.000Z" :modified-at "2026-02-02T00:00:00.000Z"}

      (sut/edited-word {:translation []} "der Hund" "пёс" "2026-02-02T00:00:00.000Z")
      {:created-at "2026-02-02T00:00:00.000Z" :modified-at "2026-02-02T00:00:00.000Z"})))

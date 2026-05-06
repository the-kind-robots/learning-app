(ns dictionary.kaikki-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [dictionary.kaikki :as kaikki]))


;; ---------------------------------------------------------------------------
;; lemma-entry?
;; ---------------------------------------------------------------------------


(deftest lemma-entry?-mixed-senses
  (testing "entry with mix of form_of and real senses is a lemma"
    (is (true? (kaikki/lemma-entry?
                {:senses [{:form_of [{:word "foo"}]}
                          {:tags ["animal"]}]})))))


(deftest lemma-entry?-all-form-of
  (testing "entry where ALL senses have form_of is NOT a lemma"
    (is (not (kaikki/lemma-entry?
              {:senses [{:form_of [{:word "a"}]}
                        {:form_of [{:word "b"}]}]})))))


(deftest lemma-entry?-empty-senses
  (testing "entry with empty senses returns nil (falsy)"
    (is (not (kaikki/lemma-entry? {:senses []})))))


(deftest lemma-entry?-no-senses-key
  (testing "entry without :senses key returns nil (falsy)"
    (is (not (kaikki/lemma-entry? {:word "test"})))))


(deftest lemma-entry?-all-real-senses
  (testing "entry with all real senses (no form_of) is a lemma"
    (is (true? (kaikki/lemma-entry?
                {:senses [{:tags ["noun"]}
                          {:tags ["informal"]}]})))))


;; ---------------------------------------------------------------------------
;; german-entry?
;; ---------------------------------------------------------------------------


(deftest german-entry?-basic-word
  (testing "basic German word passes"
    (is (kaikki/german-entry? {:lang_code "de" :word "Hund"}))))


(deftest german-entry?-non-de
  (testing "non-German lang_code fails"
    (is (not (kaikki/german-entry? {:lang_code "en" :word "dog"})))))


(deftest german-entry?-umlauts-and-eszett
  (testing "umlauts and ß pass"
    (is (kaikki/german-entry? {:lang_code "de" :word "Größe"}))))


(deftest german-entry?-loanword-accents
  (testing "loanword accents (é, ç, ñ) pass"
    (is (kaikki/german-entry? {:lang_code "de" :word "Café"}))))


(deftest german-entry?-multi-word
  (testing "multi-word with spaces pass"
    (is (kaikki/german-entry? {:lang_code "de" :word "guten Tag"}))))


(deftest german-entry?-hyphens-and-apostrophes
  (testing "hyphens and apostrophes pass"
    (is (kaikki/german-entry? {:lang_code "de" :word "Mutter-Kind-Kur"}))))


(deftest german-entry?-non-latin
  (testing "non-Latin characters fail"
    (is (not (kaikki/german-entry? {:lang_code "de" :word "собака"})))))


(deftest german-entry?-missing-word
  (testing "missing word (empty default) fails gracefully"
    (is (not (kaikki/german-entry? {:lang_code "de"})))))


;; ---------------------------------------------------------------------------
;; extract-gender
;; ---------------------------------------------------------------------------


(deftest extract-gender-masculine-from-tags
  (testing "masculine from entry-level tags"
    (is (= "der" (kaikki/extract-article {:tags ["noun" "masculine"]})))))


(deftest extract-gender-feminine-from-tags
  (testing "feminine from entry-level tags"
    (is (= "die" (kaikki/extract-article {:tags ["feminine"]})))))


(deftest extract-gender-neuter-from-tags
  (testing "neuter from entry-level tags"
    (is (= "das" (kaikki/extract-article {:tags ["neuter"]})))))


(deftest extract-gender-fallback-to-form-article
  (testing "falls back to first form's article field"
    (is (= "die"
           (kaikki/extract-article
            {:tags  []
             :forms [{:article "die" :form "Katzen"}]})))))


(deftest extract-gender-tag-priority-over-article
  (testing "tag gender takes priority over form article"
    (is (= "der"
           (kaikki/extract-article
            {:tags  ["masculine"]
             :forms [{:article "die" :form "Hunde"}]})))))


(deftest extract-gender-nil-cases
  (testing "returns nil when no gender info available"
    (is (nil? (kaikki/extract-article {:tags [] :forms []})))))


(deftest extract-gender-nil-unknown-article
  (testing "returns nil for unknown article value"
    (is (nil? (kaikki/extract-article
               {:tags  []
                :forms [{:article "les" :form "foo"}]})))))


;; ---------------------------------------------------------------------------
;; russian-translations
;; ---------------------------------------------------------------------------


(deftest russian-translations-mixed-languages
  (testing "filters only Russian translations"
    (is (= [{:lang "ru" :value "собака"}]
           (kaikki/russian-translations
            {:translations [{:lang_code "ru" :word "собака"}
                            {:lang_code "en" :word "dog"}]})))))


(deftest russian-translations-dedup
  (testing "deduplicates identical translations"
    (is (= [{:lang "ru" :value "пёс"}]
           (kaikki/russian-translations
            {:translations [{:lang_code "ru" :word "пёс"}
                            {:lang_code "ru" :word "пёс"}]})))))


(deftest russian-translations-nil-word
  (testing "skips translations with nil word"
    (is (= [{:lang "ru" :value "кот"}]
           (kaikki/russian-translations
            {:translations [{:lang_code "ru" :word nil}
                            {:lang_code "ru" :word "кот"}]})))))


(deftest russian-translations-no-russian
  (testing "returns empty vec when no Russian translations"
    (is (= []
           (kaikki/russian-translations
            {:translations [{:lang_code "en" :word "cat"}]})))))


(deftest russian-translations-missing-key
  (testing "returns empty vec when no :translations key"
    (is (= [] (kaikki/russian-translations {:word "Hund"})))))


;; ---------------------------------------------------------------------------
;; inflected-forms
;; ---------------------------------------------------------------------------


(deftest inflected-forms-valid-forms
  (testing "extracts valid inflected forms"
    (is (= ["Hundes" "Hunde" "Hunden"]
           (kaikki/inflected-forms
            {:word  "Hund"
             :forms [{:form "Hundes"} {:form "Hunde"} {:form "Hunden"}]})))))


(deftest inflected-forms-too-short
  (testing "filters out single-character forms"
    (is (= ["ab"]
           (kaikki/inflected-forms
            {:word  "Test"
             :forms [{:form "a"} {:form "ab"}]})))))


(deftest inflected-forms-auxiliaries
  (testing "filters out auxiliary verb markers"
    (is (= ["ging" "gegangen"]
           (kaikki/inflected-forms
            {:word  "gehen"
             :forms [{:form "haben"} {:form "sein"} {:form "ging"} {:form "gegangen"}]})))))


(deftest inflected-forms-lemma-excluded
  (testing "excludes the lemma itself from forms"
    (is (= ["Hunde"]
           (kaikki/inflected-forms
            {:word  "Hund"
             :forms [{:form "Hund"} {:form "Hunde"}]})))))


(deftest inflected-forms-non-german-chars
  (testing "excludes forms with non-German characters"
    (is (= ["Hunde"]
           (kaikki/inflected-forms
            {:word  "Hund"
             :forms [{:form "Hunde"} {:form "собаки"}]})))))


(deftest inflected-forms-dedup
  (testing "deduplicates identical forms"
    (is (= ["Hunde"]
           (kaikki/inflected-forms
            {:word  "Hund"
             :forms [{:form "Hunde"} {:form "Hunde"}]})))))


(deftest inflected-forms-umlauts
  (testing "forms with umlauts pass through"
    (is (= ["Häuser"]
           (kaikki/inflected-forms
            {:word  "Haus"
             :forms [{:form "Häuser"}]})))))

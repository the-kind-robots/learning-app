(ns dictionary.transform-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [dictionary.transform :as transform]))


;; Access private fns via var
(def ^:private compute-rank #'transform/compute-rank)


(def ^:private entry-value #'transform/entry-value)


(def ^:private bare-word #'transform/bare-word)


;; ---------------------------------------------------------------------------
;; compute-rank (private)
;; ---------------------------------------------------------------------------


(deftest compute-rank-a1-base-plus-bonus
  (testing "A1 base rank 30000 + sense/translation bonus"
    (is (= 30032 (compute-rank "a1" 3 2)))))


(deftest compute-rank-a2-base-plus-bonus
  (testing "A2 base rank 20000 + bonus"
    (is (= 20021 (compute-rank "a2" 2 1)))))


(deftest compute-rank-b1-base-plus-bonus
  (testing "B1 base rank 10000 + bonus"
    (is (= 10011 (compute-rank "b1" 1 1)))))


(deftest compute-rank-non-cefr-cap-at-5000
  (testing "non-CEFR entry capped at 5000"
    (is (= 5000 (compute-rank nil 100 100)))))


(deftest compute-rank-boundary-values
  (testing "zero counts give base rank only"
    (is (= 30000 (compute-rank "a1" 0 0)))))


(deftest compute-rank-zero-counts-non-cefr
  (testing "non-CEFR with zero counts gives 0"
    (is (= 0 (compute-rank nil 0 0)))))


(deftest compute-rank-b1-min-gt-non-cefr-max
  (testing "B1 minimum (10000) > non-CEFR maximum (5000)"
    (let [b1-min  (compute-rank "b1" 0 0)
          non-max (compute-rank nil 100 100)]
      (is (> b1-min non-max)))))


(deftest compute-rank-frequency-dominates-fallbacks
  (testing "frequency rank is the primary importance signal when present"
    (is (> (compute-rank nil 0 0 {:rank 10})
           (compute-rank "a1" 100 100)))))


;; ---------------------------------------------------------------------------
;; entry-value (private)
;; ---------------------------------------------------------------------------


(deftest entry-value-noun-with-gender
  (testing "noun with gender prepends article"
    (is (= "der Hund" (entry-value "Hund" "noun" "der")))))


(deftest entry-value-noun-no-gender
  (testing "noun without gender returns bare word"
    (is (= "Tier" (entry-value "Tier" "noun" nil)))))


(deftest entry-value-verb
  (testing "verb ignores gender, returns bare word"
    (is (= "gehen" (entry-value "gehen" "verb" nil)))))


(deftest entry-value-verb-with-gender-ignored
  (testing "verb with gender still returns bare word (gender ignored for non-nouns)"
    (is (= "laufen" (entry-value "laufen" "verb" "der")))))


;; ---------------------------------------------------------------------------
;; bare-word (private)
;; ---------------------------------------------------------------------------


(deftest bare-word-der-stripping
  (testing "strips 'der' article"
    (is (= "Hund" (bare-word "der Hund")))))


(deftest bare-word-die-stripping
  (testing "strips 'die' article"
    (is (= "Katze" (bare-word "die Katze")))))


(deftest bare-word-das-stripping
  (testing "strips 'das' article"
    (is (= "Buch" (bare-word "das Buch")))))


(deftest bare-word-no-article
  (testing "no article returns word as-is"
    (is (= "gehen" (bare-word "gehen")))))


(deftest bare-word-partial-match-like-derzeit
  (testing "'derzeit' is not stripped (no space after 'der')"
    (is (= "derzeit" (bare-word "derzeit")))))


;; ---------------------------------------------------------------------------
;; dictionary-entry
;; ---------------------------------------------------------------------------


(def ^:private sample-goethe-index
  [["hundefutter" "a1"] ["hund" "a1"] ["geh" "a2"] ["katz" "b1"]])


(def ^:private sample-timestamp "2026-01-30T12:00:00Z")


(deftest dictionary-entry-full-noun
  (testing "full noun entry with gender, translations, forms"
    (let [kentry {:word         "Hund"
                  :pos          "Noun"
                  :lang_code    "de"
                  :tags         ["masculine" "noun"]
                  :senses       [{:tags ["animal"] :glosses ["a dog"]}
                                  {:tags ["insult"] :glosses ["an unpleasant person"]}]
                  :translations [{:lang_code "ru" :word "собака"}
                                 {:lang_code "en" :word "dog"}]
                  :forms        [{:form "Hundes"} {:form "Hunde"} {:form "Hunden"}]}
          result (transform/dictionary-entry kentry sample-goethe-index sample-timestamp)]
      (is (some? result))
      (is (= "der Hund" (:value result)))
      (is (= "noun" (:pos result)))
      (is (= "lemma:der hund:noun" (:_id result)))
      (is (= [{:lang "ru" :value "собака"}] (:translation result)))
      (is (= ["Hundes" "Hunde" "Hunden"] (:forms result)))
      (is (= "a1" (get-in result [:meta :cefr-level])))
      (is (= "der" (get-in result [:meta :gender])))
      (is (= [{:tags ["animal"] :glosses ["a dog"]}
              {:tags ["insult"] :glosses ["an unpleasant person"]}]
             (get-in result [:meta :senses])))
      (is (pos? (:rank result))))))


(deftest dictionary-entry-stores-frequency-metadata
  (testing "frequency data is copied to meta and drives rank"
    (let [kentry {:word         "Hund"
                  :pos          "Noun"
                  :lang_code    "de"
                  :tags         ["masculine"]
                  :senses       [{:tags ["animal"]}]
                  :translations [{:lang_code "ru" :word "собака"}]
                  :forms        []}
          freq   {"hund" {:source "subtitles" :rank 7 :count 12345.0}}
          result (transform/dictionary-entry kentry sample-goethe-index sample-timestamp freq)]
      (is (= {:source "subtitles" :rank 7 :count 12345.0 :matched "Hund"}
             (get-in result [:meta :frequency])))
      (is (= 999993 (:rank result))))))


(deftest dictionary-entry-aggregates-frequency-from-forms
  (testing "lemma rank uses the best matching surface form and sums matched counts"
    (let [kentry {:word         "gehen"
                  :pos          "Verb"
                  :lang_code    "de"
                  :tags         []
                  :senses       [{:tags ["motion"]}]
                  :translations []
                  :forms        [{:form "ging"} {:form "gegangen"}]}
          freq   {"gehen"    {:source "subtlex-de" :rank 500 :count 100.0}
                  "ging"     {:source "subtlex-de" :rank 20 :count 1000.0}
                  "gegangen" {:source "subtlex-de" :rank 200 :count 300.0}}
          result (transform/dictionary-entry kentry sample-goethe-index sample-timestamp freq)]
      (is (= {:source "subtlex-de" :rank 20 :count 1400.0 :matched "ging"}
             (get-in result [:meta :frequency])))
      (is (= 999980 (:rank result))))))


(deftest dictionary-entry-aggregates-noun-frequency-from-forms
  (testing "noun frequency matches bare lemma and inflected forms, not only article value"
    (let [kentry {:word         "Hund"
                  :pos          "Noun"
                  :lang_code    "de"
                  :tags         ["masculine"]
                  :senses       [{:tags ["animal"]}]
                  :translations []
                  :forms        [{:form "Hunde"} {:form "Hunden"}]}
          freq   {"hund"  {:source "subtlex-de" :rank 100 :count 100.0}
                  "hunde" {:source "subtlex-de" :rank 40 :count 300.0}}
          result (transform/dictionary-entry kentry sample-goethe-index sample-timestamp freq)]
      (is (= {:source "subtlex-de" :rank 40 :count 400.0 :matched "Hunde"}
             (get-in result [:meta :frequency])))
      (is (= 999960 (:rank result))))))


(deftest dictionary-entry-verb
  (testing "verb entry without gender"
    (let [kentry {:word         "gehen"
                  :pos          "Verb"
                  :lang_code    "de"
                  :tags         []
                  :senses       [{:tags ["motion"]}]
                  :translations [{:lang_code "ru" :word "идти"}]
                  :forms        [{:form "ging"} {:form "gegangen"}]}
          result (transform/dictionary-entry kentry sample-goethe-index sample-timestamp)]
      (is (some? result))
      (is (= "gehen" (:value result)))
      (is (= "verb" (:pos result)))
      (is (nil? (get-in result [:meta :gender]))))))


(deftest dictionary-entry-empty-translation-when-no-russian
  (testing "returns entry with empty translation when no Russian translations"
    (let [kentry {:word         "Hund"
                  :pos          "Noun"
                  :lang_code    "de"
                  :tags         ["masculine"]
                  :senses       [{:tags ["animal"]}]
                  :translations [{:lang_code "en" :word "dog"}]
                  :forms        []}
          result (transform/dictionary-entry kentry sample-goethe-index sample-timestamp)]
      (is (some? result))
      (is (= [] (:translation result))))))


(deftest dictionary-entry-nil-for-name-without-cefr
  (testing "returns nil for name POS without CEFR level"
    (let [kentry {:word         "Berlin"
                  :pos          "Name"
                  :lang_code    "de"
                  :tags         []
                  :senses       [{:tags []}]
                  :translations [{:lang_code "ru" :word "Берлин"}]
                  :forms        []}
          ;; empty index = no CEFR
          result (transform/dictionary-entry kentry [] sample-timestamp)]
      (is (nil? result)))))


(deftest dictionary-entry-name-always-excluded
  (testing "name POS is always excluded regardless of CEFR level"
    (let [kentry {:word         "Berlin"
                  :pos          "Name"
                  :lang_code    "de"
                  :tags         []
                  :senses       [{:tags []}]
                  :translations [{:lang_code "ru" :word "Берлин"}]
                  :forms        []}
          index  [["berlin" "a1"]]
          result (transform/dictionary-entry kentry index sample-timestamp)]
      (is (nil? result)))))


(deftest dictionary-entry-id-format
  (testing "_id keeps raw lemma identity while lookup metadata stays normalized"
    (let [kentry {:word         "Größe"
                  :pos          "Noun"
                  :lang_code    "de"
                  :tags         ["feminine"]
                  :senses       [{:tags []}]
                  :translations [{:lang_code "ru" :word "размер"}]
                  :forms        []}
          result (transform/dictionary-entry kentry [] sample-timestamp)]
      (is (= "lemma:die größe:noun" (:_id result)))
      (is (= "die groesse" (get-in result [:meta :normalized-value]))))))


(deftest dictionary-entry-pos-lowercasing
  (testing "POS is lowercased"
    (let [kentry {:word         "gehen"
                  :pos          "Verb"
                  :lang_code    "de"
                  :tags         []
                  :senses       [{:tags []}]
                  :translations [{:lang_code "ru" :word "идти"}]
                  :forms        []}
          result (transform/dictionary-entry kentry [] sample-timestamp)]
      (is (= "verb" (:pos result))))))


(deftest dictionary-entry-nil-pos
  (testing "nil POS is excluded (unknown not in allowed-pos)"
    (let [kentry {:word         "hmm"
                  :pos          nil
                  :lang_code    "de"
                  :tags         []
                  :senses       [{:tags []}]
                  :translations [{:lang_code "ru" :word "хмм"}]
                  :forms        []}
          result (transform/dictionary-entry kentry [] sample-timestamp)]
      (is (nil? result)))))


;; ---------------------------------------------------------------------------
;; accumulate-surface-forms
;; ---------------------------------------------------------------------------


(deftest accumulate-surface-forms-value-bare-and-forms-indexed
  (testing "value, bare word, and inflected forms are all indexed"
    (let [entry {:_id   "lemma:der hund:noun"
                 :value "der Hund"
                 :rank  30000
                 :forms ["Hundes" "Hunde"]}
          idx   (transform/accumulate-surface-forms {} entry)]
      ;; "der Hund" normalized, "Hund" normalized, "Hundes" normalized, "Hunde" normalized
      (is (contains? idx "der hund"))
      (is (contains? idx "hund"))
      (is (contains? idx "hundes"))
      (is (contains? idx "hunde")))))


(deftest accumulate-surface-forms-verb-value-equals-bare
  (testing "verb where value == bare word doesn't create duplicates in index entries"
    (let [entry {:_id   "lemma:gehen:verb"
                 :value "gehen"
                 :rank  20000
                 :forms ["ging" "gegangen"]}
          idx   (transform/accumulate-surface-forms {} entry)]
      ;; "gehen" should appear once in the index (value and bare are same)
      (is (= 1 (count (get idx "gehen")))))))


(deftest accumulate-surface-forms-accumulates-across-entries
  (testing "accumulates entries for the same normalized form"
    (let [entry1 {:_id   "lemma:der hund:noun"
                  :value "der Hund"
                  :rank  30000
                  :forms ["Hunde"]}
          entry2 {:_id   "lemma:hunde:noun"
                  :value "Hunde"
                  :rank  5000
                  :forms []}
          idx    (-> {}
                     (transform/accumulate-surface-forms entry1)
                     (transform/accumulate-surface-forms entry2))]
      (is (= 2 (count (get idx "hunde")))))))


;; ---------------------------------------------------------------------------
;; surface-form-documents
;; ---------------------------------------------------------------------------


(deftest surface-form-documents-sorted-by-key
  (testing "documents are sorted alphabetically by normalized form"
    (let [idx  {"zebra" [{:lemma-id "z" :lemma "Zebra" :rank 100}]
                "apfel" [{:lemma-id "a" :lemma "Apfel" :rank 200}]}
          docs (transform/surface-form-documents idx)]
      (is (= "sf:apfel" (:_id (first docs))))
      (is (= "sf:zebra" (:_id (second docs)))))))


(deftest surface-form-documents-entries-sorted-by-rank-desc
  (testing "entries within a doc are sorted by rank descending"
    (let [idx  {"hund" [{:lemma-id "a" :lemma "A" :rank 100}
                        {:lemma-id "b" :lemma "B" :rank 5000}]}
          docs (transform/surface-form-documents idx)
          doc  (first docs)]
      (is (= 5000 (:rank (first (:entries doc)))))
      (is (= 100 (:rank (second (:entries doc))))))))


(deftest surface-form-documents-sf-prefix
  (testing "_id has sf: prefix"
    (let [idx  {"test" [{:lemma-id "t" :lemma "Test" :rank 100}]}
          docs (transform/surface-form-documents idx)]
      (is (= "sf:test" (:_id (first docs)))))))


(deftest surface-form-documents-dedup
  (testing "duplicate entries for same form are deduplicated"
    (let [entry {:lemma-id "a" :lemma "A" :rank 100}
          idx   {"test" [entry entry]}
          docs  (transform/surface-form-documents idx)
          doc   (first docs)]
      (is (= 1 (count (:entries doc)))))))

(ns dictionary.core-test
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [deftest is testing]]
   [dictionary.core :as core]))


;; ---------------------------------------------------------------------------
;; slim-entry
;; ---------------------------------------------------------------------------


(deftest slim-entry-retains-essential-fields
  (testing "retains word, pos, tags, senses, translations, forms"
    (let [entry  {:word         "Hund"
                  :pos          "Noun"
                  :lang_code    "de"
                  :tags         ["masculine" "noun"]
                  :senses       [{:tags ["animal"] :glosses ["a dog"]}
                                 {:tags ["insult"] :form_of [{:word "hound"}]}]
                  :translations [{:lang_code "ru" :word "собака"}
                                 {:lang_code "en" :word "dog"}]
                  :forms        [{:form "Hundes" :article "des" :extra "ignore"}
                                 {:form "Hunde"}
                                 {:article "der"}]} ;; no :form → filtered out
          result (core/slim-entry entry)]
      (is (= "Hund" (:word result)))
      (is (= "Noun" (:pos result)))
      (is (= ["masculine" "noun"] (:tags result))))))


(deftest slim-entry-filters-ru-translations-only
  (testing "only retains Russian translations"
    (let [entry  {:word         "Hund"
                  :pos          "Noun"
                  :lang_code    "de"
                  :tags         []
                  :senses       []
                  :translations [{:lang_code "ru" :word "собака"}
                                 {:lang_code "en" :word "dog"}
                                 {:lang_code "ru" :word "пёс"}]
                  :forms        []}
          result (core/slim-entry entry)]
      (is (= 2 (count (:translations result))))
      (is (every? #(= "ru" (:lang_code %)) (:translations result))))))


(deftest slim-entry-filters-forms-with-form-key
  (testing "only retains forms that have a :form key"
    (let [entry  {:word         "Hund"
                  :pos          "Noun"
                  :lang_code    "de"
                  :tags         []
                  :senses       []
                  :translations []
                  :forms        [{:form "Hundes" :article "des"}
                                 {:article "der"} ;; no :form
                                 {:form "Hunde"}]}
          result (core/slim-entry entry)]
      (is (= 2 (count (:forms result))))
      (is (every? :form (:forms result))))))


(deftest slim-entry-senses-retain-tags-glosses-and-form-of
  (testing "senses retain only machine tags, glosses, and form_of when present"
    (let [entry  {:word         "Test"
                  :pos          "Noun"
                  :lang_code    "de"
                  :tags         []
                  :senses       [{:tags ["sense1"] :glosses ["meaning"] :form_of [{:word "x"}]}
                                 {:tags ["sense2"] :glosses ["meaning2"]}]
                  :translations []
                  :forms        []}
          result (core/slim-entry entry)]
      ;; first sense has tags + glosses + form_of
      (is (= {:tags ["sense1"] :glosses ["meaning"] :form_of [{:word "x"}]}
             (first (:senses result))))
      ;; second sense has tags + glosses only (no form_of key)
      (is (= {:tags ["sense2"] :glosses ["meaning2"]}
             (second (:senses result)))))))


;; ---------------------------------------------------------------------------
;; merge-kaikki-entries
;; ---------------------------------------------------------------------------


(deftest merge-kaikki-entries-unions-translations
  (testing "unions translations from two entries"
    (let [e1     {:word         "Hund"
                  :pos          "Noun"
                  :translations [{:lang_code "ru" :word "собака"}]
                  :forms        [{:form "Hundes"}]
                  :senses       [{:tags ["animal"]}]}
          e2     {:word         "Hund"
                  :pos          "Noun"
                  :translations [{:lang_code "ru" :word "пёс"}]
                  :forms        [{:form "Hunden"}]
                  :senses       [{:tags ["insult"]}]}
          result (core/merge-kaikki-entries e1 e2)]
      (is (= 2 (count (:translations result))))
      (is (= 2 (count (:forms result))))
      (is (= 2 (count (:senses result)))))))


(deftest merge-kaikki-entries-deduplicates-overlapping
  (testing "deduplicates overlapping translations and forms"
    (let [e1     {:word         "Hund"
                  :pos          "Noun"
                  :translations [{:lang_code "ru" :word "собака"}]
                  :forms        [{:form "Hunde"}]
                  :senses       [{:tags ["animal"]}]}
          e2     {:word         "Hund"
                  :pos          "Noun"
                  :translations [{:lang_code "ru" :word "собака"}]
                  :forms        [{:form "Hunde"}]
                  :senses       [{:tags ["animal"]}]}
          result (core/merge-kaikki-entries e1 e2)]
      (is (= 1 (count (:translations result))))
      (is (= 1 (count (:forms result))))
      (is (= 1 (count (:senses result)))))))


(deftest merge-kaikki-entries-preserves-word-and-pos
  (testing "preserves the existing entry's word and pos"
    (let [e1     {:word         "Hund"
                  :pos          "Noun"
                  :translations []
                  :forms        []
                  :senses       []}
          e2     {:word         "Hund"
                  :pos          "Noun"
                  :translations []
                  :forms        []
                  :senses       []}
          result (core/merge-kaikki-entries e1 e2)]
      (is (= "Hund" (:word result)))
      (is (= "Noun" (:pos result))))))


;; ---------------------------------------------------------------------------
;; merge-entries-from-lines
;; ---------------------------------------------------------------------------


(deftest merge-entries-from-lines-filters-merges-and-handles-errors
  (testing "filters non-German, merges duplicates, counts parse errors"
    (let [;; Two valid German Hund entries (will merge)
          hund-1    (json/generate-string
                     {:word         "Hund"
                      :pos          "Noun"
                      :lang_code    "de"
                      :tags         ["masculine"]
                      :senses       [{:tags ["animal"]}]
                      :translations [{:lang_code "ru" :word "собака"}]
                      :forms        [{:form "Hunde"}]})
          hund-2    (json/generate-string
                     {:word         "Hund"
                      :pos          "Noun"
                      :lang_code    "de"
                      :tags         ["masculine"]
                      :senses       [{:tags ["insult"]}]
                      :translations [{:lang_code "ru" :word "пёс"}]
                      :forms        [{:form "Hunden"}]})
          ;; English entry (should be skipped)
          english   (json/generate-string
                     {:word         "dog"
                      :pos          "Noun"
                      :lang_code    "en"
                      :tags         []
                      :senses       [{:tags []}]
                      :translations []
                      :forms        []})
          ;; German verb (valid, separate entry)
          verb      (json/generate-string
                     {:word         "gehen"
                      :pos          "Verb"
                      :lang_code    "de"
                      :tags         []
                      :senses       [{:tags ["motion"]}]
                      :translations [{:lang_code "ru" :word "идти"}]
                      :forms        [{:form "ging"}]})
          ;; Malformed JSON
          malformed "{\"lang_code\": \"de\", broken json"
          lines     [hund-1 hund-2 english verb malformed]
          result    (core/merge-entries-from-lines lines)]
      ;; 5 total lines processed
      (is (= 5 (:total-lines result)))
      ;; 1 parse error
      (is (= 1 (:parse-errors result)))
      ;; 2 unique entries: Hund+Noun merged, gehen+Verb separate
      (is (= 2 (count (:entries result))))
      ;; Hund entry has merged translations
      (let [hund (get (:entries result) ["hund" "noun"])]
        (is (some? hund))
        (is (= 2 (count (:translations hund))))
        (is (= 2 (count (:forms hund))))))))


;; ---------------------------------------------------------------------------
;; transform-entries
;; ---------------------------------------------------------------------------


(deftest transform-entries-transforms-and-skips-correctly
  (testing "produces docs, skips entries without Russian translations, counts CEFR"
    (let [goethe-index [["hund" "a1"] ["geh" "a2"]]
          timestamp    "2026-01-30T12:00:00Z"
          ;; Noun with Russian translation (should produce doc)
          hund         {:word         "Hund"
                        :pos          "Noun"
                        :tags         ["masculine"]
                        :senses       [{:tags ["animal"]}]
                        :translations [{:lang_code "ru" :word "собака"}]
                        :forms        [{:form "Hunde"}]}
          ;; Verb with Russian translation (should produce doc)
          gehen        {:word         "gehen"
                        :pos          "Verb"
                        :tags         []
                        :senses       [{:tags ["motion"]}]
                        :translations [{:lang_code "ru" :word "идти"}]
                        :forms        [{:form "ging"}]}
          ;; Entry without Russian translation (should be skipped)
          katze        {:word         "Katze"
                        :pos          "Noun"
                        :tags         ["feminine"]
                        :senses       [{:tags ["animal"]}]
                        :translations [{:lang_code "en" :word "cat"}]
                        :forms        []}
          merged       {["Hund" "noun"]  hund
                        ["gehen" "verb"] gehen
                        ["Katze" "noun"] katze}
          result       (core/transform-entries merged goethe-index timestamp)]
      ;; 3 docs produced (Hund + gehen + Katze with empty translation)
      (is (= 3 (:entry-count result)))
      (is (= 3 (count (:docs result))))
      ;; 0 skipped
      (is (= 0 (:skip-count result)))
      ;; sf-index populated
      (is (pos? (count (:sf-index result))))
      ;; CEFR counts: a1=1 (Hund), a2=1 (gehen)
      (is (= 1 (get (:cefr-counts result) "a1")))
      (is (= 1 (get (:cefr-counts result) "a2"))))))


(deftest transform-entries-sorts-by-rank-desc
  (testing "dictionary-entries output order follows rank so enrichment starts with important words"
    (let [timestamp "2026-01-30T12:00:00Z"
          low       {:word         "Zebra"
                     :pos          "Noun"
                     :tags         []
                     :senses       [{:tags []}]
                     :translations []
                     :forms        []}
          high      {:word         "machen"
                     :pos          "Verb"
                     :tags         []
                     :senses       [{:tags []}]
                     :translations []
                     :forms        []}
          merged    {["Zebra" "noun"] low
                     ["machen" "verb"] high}
          freq      {"machen" {:source "subtitles" :rank 1 :count 100000.0}}
          result    (core/transform-entries merged [] timestamp freq)]
      (is (= ["machen" "Zebra"] (mapv :value (:docs result)))))))


(deftest transform-entries-keeps-raw-id-collisions-separate
  (testing "spelling variants with the same normalized lookup key keep separate IDs"
    (let [timestamp "2026-01-30T12:00:00Z"
          ablass    {:word         "Ablass"
                     :pos          "Noun"
                     :tags         ["masculine"]
                     :senses       [{:glosses ["technical sense"]}]
                     :translations [{:lang_code "ru" :word "сброс"}]
                     :forms        [{:form "Ablasse"}]}
          ablass-ss {:word         "Ablaß"
                     :pos          "Noun"
                     :tags         ["masculine"]
                     :senses       [{:glosses ["religious sense"]}]
                     :translations [{:lang_code "ru" :word "индульгенция"}]
                     :forms        [{:form "Ablässe"}]}
          merged    {["ablass" "noun"] ablass
                     ["ablaß" "noun"] ablass-ss}
          result    (core/transform-entries merged [] timestamp)
          docs      (:docs result)
          ids       (set (map :_id docs))]
      (is (= 2 (:entry-count result)))
      (is (= #{"lemma:der ablass:noun"
               "lemma:der ablaß:noun"} ids))
      (is (= 2 (count (get (:sf-index result) "der ablass")))))))

(ns dictionary.pipeline-test
  (:require
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [dictionary.core :as core]
   [dictionary.emit :as emit]
   [dictionary.goethe :as goethe]
   [dictionary.transform :as transform]))


(defn- write-temp-csv!
  "Write CSV rows to a temp file. Returns path string."
  [rows]
  (let [f (java.io.File/createTempFile "goethe-pipeline" ".csv")]
    (.deleteOnExit f)
    (with-open [w (io/writer f)]
      (doseq [row rows]
        (.write w (str/join "," row))
        (.write w "\n")))
    (.getAbsolutePath f)))


(deftest full-pure-pipeline-integration
  (testing "merge → transform → emit → verify manifest with synthetic data"
    (let [;; Synthetic Kaikki lines
          hund-1       (json/generate-string
                        {:word         "Hund"
                         :pos          "Noun"
                         :lang_code    "de"
                         :tags         ["masculine"]
                         :senses       [{:tags ["animal"]}]
                         :translations [{:lang_code "ru" :word "собака"}
                                        {:lang_code "en" :word "dog"}]
                         :forms        [{:form "Hunde"} {:form "Hundes"}]})
          hund-2       (json/generate-string
                        {:word         "Hund"
                         :pos          "Noun"
                         :lang_code    "de"
                         :tags         ["masculine"]
                         :senses       [{:tags ["insult"]}]
                         :translations [{:lang_code "ru" :word "пёс"}]
                         :forms        [{:form "Hunden"}]})
          verb         (json/generate-string
                        {:word         "gehen"
                         :pos          "Verb"
                         :lang_code    "de"
                         :tags         []
                         :senses       [{:tags ["motion"]}]
                         :translations [{:lang_code "ru" :word "идти"}]
                         :forms        [{:form "ging"} {:form "gegangen"}]})
          no-ru        (json/generate-string
                        {:word         "Katze"
                         :pos          "Noun"
                         :lang_code    "de"
                         :tags         ["feminine"]
                         :senses       [{:tags ["animal"]}]
                         :translations [{:lang_code "en" :word "cat"}]
                         :forms        []})
          english      (json/generate-string
                        {:word         "dog"
                         :pos          "Noun"
                         :lang_code    "en"
                         :tags         []
                         :senses       [{:tags []}]
                         :translations []
                         :forms        []})
          malformed    "{\"lang_code\": \"de\", broken"
          lines        [hund-1 hund-2 verb no-ru english malformed]

          ;; Goethe CSV
          goethe-path  (write-temp-csv! [["stem" "level"]
                                         ["hund" "a1"]
                                         ["geh" "a2"]
                                         ["katz" "b1"]])
          goethe-index (goethe/stem-level-index goethe-path)
          timestamp    "2026-01-30T12:00:00Z"

          ;; Step 1: merge
          merge-result (core/merge-entries-from-lines lines)]

      ;; Verify merge
      (is (= 6 (:total-lines merge-result)))
      (is (= 1 (:parse-errors merge-result)))
      ;; 3 entries: Hund (merged), gehen, Katze — English filtered at regex level
      (is (= 3 (count (:entries merge-result))))

      ;; Step 2: transform
      (let [transform-result (core/transform-entries (:entries merge-result) goethe-index timestamp)]
        ;; Hund + gehen + Katze = 3 docs (Katze has empty translation, will be enriched later)
        (is (= 3 (:entry-count transform-result)))
        (is (= 3 (count (:docs transform-result))))
        (is (= 0 (:skip-count transform-result)))

        ;; Step 3: emit to temp dir
        (let [tmp-dir      (str (System/getProperty "java.io.tmpdir")
                                "/pipeline-test-"
                                (System/currentTimeMillis))
              _ (.mkdirs (io/file tmp-dir))
              entries-path (str tmp-dir "/dictionary-entries.jsonl")
              entry-stats  (emit/write-jsonl! entries-path (:docs transform-result))
              sf-docs      (transform/surface-form-documents (:sf-index transform-result))
              sf-path      (str tmp-dir "/surface-forms.jsonl")
              sf-stats     (emit/write-jsonl! sf-path sf-docs)
              manifest     (emit/write-manifest! tmp-dir
                                                 {"dictionary-entries.jsonl" entry-stats
                                                  "surface-forms.jsonl"      sf-stats}
                                                 timestamp)]

          ;; Step 4: verify manifest
          (is (= 3 (get-in manifest [:files "dictionary-entries.jsonl" :count])))
          (is (pos? (get-in manifest [:files "dictionary-entries.jsonl" :bytes])))
          (is (pos? (get-in manifest [:files "surface-forms.jsonl" :count])))

          ;; SHA in manifest matches actual file
          (is (= (emit/sha256 entries-path)
                 (get-in manifest [:files "dictionary-entries.jsonl" :sha256])))
          (is (= (emit/sha256 sf-path)
                 (get-in manifest [:files "surface-forms.jsonl" :sha256])))

          ;; Roundtrip parse: first doc has correct _id prefix and required fields
          (let [first-line (first (str/split-lines (slurp entries-path)))
                doc        (json/parse-string first-line true)]
            (is (str/starts-with? (:_id doc) "lemma:"))
            (is (some? (:type doc)))
            (is (some? (:value doc)))
            (is (seq (:translation doc))))

          ;; Manifest file is readable EDN
          (let [manifest-data (edn/read-string (slurp (str tmp-dir "/manifest.edn")))]
            (is (= timestamp (:generated-at manifest-data)))
            (is (map? (:files manifest-data))))

          ;; Cleanup
          (doseq [f [entries-path sf-path (str tmp-dir "/manifest.edn")]]
            (io/delete-file f true))
          (io/delete-file tmp-dir true))))))

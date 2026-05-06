(ns dictionary.import-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [clojure.java.io :as io]
   [dictionary.import]))


;; Access private fns via var
(def ^:private parse-args #'dictionary.import/parse-args)


(def ^:private compare-meta #'dictionary.import/compare-meta)


(def ^:private jsonl-batches #'dictionary.import/jsonl-batches)


(def ^:private ours-docs? #'dictionary.import/ours-docs?)


(def ^:private doc-digest #'dictionary.import/doc-digest)


(def ^:private diff-doc #'dictionary.import/diff-doc)


(def ^:private delete-candidates #'dictionary.import/delete-candidates)


(def ^:private validate-sample-docs! #'dictionary.import/validate-sample-docs!)

(def ^:private lemma-schema #'dictionary.import/lemma-schema)


(def ^:private surface-schema #'dictionary.import/surface-schema)


(def ^:private duplicate-doc-ids #'dictionary.import/duplicate-doc-ids)


(def ^:private unresolved-translation-count #'dictionary.import/unresolved-translation-count)


;; ---------------------------------------------------------------------------
;; parse-args (private)
;; ---------------------------------------------------------------------------


(deftest parse-args-all-flags
  (testing "parses all flags correctly"
    (let [opts (parse-args ["--input-dir" "/tmp/dict"
                            "--db" "my-db"
                            "--batch" "500"
                            "--max-bytes" "1000000"
                            "--reset"])]
      (is (= "/tmp/dict" (:input-dir opts)))
      (is (= "my-db" (:db opts)))
      (is (= 500 (:batch opts)))
      (is (= 1000000 (:max-bytes opts)))
      (is (true? (:reset opts))))))


(deftest parse-args-dry-run
  (testing "parses --dry-run flag"
    (let [opts (parse-args ["--input-dir" "/tmp/dict" "--dry-run"])]
      (is (true? (:dry-run opts)))
      (is (not (:reset opts))))))


(deftest parse-args-defaults
  (testing "uses defaults when flags not provided"
    (let [opts (parse-args ["--input-dir" "/tmp/dict"])]
      (is (= "dictionary-db" (:db opts)))
      (is (= 1000 (:batch opts)))
      (is (= 5242880 (:max-bytes opts)))
      (is (not (:reset opts))))))


(deftest parse-args-reset-positioning
  (testing "--reset can appear at different positions"
    (let [opts1 (parse-args ["--reset" "--input-dir" "/tmp/dict"])
          opts2 (parse-args ["--input-dir" "/tmp/dict" "--reset"])]
      (is (true? (:reset opts1)))
      (is (true? (:reset opts2))))))


;; ---------------------------------------------------------------------------
;; compare-meta (private)
;; ---------------------------------------------------------------------------


(deftest compare-meta-match
  (testing "returns true when schema version and SHA match"
    (is (true? (compare-meta {:schema-version 2
                              :dictionary-epoch "dictionary-v2-raw-lemma-ids"
                              :manifest-sha256 "abc123"}
                             "abc123")))))


(deftest compare-meta-version-mismatch
  (testing "returns false when schema version doesn't match"
    (is (not (compare-meta {:schema-version 1
                            :dictionary-epoch "dictionary-v2-raw-lemma-ids"
                            :manifest-sha256 "abc123"}
                           "abc123")))))


(deftest compare-meta-sha-mismatch
  (testing "returns false when SHA doesn't match"
    (is (not (compare-meta {:schema-version 2
                            :dictionary-epoch "dictionary-v2-raw-lemma-ids"
                            :manifest-sha256 "abc123"}
                           "xyz789")))))


(deftest compare-meta-epoch-mismatch
  (testing "returns false when dictionary epoch doesn't match"
    (is (not (compare-meta {:schema-version 2
                            :dictionary-epoch "old"
                            :manifest-sha256 "abc123"}
                           "abc123")))))


(deftest compare-meta-nil-meta
  (testing "nil meta returns false"
    (is (not (compare-meta nil "abc123")))))


;; ---------------------------------------------------------------------------
;; diff import helpers (private)
;; ---------------------------------------------------------------------------


(deftest ours-docs-recognizes-our-docs
  (testing "only ours docs are managed by diff deletes"
    (is (true? (ours-docs? "dictionary-meta")))
    (is (true? (ours-docs? "lemma:gehen:verb")))
    (is (true? (ours-docs? "sf:gehen")))
    (is (false? (ours-docs? "_design/search")))))


(deftest doc-digest-ignores-revision
  (testing "CouchDB _rev changes do not force an upsert"
    (is (= (doc-digest {:_id "a" :value "x" :_rev "1-a"})
           (doc-digest {:_id "a" :value "x" :_rev "2-b"})))))


(deftest diff-doc-skips-unchanged-and-attaches-rev
  (testing "unchanged docs are skipped"
    (let [doc {:_id "lemma:a:noun" :value "a"}
          existing {"lemma:a:noun" {:rev "1-a" :digest (doc-digest doc)}}]
      (is (nil? (diff-doc existing doc)))))
  (testing "changed docs keep their existing revision"
    (let [existing {"lemma:a:noun" {:rev "1-a" :digest (doc-digest {:_id "lemma:a:noun" :value "old"})}}]
      (is (= {:_id "lemma:a:noun" :value "new" :_rev "1-a"}
             (diff-doc existing {:_id "lemma:a:noun" :value "new"})))))
  (testing "new docs are inserted without a revision"
    (is (= {:_id "lemma:b:noun" :value "b"}
           (diff-doc {} {:_id "lemma:b:noun" :value "b"})))))


(deftest delete-candidates-only-selects-missing-managed-docs
  (testing "unmanaged docs and still-seen docs survive diff import"
    (let [existing {"lemma:old:noun"  {:rev "1-old" :digest "x"}
                    "sf:old"          {:rev "1-sf" :digest "x"}
                    "dictionary-meta" {:rev "1-meta" :digest "x"}
                    "_design/search"  {:rev "1-design" :digest "x"}
                    "lemma:seen:noun" {:rev "1-seen" :digest "x"}}
          seen     #{"dictionary-meta" "lemma:seen:noun"}
          result   (set (delete-candidates existing seen))]
      (is (= #{{:_id "lemma:old:noun" :_rev "1-old"}
               {:_id "sf:old" :_rev "1-sf"}}
             result)))))


;; ---------------------------------------------------------------------------
;; jsonl-batches (private)
;; ---------------------------------------------------------------------------


(defn- make-reader
  "Create a BufferedReader from a string for testing jsonl-batches."
  [text]
  (io/reader (java.io.StringReader. text)))


(deftest jsonl-batches-basic-batching
  (testing "batches 3 lines with batch-size 2 into 2 batches"
    (let [lines   (str "{\"_id\":\"a\",\"v\":1}\n"
                       "{\"_id\":\"b\",\"v\":2}\n"
                       "{\"_id\":\"c\",\"v\":3}\n")
          reader  (make-reader lines)
          batches (vec (jsonl-batches reader 2 999999))]
      (is (= 2 (count batches)))
      (is (= 2 (count (first batches))))
      (is (= 1 (count (second batches)))))))


(deftest jsonl-batches-blank-line-skipping
  (testing "blank lines are skipped"
    (let [lines   (str "{\"_id\":\"a\"}\n"
                       "\n"
                       "   \n"
                       "{\"_id\":\"b\"}\n")
          reader  (make-reader lines)
          batches (vec (jsonl-batches reader 10 999999))]
      (is (= 1 (count batches)))
      (is (= 2 (count (first batches)))))))


(deftest jsonl-batches-byte-size-threshold
  (testing "batch splits when byte size threshold is reached"
    ;; Each line is ~15-20 bytes. Set max-bytes very low to force splitting.
    (let [lines   (str "{\"_id\":\"a\",\"v\":1}\n"
                       "{\"_id\":\"b\",\"v\":2}\n"
                       "{\"_id\":\"c\",\"v\":3}\n")
          reader  (make-reader lines)
          batches (vec (jsonl-batches reader 100 20))]
      ;; With max-bytes=20, each line should trigger a new batch
      (is (>= (count batches) 2)))))


;; ---------------------------------------------------------------------------
;; validation (private)
;; ---------------------------------------------------------------------------


(defn- temp-jsonl
  [content]
  (let [file (java.io.File/createTempFile "import-test" ".jsonl")]
    (.deleteOnExit file)
    (spit file content)
    (.getAbsolutePath file)))


(deftest validate-sample-docs-allows-empty-translation-vector
  (testing "empty translation is valid unresolved state"
    (let [path (temp-jsonl "{\"_id\":\"lemma:hunde:noun\",\"type\":\"dictionary-entry\",\"value\":\"Hunde\",\"translation\":[]}\n")]
      (is (nil? (validate-sample-docs! path lemma-schema))))))


(deftest validate-sample-docs-rejects-non-vector-translation
  (testing "translation must still be structurally valid"
    (let [path (temp-jsonl "{\"_id\":\"lemma:hunde:noun\",\"type\":\"dictionary-entry\",\"value\":\"Hunde\",\"translation\":null}\n")]
      (with-redefs [dictionary.import/exit! (fn [code message]
                                              (throw (ex-info message {:code code})))]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"Invalid doc"
                              (validate-sample-docs! path lemma-schema)))))))


(deftest validate-sample-docs-accepts-surface-form-entry-maps
  (testing "surface forms store denormalized entry maps for autocomplete"
    (let [path (temp-jsonl (str "{\"_id\":\"sf:hunde\",\"value\":\"hunde\","
                                "\"entries\":[{\"lemma-id\":\"lemma:der hund:noun\","
                                "\"lemma\":\"der Hund\",\"rank\":100}]}\n"))]
      (is (nil? (validate-sample-docs! path surface-schema))))))


(deftest duplicate-doc-ids-finds-repeated-ids
  (testing "import validation can fail before CouchDB conflicts"
    (let [path (temp-jsonl (str "{\"_id\":\"lemma:a:noun\"}\n"
                                "{\"_id\":\"lemma:b:noun\"}\n"
                                "{\"_id\":\"lemma:a:noun\"}\n"
                                "{\"_id\":\"lemma:a:noun\"}\n"))]
      (is (= ["lemma:a:noun"] (duplicate-doc-ids path))))))


(deftest unresolved-translation-count-counts-empty-vectors
  (testing "counts entries still missing translations"
    (let [path (temp-jsonl (str "{\"_id\":\"lemma:a:noun\",\"translation\":[]}\n"
                                "{\"_id\":\"lemma:b:noun\",\"translation\":[{\"lang\":\"ru\",\"value\":\"б\"}]}\n"))]
      (is (= 1 (unresolved-translation-count path))))))

(ns dictionary.emit-test
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [dictionary.emit :as emit]
   [utils]))


;; ---------------------------------------------------------------------------
;; doc->json-line
;; ---------------------------------------------------------------------------


(deftest doc->json-line-snake-case-output
  (testing "output JSON has snake_case keys"
    (let [line (emit/doc->json-line {:foo-bar "baz" :baz-qux 1})]
      (is (str/includes? line "foo_bar"))
      (is (str/includes? line "baz_qux")))))


(deftest doc->json-line-roundtrip
  (testing "JSON can be parsed back"
    (let [doc  {:_id "test" :value "hello" :count 42}
          line (emit/doc->json-line doc)
          back (json/parse-string line true)]
      (is (= "test" (:_id back)))
      (is (= "hello" (:value back)))
      (is (= 42 (:count back))))))


;; ---------------------------------------------------------------------------
;; write-jsonl!
;; ---------------------------------------------------------------------------


(deftest write-jsonl-multi-doc
  (testing "writes multiple docs as JSONL"
    (let [f     (java.io.File/createTempFile "emit-test" ".jsonl")
          _ (.deleteOnExit f)
          path  (.getAbsolutePath f)
          docs  [{:_id "a" :value "one"} {:_id "b" :value "two"}]
          stats (emit/write-jsonl! path docs)]
      (is (= 2 (:count stats)))
      (is (pos? (:bytes stats)))
      ;; verify lines
      (let [lines (clojure.string/split-lines (slurp path))]
        (is (= 2 (count lines)))))))


(deftest write-jsonl-empty-seq
  (testing "empty seq writes empty file"
    (let [f     (java.io.File/createTempFile "emit-test" ".jsonl")
          _ (.deleteOnExit f)
          path  (.getAbsolutePath f)
          stats (emit/write-jsonl! path [])]
      (is (= 0 (:count stats)))
      (is (= 0 (:bytes stats))))))


(deftest write-jsonl-utf8-byte-counting
  (testing "byte count accounts for UTF-8 multi-byte chars"
    (let [f     (java.io.File/createTempFile "emit-test" ".jsonl")
          _ (.deleteOnExit f)
          path  (.getAbsolutePath f)
          ;; "собака" is 12 bytes in UTF-8 (6 chars x 2 bytes each)
          ;; "Größe" contains multi-byte ö (2 bytes) and ß (2 bytes)
          docs  [{:value "собака"} {:value "Größe"}]
          stats (emit/write-jsonl! path docs)]
      ;; byte count should be > character count due to multi-byte
      (let [file-size (.length (io/file path))]
        (is (= file-size (:bytes stats)))))))


(deftest write-jsonl-parent-dir-creation
  (testing "creates parent directories if they don't exist"
    (let [tmp   (System/getProperty "java.io.tmpdir")
          path  (str tmp "/emit-test-" (System/currentTimeMillis) "/sub/out.jsonl")
          stats (emit/write-jsonl! path [{:a 1}])]
      (is (= 1 (:count stats)))
      (is (.exists (io/file path)))
      ;; cleanup
      (io/delete-file path true)
      (io/delete-file (str tmp
                           "/emit-test-"
                           (-> path
                               (clojure.string/replace (str tmp "/") "")
                               (clojure.string/split #"/")
                               first)
                           "/sub")
                      true))))


;; ---------------------------------------------------------------------------
;; sha256
;; ---------------------------------------------------------------------------


(deftest sha256-known-digest
  (testing "produces known SHA-256 for known content"
    (let [f (java.io.File/createTempFile "sha-test" ".txt")]
      (.deleteOnExit f)
      (spit f "hello\n")
      (let [hash (emit/sha256 (.getAbsolutePath f))]
        ;; SHA-256 of "hello\n" = 5891b5b522d5df086d0ff0b110fbd9d21bb4fc7163af34d08286a2e846f6be03
        (is (= "5891b5b522d5df086d0ff0b110fbd9d21bb4fc7163af34d08286a2e846f6be03" hash))))))


(deftest sha256-64-char-hex
  (testing "SHA-256 output is always 64 hex characters"
    (let [f (java.io.File/createTempFile "sha-test" ".txt")]
      (.deleteOnExit f)
      (spit f "test content")
      (let [hash (emit/sha256 (.getAbsolutePath f))]
        (is (= 64 (count hash)))
        (is (re-matches #"[0-9a-f]{64}" hash))))))


(deftest sha256-different-content-different-hash
  (testing "different content produces different hash"
    (let [f1 (java.io.File/createTempFile "sha-test" ".txt")
          f2 (java.io.File/createTempFile "sha-test" ".txt")]
      (.deleteOnExit f1)
      (.deleteOnExit f2)
      (spit f1 "content A")
      (spit f2 "content B")
      (is (not= (emit/sha256 (.getAbsolutePath f1))
                (emit/sha256 (.getAbsolutePath f2)))))))


;; ---------------------------------------------------------------------------
;; write-manifest!
;; ---------------------------------------------------------------------------


(deftest write-manifest-structure-verification
  (testing "manifest has correct structure"
    (let [tmp-dir  (str (System/getProperty "java.io.tmpdir") "/manifest-test-" (System/currentTimeMillis))
          _ (.mkdirs (io/file tmp-dir))
          ;; write a dummy file so sha256 works
          _ (spit (str tmp-dir "/test.jsonl") "line1\nline2\n")
          manifest (emit/write-manifest! tmp-dir
                                         {"test.jsonl" {:count 2 :bytes 12}}
                                         "2026-01-30T00:00:00Z")]
      (is (= "2026-01-30T00:00:00Z" (:generated-at manifest)))
      (is (map? (:files manifest)))
      (is (= 2 (get-in manifest [:files "test.jsonl" :count])))
      (is (= 12 (get-in manifest [:files "test.jsonl" :bytes])))
      ;; cleanup
      (io/delete-file (str tmp-dir "/test.jsonl") true)
      (io/delete-file (str tmp-dir "/manifest.edn") true)
      (io/delete-file tmp-dir true))))


(deftest write-manifest-sha-correctness
  (testing "manifest SHA matches file SHA"
    (let [tmp-dir      (str (System/getProperty "java.io.tmpdir") "/manifest-sha-" (System/currentTimeMillis))
          _ (.mkdirs (io/file tmp-dir))
          content      "some jsonl content\n"
          _ (spit (str tmp-dir "/data.jsonl") content)
          expected-sha (emit/sha256 (str tmp-dir "/data.jsonl"))
          manifest     (emit/write-manifest! tmp-dir
                                             {"data.jsonl" {:count 1 :bytes 19}}
                                             "2026-01-30T00:00:00Z")]
      (is (= expected-sha (get-in manifest [:files "data.jsonl" :sha256])))
      ;; cleanup
      (io/delete-file (str tmp-dir "/data.jsonl") true)
      (io/delete-file (str tmp-dir "/manifest.edn") true)
      (io/delete-file tmp-dir true))))

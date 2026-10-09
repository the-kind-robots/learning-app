(ns examples.cache
  "Examples already generated, stored by subject key and shared by every
   account. A database that is away reads as a miss and drops the write."
  (:require
   [cheshire.core :as cheshire]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as result-set]
   [taoensso.telemere :as t]
   [utils :as utils]))


(set! *warn-on-reflection* true)


(defn digest
  "The key of a normalized `subject` under the digest of its `generation`."
  [subject generation]
  (utils/sha256-hex
   (cheshire/generate-string
    [(:word subject)
     (:translations subject)
     (:context subject)
     generation])))


(defn- unavailable!
  "Logs a failing cache and returns nil, so a broken read is a miss and a
   broken write is dropped."
  [operation error]
  (t/log!
   {:level :warn
    :id    ::unavailable
    :data  {:operation operation :error (ex-message error)}}
   "Example cache unavailable")
  nil)


(defn lookup
  "The example stored under `subject-key`, or nil when there is none or it
   cannot be read."
  [db subject-key]
  (try
    (some-> (jdbc/execute-one! db
              ;; `question_sha256` is the subject's key, under its old name.
              ["SELECT example FROM example_cache WHERE question_sha256 = ?" subject-key]
              {:builder-fn result-set/as-unqualified-kebab-maps})
            :example
            (cheshire/parse-string true))
    (catch Exception error
      (unavailable! :lookup error))))


(defn store!
  "Stores `example` under `subject-key` unless a row is already there. Returns
   true when this call wrote the row, false when one was there, and nil when
   the write failed."
  [db subject-key subject example]
  (try
    (let
      [written
       (jdbc/execute-one! db
         ["INSERT INTO example_cache (question_sha256, word, translations, context, example)
                      VALUES (?, ?, ?, ?, ?)
                      ON CONFLICT (question_sha256) DO NOTHING"
          subject-key
          (:word subject)
          (cheshire/generate-string (:translations subject))
          (:context subject)
          (cheshire/generate-string example)])]
      (= 1 (:next.jdbc/update-count written)))
    (catch Exception error
      (unavailable! :store error))))

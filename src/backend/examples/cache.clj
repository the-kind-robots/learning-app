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
  "The key of a normalized `subject` under the dictionary's `word-meta` and
   the operator's cache `version`, which is nil in the normal state."
  [subject word-meta version]
  (utils/sha256-hex
   (cheshire/generate-string
    [(:word subject)
     (:translations subject)
     (:context subject)
     word-meta
     version])))


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
         ["INSERT INTO example_cache (question_sha256, word, translations, context, example, rekeyed)
                      VALUES (?, ?, ?, ?, ?, 1)
                      ON CONFLICT (question_sha256) DO NOTHING"
          subject-key
          (:word subject)
          (cheshire/generate-string (:translations subject))
          (:context subject)
          (cheshire/generate-string example)])]
      (= 1 (:next.jdbc/update-count written)))
    (catch Exception error
      (unavailable! :store error))))


(def ^:private rekey-batch-size 500)


(defn- waiting-batch
  "Up to a batch of rows still waiting for the current key, after `rowid`."
  [db rowid]
  (jdbc/execute! db
    ["SELECT rowid AS id, word, translations, context, COALESCE(created_at, 0) AS created_at
        FROM example_cache
       WHERE rekeyed = 0 AND rowid > ?
       ORDER BY rowid
       LIMIT ?"
     rowid rekey-batch-size]
    {:builder-fn result-set/as-unqualified-kebab-maps}))


(defn- row-subject
  "The subject a stored row was made for, or nil when its glosses are not the
   JSON array that rows have been stored with."
  [{:keys [context translations word]}]
  (let [glosses (try (cheshire/parse-string translations)
                     (catch Exception _ nil))]
    (when (and (sequential? glosses) (every? string? glosses))
      {:context context :translations (vec glosses) :word word})))


(defn- move-row!
  "Puts the row `id` under `new-key`. When another row is there, the one
   stored first stays. Returns the number of rows dropped."
  [tx {:keys [id created-at]} new-key]
  (let [other (jdbc/execute-one! tx
                ["SELECT rowid AS id, COALESCE(created_at, 0) AS created_at
                    FROM example_cache
                   WHERE question_sha256 = ? AND rowid != ?"
                 new-key id]
                {:builder-fn result-set/as-unqualified-kebab-maps})]
    (cond
      (nil? other)
      (do (jdbc/execute! tx
            ["UPDATE example_cache SET question_sha256 = ?, rekeyed = 1 WHERE rowid = ?"
             new-key id])
          0)

      (<= (:created-at other) created-at)
      (do (jdbc/execute! tx ["DELETE FROM example_cache WHERE rowid = ?" id])
          1)

      :else
      (do (jdbc/execute! tx ["DELETE FROM example_cache WHERE rowid = ?" (:id other)])
          (jdbc/execute! tx
            ["UPDATE example_cache SET question_sha256 = ?, rekeyed = 1 WHERE rowid = ?"
             new-key id])
          1))))


(defn rekey!
  "Gives every row that still waits the key `subject-key` computes from its
   own word, glosses and context. When two rows land on one key, the one
   stored first stays. A row whose glosses cannot be read is skipped and
   left waiting.

   Rows go in batches of `rekey-batch-size`, each in one transaction: the
   dictionary reads, which are slow, stay outside the transaction, and a stop
   in the middle keeps everything done so far. A `subject-key` that throws
   stops the step, since the dictionary being away would fail every row after
   it too. Logs one line with the counts and the elapsed time, and returns
   the counts."
  [db subject-key]
  (let [started (System/nanoTime)
        report! (fn [outcome]
                  (let [counts (assoc outcome
                                 :elapsed-ms (quot (- (System/nanoTime) started) 1000000))]
                    (t/log! {:level :info :id ::rekeyed :data counts}
                            "Example cache re-keyed")
                    counts))]
    (try
      (loop [rowid   0
             moved   0
             dropped 0
             skipped 0]
        (let [batch (waiting-batch db rowid)]
          (if (empty? batch)
            (report! {:collapsed dropped :moved moved :skipped skipped})
            (let [keyed   (vec (for [row batch
                                     :let [subject (row-subject row)]
                                     :when subject]
                                 [row (subject-key subject)]))
                  dropped-here
                  (jdbc/with-transaction [tx db]
                    (reduce + 0 (for [[row new-key] keyed]
                                  (move-row! tx row new-key))))]
              (recur (long (:id (peek (vec batch))))
                     (long (+ moved (- (count keyed) dropped-here)))
                     (long (+ dropped dropped-here))
                     (long (+ skipped (- (count batch) (count keyed)))))))))
      (catch Exception error
        (unavailable! :rekey error)))))

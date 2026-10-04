(ns client.support.db-queries
  (:require
   [db :as db]))


(defn ^:async fetch-by-type
  [db doc-type]
  (let [{:keys [docs]} (await (db/find db {:selector {:type doc-type}}))]
    docs))


(defn fetch-examples
  [db]
  (fetch-by-type db "example"))


(defn ^:async index-names
  "The names of the secondary indexes on `db`; `_all_docs` is left out."
  [db]
  (let [{:keys [indexes]} (js->clj (await (.getIndexes ^js db)) :keywordize-keys true)]
    (disj (set (map :name indexes)) "_all_docs")))

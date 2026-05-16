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

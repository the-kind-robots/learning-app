(ns adapters.collections
  (:require
   [db.pouch :as dbs]))


(defn ^:async list-collections
  [dbs]
  (let [{colls :docs} (await (dbs/find dbs {:selector {:type "collection"}}))]
    (vec (sort-by :created-at colls))))

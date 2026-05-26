(ns adapters.collections
  (:require
   [db.pouch :as dbs]))


(defn ^:async list-collections
  [dbs]
  (let [{colls :docs} (await (dbs/find dbs {:selector {:type "collection"}}))]
    (vec (sort-by :created-at colls))))


(defn ^:async get-collection
  [dbs collection-id]
  (await (dbs/get dbs "collection" collection-id)))


(defn create-collection!
  [dbs clock name]
  (dbs/insert dbs
              {:created-at ((:clock/now-iso clock))
               :name       name
               :type       "collection"
               :word-ids   []}))


(defn rename-collection!
  [dbs collection-doc new-name]
  (dbs/insert dbs (assoc collection-doc :name new-name)))


(defn ^:async delete-collection!
  [dbs collection-id]
  (when-let [coll (await (dbs/get dbs "collection" collection-id))]
    (await (dbs/remove dbs coll))))


(defn ^:async add-word-to-collection!
  [dbs word-id collection-id]
  (when-let [coll (await (dbs/get dbs "collection" collection-id))]
    (let [current (or (:word-ids coll) [])]
      (when-not (some #(= word-id %) current)
        (await (dbs/insert dbs (assoc coll :word-ids (conj current word-id))))))))


(defn ^:async exclude-word!
  [dbs word-id collection-id]
  (when-let [coll (await (dbs/get dbs "collection" collection-id))]
    (let [current (or (:word-ids coll) [])]
      (when (some #(= word-id %) current)
        (await (dbs/insert dbs (assoc coll :word-ids (filterv #(not= word-id %) current))))))))

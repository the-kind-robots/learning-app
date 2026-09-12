(ns adapters.collections
  (:require
   [db.pouch :as dbs]))


(def schema
  {:type "collection"
   :db   :user/db})


(defn ^:async list-collections
  [dbs]
  (let [{colls :docs} (await (dbs/find-all dbs schema {}))]
    (vec (sort-by :created-at colls))))


(defn ^:async get-collection
  [dbs collection-id]
  (await (dbs/get dbs schema collection-id)))


(defn create-collection!
  [dbs clock name]
  (dbs/insert dbs
              schema
              {:created-at ((:clock/now-iso clock))
               :name       name
               :word-ids   []}))


(defn rename-collection!
  [dbs collection-doc new-name]
  (dbs/insert dbs schema (assoc collection-doc :name new-name)))


(defn ^:async delete-collection!
  [dbs collection-id]
  (when-let [coll (await (dbs/get dbs schema collection-id))]
    (await (dbs/remove dbs schema coll))))


(defn ^:async add-word-to-collection!
  [dbs word-id collection-id]
  (when-let [coll (await (dbs/get dbs schema collection-id))]
    (let [current (or (:word-ids coll) [])]
      (when-not (some #(= word-id %) current)
        (await (dbs/insert dbs schema (assoc coll :word-ids (conj current word-id))))))))


(defn ^:async exclude-word!
  [dbs word-id collection-id]
  (when-let [coll (await (dbs/get dbs schema collection-id))]
    (let [current (or (:word-ids coll) [])]
      (when (some #(= word-id %) current)
        (await (dbs/insert dbs schema (assoc coll :word-ids (filterv #(not= word-id %) current))))))))


(defn ^:async without-word
  "The documents of every collection holding `word-id`, with it removed —
   ready for the bulk write that deletes the word, so the word and its
   memberships go in one atomic write."
  [dbs word-id]
  (let [{colls :docs} (await (dbs/find-all dbs schema {}))]
    (->> colls
         (filter #(some #{word-id} (:word-ids %)))
         (mapv #(update % :word-ids (partial filterv (complement #{word-id})))))))

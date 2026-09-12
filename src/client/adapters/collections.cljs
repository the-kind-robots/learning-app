(ns adapters.collections
  "Repository of named collections. Outward a collection is
   `{:id :name :word-ids :created-at}`."
  (:require
   [db.pouch :as dbs]))


(def schema
  {:type "collection"
   :db   :user/db})


(defn- doc->collection
  [doc]
  {:id         (:_id doc)
   :created-at (:created-at doc)
   :name       (:name doc)
   :word-ids   (or (:word-ids doc) [])})


(defn ^:async list-collections
  [dbs]
  (let [{colls :docs} (await (dbs/find-all dbs schema {}))]
    (->> colls
         (sort-by :created-at)
         (mapv doc->collection))))


(defn ^:async get-collection
  [dbs collection-id]
  (some-> (await (dbs/get dbs schema collection-id)) doc->collection))


(defn create-collection!
  [dbs clock name]
  (dbs/insert dbs
              schema
              {:created-at ((:clock/now-iso clock))
               :name       name
               :word-ids   []}))


(defn ^:async rename-collection!
  [dbs collection-id new-name]
  (when-let [doc (await (dbs/get dbs schema collection-id))]
    (await (dbs/insert dbs schema (assoc doc :name new-name)))))


(defn ^:async delete-collection!
  [dbs collection-id]
  (when-let [doc (await (dbs/get dbs schema collection-id))]
    (await (dbs/remove dbs schema doc))))


(defn ^:async add-word-to-collection!
  [dbs word-id collection-id]
  (when-let [doc (await (dbs/get dbs schema collection-id))]
    (let [current (or (:word-ids doc) [])]
      (when-not (some #(= word-id %) current)
        (await (dbs/insert dbs schema (assoc doc :word-ids (conj current word-id))))))))


(defn ^:async exclude-word!
  [dbs word-id collection-id]
  (when-let [doc (await (dbs/get dbs schema collection-id))]
    (let [current (or (:word-ids doc) [])]
      (when (some #(= word-id %) current)
        (await (dbs/insert dbs schema (assoc doc :word-ids (filterv #(not= word-id %) current))))))))


(defn ^:async without-word
  "The documents of every collection holding `word-id`, with it removed —
   ready for the bulk write that deletes the word, so the word and its
   memberships go in one atomic write."
  [dbs word-id]
  (let [{colls :docs} (await (dbs/find-all dbs schema {}))]
    (->> colls
         (filter #(some #{word-id} (:word-ids %)))
         (mapv #(update % :word-ids (partial filterv (complement #{word-id})))))))

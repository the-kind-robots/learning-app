(ns adapters.words
  "Repository of the vocabulary: words and phrases are one document type
   that differ by `:kind`."
  (:require
   [adapters.collections :as collections]
   [adapters.reviews :as reviews]
   [clojure.core :as clojure]
   [db.pouch :as dbs]
   [domain.vocabulary :as vocabulary]))


(def schema
  "`by-type` serves every type-selected query on user-db, not only this one."
  {:type    "vocab"
   :db      :user/db
   :indexes [{:name "by-type" :fields [:type]}]
   :views   {"vocab-preview"
             {:map
              "function (doc) { if (doc.type === 'vocab') emit(doc._id, [doc.kind, doc.value, doc.translation]); }"}}})


(def ^:private preview-view
  "One row per word, keyed by id, valued `[kind value translation]` — what a
   list of words shows, without fetching the documents."
  (dbs/view schema "vocab-preview"))


(defn- now-iso
  [clock]
  ((:clock/now-iso clock)))


(defn- stamp
  [clock word]
  (let [now (now-iso clock)]
    (cond-> (assoc word :modified-at now)
      (nil? (:created-at word)) (assoc :created-at now))))


(defn ^:async previews
  "Every word and phrase as `{:_id :kind :value :translation}` — what a list
   shows — or only `word-ids` when given. Read from the vocab view, so no
   document is fetched."
  [dbs word-ids]
  (let [{rows :rows} (await (dbs/query dbs preview-view {}))
        wanted-ids   (some-> word-ids set)]
    (->> rows
         (filter (fn [{id :id}]
                   (or (nil? wanted-ids) (contains? wanted-ids id))))
         (mapv (fn [{id :id [kind value translation] :value}]
                 {:_id         id
                  :kind        kind
                  :translation translation
                  :value       value})))))


(defn ^:async count-words
  [dbs]
  (clojure/count (await (previews dbs nil))))


(defn ^:async find-by-value
  [dbs value]
  (await (dbs/get dbs schema (vocabulary/vocab-id value))))


(defn ^:async get-word
  [dbs word-id]
  (await (dbs/get dbs schema word-id)))


(defn save-word!
  [dbs clock word]
  (dbs/insert dbs schema (stamp clock word)))


(defn ^:async delete-word!
  "Atomically removes the word, its reviews and its collection memberships
   from user-db in one bulk write: the reviews and collections repositories
   hand over their tombstoned and updated documents. Examples live in
   device-db; the use-case purges them afterwards, best effort. Returns true
   when a word was deleted."
  [dbs word-id]
  (when-let [word (await (dbs/get dbs schema word-id))]
    (let [user-bulk (-> [(assoc word :_deleted true)]
                        (into (await (reviews/tombstones-of dbs word-id)))
                        (into (await (collections/without-word dbs word-id))))]
      (await (dbs/bulk-docs dbs schema user-bulk))
      true)))

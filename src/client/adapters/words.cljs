(ns adapters.words
  "Repository of the vocabulary: words and phrases are one document type
   that differ by `:kind`. Outward a word is
   `{:id :kind :value :translation :created-at :modified-at}`."
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


(defn- doc->word
  [doc]
  (-> doc
      (dissoc :_id :_rev :type)
      (assoc :id (:_id doc))))


(defn- word->doc
  [word]
  (-> word
      (dissoc :id)
      (assoc :_id (:id word))))


(defn- stamp
  [clock word]
  (let [now ((:clock/now-iso clock))]
    (cond-> (assoc word :modified-at now)
      (nil? (:created-at word)) (assoc :created-at now))))


(defn ^:async previews
  "Every word and phrase as `{:id :kind :value :translation}` — what a list
   shows — or only `word-ids` when given. Read from the vocab view, so no
   document is fetched."
  [dbs word-ids]
  (let [{rows :rows} (await (dbs/query dbs preview-view {}))
        wanted-ids   (some-> word-ids set)]
    (->> rows
         (filter (fn [{id :id}]
                   (or (nil? wanted-ids) (contains? wanted-ids id))))
         (mapv (fn [{id :id [kind value translation] :value}]
                 {:id          id
                  :kind        kind
                  :translation translation
                  :value       value})))))


(defn ^:async count-words
  [dbs]
  (clojure/count (await (previews dbs nil))))


(defn ^:async get-word
  [dbs word-id]
  (some-> (await (dbs/get dbs schema word-id)) doc->word))


(defn ^:async find-by-value
  [dbs value]
  (await (get-word dbs (vocabulary/vocab-id value))))


(defn ^:async save-word!
  "Writes the word under its id, over whichever revision is stored."
  [dbs clock word]
  (let [stored (await (dbs/get dbs schema (:id word)))
        doc    (cond-> (word->doc (stamp clock word))
                 stored (assoc :_rev (:_rev stored)))]
    (await (dbs/insert dbs schema doc))))


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

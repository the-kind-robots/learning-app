(ns adapters.words
  "Repository of the vocabulary: words and phrases are one document type
   that differ by `:kind`. Outward a word is
   `{:id :kind :value :translation :created-at :modified-at}`."
  (:require
   [adapters.repository :as repository]
   [db.pouch :as dbs]
   [domain.vocabulary :as vocabulary]))


(def schema
  {:type  "vocab"
   :db    :user/db
   :views {"vocab-preview"
           {:map
            "function (doc) { if (doc.type === 'vocab') emit(doc._id, [doc.kind, doc.value, doc.translation]); }"}}})


(def ^:private preview-view
  "One row per word, keyed by id, valued `[kind value translation]` — what a
   list of words shows, without fetching the documents."
  (dbs/view schema "vocab-preview"))


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
  (let [{rows :rows} (await (dbs/query dbs
                                       preview-view
                                       (cond-> {} word-ids (assoc :keys (vec word-ids)))))]
    (mapv (fn [{id :id [kind value translation] :value}]
            {:id          id
             :kind        kind
             :translation translation
             :value       value})
          rows)))


(defn ^:async count-words
  [dbs]
  (count (await (previews dbs nil))))


(defn ^:async get-word
  [dbs word-id]
  (some-> (await (dbs/get dbs schema word-id)) repository/entity))


(defn ^:async find-by-value
  [dbs value]
  (await (get-word dbs (vocabulary/vocab-id value))))


(defn save-word!
  "Writes the word under its id, over whichever revision is stored."
  [dbs clock word]
  (repository/upsert! dbs schema (repository/doc (stamp clock word))))


(defn ^:async delete-word!
  "Removes the word from user-db in one atomic bulk write together with
   `companion-docs`: the tombstoned reviews and the updated collection
   documents the use-case gathered from their repositories. Examples live
   in device-db; the use-case purges them afterwards. Returns true when a
   word was deleted."
  [dbs word-id companion-docs]
  (when-let [word (await (dbs/get dbs schema word-id))]
    (await (dbs/bulk-docs dbs schema (into [(repository/tombstone word)] companion-docs)))
    true))

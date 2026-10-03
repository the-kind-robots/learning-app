(ns adapters.learner.documents
  "The document types of the learner's data, and how a stored document
   becomes a domain entity and back. Each schema names the document's type,
   the database it lives in, and any index the engine keeps for it
   (`db.pouch`).

   Outward, the entities are:

     word        {:id :kind :value :translation :created-at :modified-at}
     review      {:id :word-id :retained :created-at}
     collection  {:id :name :word-ids :created-at}
     example     {:id :word-id :collection-id :word :value :translation
                  :structure :created-at}

   Words and phrases are one document type that differ by `:kind`.")


(def vocab-schema
  {:type "vocab"
   :db   :user/db})


(def review-schema
  {:type "review"
   :db   :user/db})


(def collection-schema
  {:type "collection"
   :db   :user/db})


(def example-schema
  {:type "example"
   :db   :device/db})


(def schemas
  "Every document type of the learner's data."
  [vocab-schema review-schema collection-schema example-schema])


(defn entity
  "The domain shape of a stored document: `:id` for `:_id`, no revision, no
   type."
  [doc]
  (-> doc
      (dissoc :_id :_rev :type)
      (assoc :id (:_id doc))))


(defn doc
  "The stored shape of an entity: `:_id` for `:id`."
  [entity]
  (-> entity
      (dissoc :id)
      (assoc :_id (:id entity))))


(defn tombstone
  "The deletion of `doc`."
  [doc]
  (assoc doc :_deleted true))


(defn doc->word
  [doc]
  (entity doc))


(defn doc->review
  "The part of a review that retention reads."
  [doc]
  (assoc (select-keys doc [:created-at :retained :word-id]) :id (:_id doc)))


(defn doc->collection
  [doc]
  (update (entity doc) :word-ids #(or % [])))


(defn doc->example
  [doc]
  (entity doc))

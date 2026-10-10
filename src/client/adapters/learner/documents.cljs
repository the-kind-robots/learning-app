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
                  :structure}

   Words and phrases are one document type that differ by `:kind`. Every
   type lives in user-db and replicates with the account."
  (:require
   [clojure.walk :as walk]
   [goog.crypt :as crypt])
  (:import
   [goog.crypt Sha1]))


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
   :db   :user/db})


(defn pair-key
  "The entry `word-id` and the collection `collection-id` as one string, as
   an id carries them: the two joined by a colon. For an example made
   outside every collection, the collection id is nil and nothing follows
   the colon. A collection id may carry colons of its own, as in
   `collection:1-87155332`; nothing splits a key back into its parts."
  [word-id collection-id]
  (str word-id ":" collection-id))


(defn- sorted-keys
  "`x` with every map in it sorted by key. Two maps with the same entries
   then print and serialise alike, in whatever order they were built."
  [x]
  (walk/postwalk #(if (map? %) (into (sorted-map) %) %) x))


(defn- content-hash
  "A short hash of what makes an example itself: its sentence, its
   translation and its structure, whose maps are sorted by key
   (`sorted-keys`). The hash is taken of the three as JSON, so it depends
   on nothing but the content. Two examples with the same content have the
   same hash on any device."
  [value translation structure]
  (let [sha (Sha1.)]
    (.update sha (crypt/stringToUtf8ByteArray (js/JSON.stringify (clj->js [value translation structure]))))
    (subs (crypt/byteArrayToHex (.digest sha)) 0 12)))


(defn example-doc
  "The document that stores `example`, a fetched example `{:value
   :translation :structure}`, for the entry `word-id`, whose text is
   `word`, in the collection `collection-id`. The collection id is nil for
   an example made outside every collection, and the document then has no
   `:collection-id`.

   The id is `example:`, the pair (`pair-key`), a colon and a hash of the
   example's content. Different examples of one pair get different ids, and
   all of them are kept. The same example gets the same id on every device.
   The document holds nothing that depends on the device or on the time,
   and its keys come in one order. So two devices that store the same
   example write the same document, PouchDB gives both the same revision,
   and replication leaves one document without a conflict."
  [word-id word collection-id example]
  (let [{:keys [translation value]} example
        structure                 (sorted-keys (:structure example))]
    (cond-> {:_id         (str "example:" (pair-key word-id collection-id) ":" (content-hash value translation structure))
             :structure   structure
             :translation translation
             :type        (:type example-schema)
             :value       value
             :word        word
             :word-id     word-id}
      collection-id (assoc :collection-id collection-id))))


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
  "The deletion of `doc`: its id, revision and type, marked deleted."
  [doc]
  (assoc (select-keys doc [:_id :_rev :type]) :_deleted true))


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

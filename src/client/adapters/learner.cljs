(ns adapters.learner
  "The learner's data for the use cases: memory to read, every write, and
   the active collection.

   A write reads the document it changes from PouchDB by id — the winner —
   makes its change to that, and writes it (`db.pouch/write-latest!`). Then
   it catches memory up with user-db's change log (`catch-up!`), so the
   screen that asked for it can show it at once. Memory takes the write the
   way it takes every other change: in the order the database stored it,
   through the change feed's catch-up (ADR-0019). A decision that
   needs every collection — whether a name is taken, which collections
   list a word — reads memory once it has caught up with the databases. A
   write queries no index.

   Deleting a word removes the word and its place in every collection. Its
   reviews and its examples stay, and come back with the word when it is
   added again. Deleting a collection removes the collection only.

   `learner` is `{:clock :dbs :store}`: the clock, the databases, and the
   app store that keeps memory under `:learner/memory`."
  (:require
   [adapters.active-collection :as active-collection]
   [adapters.example-fetch :as example-fetch]
   [adapters.learner.documents :as documents]
   [adapters.learner.loader :as loader]
   [adapters.learner.memory :as memory]
   [db.pouch :as dbs]
   [domain.vocabulary :as vocabulary]
   [lambdaisland.glogi :as log]))


(defn current-memory
  "The learner's data in memory as it stands now."
  [{:keys [store]}]
  (:learner/memory @store))


(defn active-collection
  "The collection that is active on this device, as memory has it, or nil
   when «Всё подряд» is active. The device stores the id of its choice in
   localStorage. When memory has no collection under that id, because the
   collection was deleted here or on another device, «Всё подряд» is active."
  [learner]
  (memory/collection (current-memory learner) (active-collection/active-collection-id)))


(defn set-active-collection!
  "Stores `collection-id` as this device's active collection; nil makes
   «Всё подряд» active."
  [_learner collection-id]
  (active-collection/set-active-collection! collection-id))


(defn loaded
  "Resolves once memory has everything the databases held at start."
  [{:keys [store]}]
  (js/Promise.
   (fn [resolve]
     (if (:learner/loaded? @store)
       (resolve nil)
       (let [watch-key (gensym "loaded-")]
         (add-watch store
                    watch-key
                    (fn [_ _ _ state]
                      (when (:learner/loaded? state)
                        (remove-watch store watch-key)
                        (resolve nil)))))))))


(defn ^:async catch-up!
  "Resolves once memory has whatever user-db stored that its change feed
   has not brought yet (`adapters.learner.loader/catch-up!`). Every
   document a write of the learner's data makes, and every document a
   decision here reads, lives in user-db. After a write, this is how memory
   takes it."
  [{:keys [store]}]
  (await (loader/catch-up! store :user/db)))


(defn- ^:async persist!
  "Writes the new document `doc` of `schema`'s type to PouchDB, and catches
   memory up with it. Resolves with the entity written."
  [learner schema doc]
  (let [written (await (dbs/insert (:dbs learner) schema doc))]
    (await (catch-up! learner))
    (documents/entity written)))


(def ^:private word-kind
  "A word: its schema, and how its stored document becomes the entity."
  {:from-doc documents/doc->word
   :schema   documents/vocab-schema})


(def ^:private collection-kind
  "A collection: its schema, and how its stored document becomes the
   entity."
  {:from-doc documents/doc->collection
   :schema   documents/collection-schema})


(defn- ^:async update!
  "Writes the entity `id`, of `kind`, as `change` makes it out of the
   version PouchDB holds as the winner (`db.pouch/write-latest!`), and
   catches memory up with it. `change` takes that entity, or nil when there
   is none, and returns the entity to write, or nil to write nothing.
   Resolves with `{:stored :written}`: the entity the change was made to,
   and the entity written; nil when nothing was written."
  [learner {:keys [from-doc schema]} id change]
  (when-let [{:keys [stored written]}
             (await (dbs/write-latest! (:dbs learner)
                                       schema
                                       id
                                       #(some-> (change (some-> % from-doc)) documents/doc)))]
    (await (catch-up! learner))
    {:stored  (some-> stored from-doc)
     :written (documents/entity written)}))


(defn- now-iso
  [{:keys [clock]}]
  ((:clock/now-iso clock)))


(defn- stamped
  "`word` stamped modified now; a word with no creation time is also
   stamped created now."
  [learner word]
  (let [now (now-iso learner)]
    (cond-> (assoc word :modified-at now)
      (nil? (:created-at word)) (assoc :created-at now))))


(defn ^:async add-word!
  "Adds the word or phrase `entry` to the vocabulary. When a word is stored
   under its id already, the entry's translations are merged into the
   stored ones, and the stored word keeps its kind and its creation time.
   Resolves with `{:word :created?}`: the word as written, and whether it
   is new."
  [learner entry]
  (let [{:keys [stored written]}
        (await (update! learner
                        word-kind
                        (:id entry)
                        (fn [stored]
                          (stamped learner
                                   (if stored
                                     (update stored :translation vocabulary/merge-translations (:translation entry))
                                     entry)))))]
    {:created? (nil? stored) :word written}))


(defn ^:async update-word!
  "Changes the word `word-id` with `change`, a function from the stored word
   to the changed one, and stamps it modified now. Resolves with the word
   as written, or nil when there is no such word."
  [learner word-id change]
  (:written (await (update! learner
                            word-kind
                            word-id
                            (fn [stored]
                              (when stored
                                (stamped learner (change stored))))))))


(defn add-review!
  "Writes a new review of `word-id`: whether the word was `retained`, and the
   `translation` it was asked with."
  [learner word-id retained translation]
  (persist! learner
            documents/review-schema
            (assoc (vocabulary/new-review word-id retained translation)
                   :created-at
                   (now-iso learner))))


(defn create-collection!
  "Writes a new, empty collection called `name`. Resolves with the
   collection as written."
  [learner name]
  (persist! learner
            documents/collection-schema
            {:created-at (now-iso learner)
             :name       name
             :word-ids   []}))


(defn- ^:async update-collection!
  "Writes the collection `collection-id` as `change` turns it. It writes
   nothing when there is no such collection, or when `change` leaves it as
   it was. Resolves with the collection as written, or nil."
  [learner collection-id change]
  (:written (await (update! learner
                            collection-kind
                            collection-id
                            (fn [collection]
                              (when collection
                                (let [changed (change collection)]
                                  (when (not= changed collection)
                                    changed))))))))


(defn rename-collection!
  "Gives the collection `collection-id` the name `new-name`. Resolves with
   the collection as written, or nil when nothing was written."
  [learner collection-id new-name]
  (update-collection! learner collection-id #(assoc % :name new-name)))


(defn add-to-collection!
  "Adds the word `word-id` to the collection `collection-id`, unless the
   collection lists it already."
  [learner word-id collection-id]
  (update-collection! learner
                      collection-id
                      (fn [{:keys [word-ids] :as collection}]
                        (cond-> collection
                          (not (some #{word-id} word-ids)) (update :word-ids conj word-id)))))


(defn remove-from-collection!
  "Takes the word `word-id` out of the collection `collection-id`. The word
   itself stays."
  [learner word-id collection-id]
  (update-collection! learner
                      collection-id
                      (fn [collection]
                        (update collection :word-ids (partial filterv #(not= word-id %))))))


(defn- ^:async word-deletion
  "The documents that delete the word `word-id` in one bulk write, as
   PouchDB holds them now: the word's deletion, and every collection that
   lists the word, without it. Which collections list it is asked of
   memory once it has caught up."
  [{:keys [dbs] :as learner} word-id]
  (await (catch-up! learner))
  (let [listing (into []
                      (comp (filter #(some #{word-id} (:word-ids %))) (map :id))
                      (memory/collections (current-memory learner)))
        stored  (await (dbs/read-ids dbs :user/db (cons word-id listing)))
        word    (first (filter #(and (= word-id (:_id %)) (not (:_deleted %))) stored))]
    (cond-> (into []
                  (keep (fn [{:keys [_deleted word-ids] :as doc}]
                          (when (and (not _deleted) (some #{word-id} word-ids))
                            (assoc doc :word-ids (filterv #(not= word-id %) word-ids)))))
                  stored)
      word (conj (documents/tombstone (select-keys word [:_id :_rev :type]))))))


(defn- ^:async attempt-word-deletion!
  "Writes the deletion of the word `word-id` once, and catches memory up
   with what it wrote. Resolves with `{:docs :written}`: the documents it
   meant to write and those PouchDB accepted."
  [{:keys [dbs] :as learner} word-id]
  (let [docs    (await (word-deletion learner word-id))
        written (await (dbs/bulk-docs dbs documents/vocab-schema docs))]
    (await (catch-up! learner))
    {:docs docs :written written}))


(defn ^:async delete-word!
  "Deletes the word `word-id` and takes it out of every collection that
   lists it, in one write. Its reviews and its examples stay: when the word
   is added again, they come back with it.

   PouchDB writes the documents of one bulk write one by one, and refuses
   one whose revision another write has just replaced. So when any is
   refused, this reads them again and writes once more. Refused again, the
   delete has failed: the collections it took the word out of get it back
   while the word lives, the failure is logged, and this resolves false.
   Otherwise it resolves true when PouchDB had the word, and nil when it
   had none."
  [learner word-id]
  (let [complete? (fn [{:keys [docs written]}] (= (count docs) (count written)))
        deleted?  (fn [{:keys [written]}] (boolean (some #(and (= word-id (:_id %)) (:_deleted %)) written)))
        emptied   (fn [{:keys [written]}] (into [] (comp (remove :_deleted) (map :_id)) written))
        first-try (await (attempt-word-deletion! learner word-id))
        result    (if (complete? first-try) first-try (await (attempt-word-deletion! learner word-id)))]
    (cond
      (complete? result)
      (when (some :_deleted (:docs first-try)) true)

      (or (deleted? first-try) (deleted? result))
      (do (log/error :learner/word-delete-incomplete {:word-id word-id})
          false)

      :else
      (do (doseq [collection-id (into (emptied first-try) (emptied result))]
            (await (add-to-collection! learner word-id collection-id)))
          (log/error :learner/word-delete-failed {:word-id word-id})
          false))))


(defn ^:async delete-collection!
  "Deletes the collection `collection-id`. Its words stay, and so do the
   examples made for it: a collection made again has a new id, so they are
   not shown in it. «Всё подряд» shows them like any other example."
  [learner collection-id]
  (when (await (dbs/write-latest! (:dbs learner)
                                  documents/collection-schema
                                  collection-id
                                  #(when % {:_deleted true})))
    (await (catch-up! learner))))


(defn ^:async read-stored
  "What PouchDB holds under `ids`, read by id: `{:collections [...] :words
   [...] :deleted #{id}}`, the words and the collections among them, and
   the ids of those deleted."
  [{:keys [dbs]} ids]
  (let [docs (await (dbs/read-ids dbs :user/db ids))
        of   (fn [schema from-doc]
               (into [] (comp (filter #(= (:type schema) (:type %))) (map from-doc)) docs))]
    {:collections (of documents/collection-schema documents/doc->collection)
     :deleted     (into #{} (comp (filter :_deleted) (map :_id)) docs)
     :words       (of documents/vocab-schema documents/doc->word)}))


(defn request-examples!
  "Queues an example fetch for each of `requests`; see
   `adapters.example-fetch/request!`."
  [{:keys [clock dbs]} requests]
  (example-fetch/request! dbs clock requests))

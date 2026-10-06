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
   list a word — reads memory once it has caught up with user-db. A
   write queries no index.

   Deleting a word removes the word and its place in every collection. Its
   reviews and its examples stay, and come back with the word when it is
   added again. Deleting a collection removes the collection only.

   Examples an earlier build kept in device-db move to user-db
   (`examples-moved!`).

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
   [lambdaisland.glogi :as log]
   [tasks :as tasks]))


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
  "Resolves once memory has everything user-db held at start."
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
  (await (loader/catch-up! store)))


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
      word (conj (documents/tombstone word)))))


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


(def ^:private device-example-schema
  "Where an earlier build kept examples: the example type, in device-db.
   Only the move reads it."
  (assoc documents/example-schema :db :device/db))


(def ^:private move-page-size
  "How many device-db examples the move reads, writes and deletes in one
   task."
  100)


(defn- ^:async move-page!
  "Moves one page of device-db examples, `docs`. It writes each of them to
   user-db under the id its content gives it, in one bulk write; an example
   user-db holds already is not written again. Then it deletes, in one
   bulk write, every example of the page that user-db now holds. Resolves
   with how many it deleted."
  [dbs docs]
  (let [moved (into {}
                    (map (juxt :_id #(documents/example-doc (:word-id %) (:word %) (:collection-id %) %)))
                    docs)
        held  (await (dbs/insert-all-if-absent dbs documents/example-schema (vals moved)))]
    (count (await (dbs/bulk-docs dbs
                                 device-example-schema
                                 (into [] (comp (filter #(held (:_id (moved (:_id %))))) (map documents/tombstone)) docs))))))


(defn ^:async move-device-examples!
  "Moves the examples an earlier build kept in device-db to user-db, where
   they replicate: every one of them, including the examples of a word or
   a collection deleted since, which come back with it or show outside
   every collection. Every distinct example of a pair is kept; identical
   examples become one document. It reads, writes and deletes a page at a
   time (`move-page!`), with a task between pages. When it moved anything,
   it then catches memory up, so that memory holds what was moved.

   It reads device-db only up to the ids of the task queue
   (`tasks/id-prefix`). The examples an earlier build kept there have
   generated ids, which sort before them, so the move does not page
   through the queue.

   It is safe to run again and to interrupt. A run after an interrupted one
   writes nothing that user-db holds and deletes the copies left behind. A
   device-db example that could not be written stays for the next run.
   Resolves with how many device-db examples it deleted."
  [{:keys [dbs] :as learner}]
  (let [deleted (loop [after   nil
                       deleted 0]
                  (let [{:keys [docs next]} (await (dbs/read-page dbs
                                                                  (:db device-example-schema)
                                                                  {:after after
                                                                   :end   tasks/id-prefix
                                                                   :limit move-page-size
                                                                   :types #{(:type device-example-schema)}}))
                        deleted             (+ deleted (if (seq docs) (await (move-page! dbs docs)) 0))]
                    (if next
                      (do (await (dbs/next-task))
                          (recur next deleted))
                      deleted)))]
    (when (pos? deleted)
      (await (catch-up! learner))
      (log/info :learner/examples-moved {:deleted deleted}))
    deleted))


(defn ^:async examples-moved!
  "Waits for memory to load, and then moves the examples an earlier build
   kept in device-db to user-db (`move-device-examples!`). A failed move is
   logged and run again after a wait, until one succeeds
   (`db.pouch/retried`). Resolves nil
   once one has."
  [learner]
  (await (loaded learner))
  (await (dbs/retried #(move-device-examples! learner)
                      (fn [err wait]
                        (log/warn :learner/examples-move-failed {:error (str err) :retry-ms wait}))))
  nil)


(defn request-examples!
  "Queues an example fetch for each of `requests`; see
   `adapters.example-fetch/request!`."
  [{:keys [clock dbs]} requests]
  (example-fetch/request! dbs clock requests))


(defn hold-fetches-until!
  "Holds every example fetch back until the promise `ready` resolves; see
   `adapters.example-fetch/hold-until!`."
  [_learner ready]
  (example-fetch/hold-until! ready))


(defn cancel-answered-fetches!
  "Deletes the queued fetches of the pairs the examples `example-ids`
   answer; see `adapters.example-fetch/cancel-answered!`."
  [{:keys [dbs]} example-ids]
  (example-fetch/cancel-answered! dbs example-ids))

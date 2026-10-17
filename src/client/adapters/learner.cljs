(ns adapters.learner
  "The learner's data for the use cases: memory to read, every write, and
   the active collection; and everything the learner's data rests on, started
   in `start!`: the databases, memory, sync, backup.

   The learner owns its memory. It keeps it in an atom of its own, `store`,
   which the loader fills from the databases and follows them with, and
   which the app watches through `:learner/on-change`.

   A write reads the document it changes from PouchDB by id — the winner —
   makes its change to that, and writes it (`db.pouch/write-latest!`). Then
   it catches memory up with user-db's change log (`fresh-memory`), so the
   screen that asked for it can show it at once. Memory takes the write the
   way it takes every other change: in the order the database stored it,
   through the change feed's catch-up (ADR-0019). A decision that
   needs every collection — whether a name is taken, which collections
   list a word — reads memory once it has caught up with user-db. A
   write queries no index.

   Fetched examples are saved by `insert!`.

   `learner` is `{:databases :store :world}`: the databases, the atom that
   keeps memory under `:learner/memory`, and the world, for the time."
  (:require
   [abort]
   [adapters.learner.active-id :as active-id]
   [adapters.learner.backup :as backup]
   [adapters.learner.databases :as databases]
   [adapters.learner.documents :as documents]
   [adapters.learner.loader :as loader]
   [adapters.learner.memory :as memory]
   [adapters.learner.sync :as sync]
   [db.pouch :as pouch]
   [lambdaisland.glogi :as log]
   [runtime.system :as system]))


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
  (memory/collection (current-memory learner) (active-id/active-collection-id)))


(defn set-active-collection!
  "Stores `collection-id` as this device's active collection; nil makes
   «Всё подряд» active."
  [_learner collection-id]
  (active-id/set-active-collection! collection-id))


(defn new-learner
  "The learner over the world (time), the `databases` and the `store`, the
   atom that keeps memory."
  [{:keys [databases store world]}]
  {:databases databases
   :store     store
   :world     world})


(defn- until-store
  "A promise that resolves once `(pred state)` holds for the store's state,
   at once if it holds now. When `signal`, an AbortSignal, aborts first, the
   promise stays pending and the watch on the store is gone. A nil `signal`
   never aborts."
  [store pred signal]
  (js/Promise.
   (fn [resolve]
     (if (pred @store)
       (resolve nil)
       (let [watch-key (gensym "until-store-")
             unlisten  (volatile! (fn []))]
         ;; The watch goes in before the abort listener: an already aborted
         ;; signal calls `on-abort` back at once, and must find the watch.
         (add-watch store
                    watch-key
                    (fn [_ _ _ state]
                      (when (pred state)
                        (remove-watch store watch-key)
                        (@unlisten)
                        (resolve nil))))
         (vreset! unlisten (abort/on-abort signal #(remove-watch store watch-key))))))))


(defn ^:async fresh-memory
  "Resolves with memory once it has whatever user-db stored that its change
   feed has not brought yet (`adapters.learner.loader/catch-up!`). Every
   document a write of the learner's data makes, and every document a
   decision here reads, lives in user-db. After a write, this is how memory
   takes it."
  [{:keys [store] :as learner}]
  (await (loader/catch-up! store))
  (current-memory learner))


(defn- ^:async persist!
  "Writes the new document `doc` of `schema`'s type to PouchDB, and catches
   memory up with it. Resolves with the entity written."
  [learner schema doc]
  (let [written (await (pouch/insert (:databases learner) schema doc))]
    (await (fresh-memory learner))
    (documents/entity written)))


(def ^:private kinds
  "The entities a change reaches: each one's schema, and how its stored
   document becomes the entity."
  {:entity/collection {:from-doc documents/doc->collection
                       :schema   documents/collection-schema}
   :entity/word       {:from-doc documents/doc->word
                       :schema   documents/vocab-schema}})


(defn- stamped
  "`word` stamped modified now; a word with no creation time is also
   stamped created now."
  [learner word]
  (let [now ((:clock/now-iso (:world/clock (:world learner))))]
    (cond-> (assoc word :modified-at now)
      (nil? (:created-at word)) (assoc :created-at now))))


(defn- next-document
  "What `change` makes of the `stored` document of `entity`: nil when it
   writes nothing, `:entity/deleted` when the entity goes, or the document
   to write. The fields the stored document has and the entity does not are
   kept."
  [learner entity stored change]
  (let [{:keys [from-doc]} (kinds entity)
        before (some-> stored from-doc)
        after  (change before)]
    (cond
      (or (nil? after) (identical? after before)) nil
      (= :entity/deleted after) :entity/deleted
      :else (merge (apply dissoc stored (keys (documents/doc before)))
                   (documents/doc (cond->> after
                                    (= :entity/word entity) (stamped learner)))))))


(defn- ^:async live-versions
  "Every live version of the document `id` of `schema`'s type: a
   replication may leave it more than one, and the one left alive would
   bring the entity back."
  [databases schema id]
  (into [] (remove :_deleted) (await (pouch/doc-versions databases schema id))))


(defn ^:async change!
  "Changes the `entity` `id` as `change` makes it out of the version PouchDB
   holds as the winner (`db.pouch/write-latest!`), and catches memory up.
   `change` takes the entity, or nil when there is none, and returns the new
   entity; nil or the same entity to write nothing; `:entity/deleted` to
   delete every live version. Resolves with the entity written, or nil."
  [{:keys [databases] :as learner} entity id change]
  (let [{:keys [schema]} (kinds entity)
        deleting (volatile! false)
        written  (await (pouch/write-latest! databases
                                             schema
                                             id
                                             (fn [stored]
                                               (let [doc (next-document learner entity stored change)]
                                                 (vreset! deleting (= :entity/deleted doc))
                                                 (when-not @deleting doc)))))]
    (cond
      @deleting
      (let [live (await (live-versions databases schema id))]
        (when (seq (await (pouch/bulk-docs databases schema (mapv documents/tombstone live))))
          (await (fresh-memory learner)))
        nil)

      written
      (do (await (fresh-memory learner))
          (documents/entity (:written written))))))


(defn- ^:async writes-of
  "What the operation `[entity id change]` writes, as PouchDB holds the
   document now: a vector of `{:doc :previous}`, the document to write and
   the one it replaces."
  [{:keys [databases] :as learner} [entity id change]]
  (let [{:keys [schema]} (kinds entity)
        stored (await (pouch/get databases schema id))
        doc    (next-document learner entity stored change)]
    (cond
      (nil? doc)
      []

      (= :entity/deleted doc)
      (mapv (fn [version] {:doc (documents/tombstone version) :previous version})
            (await (live-versions databases schema id)))

      :else
      [{:doc      (cond-> (-> doc (dissoc :_rev) (assoc :_id id))
                    stored (assoc :_rev (:_rev stored)))
        :previous stored}])))


(defn- ^:async attempt-writes!
  "Writes the operations `ops` once, in one bulk write, and catches memory up
   with what was written. Resolves with `{:writes :written}`: the writes it
   meant to make and the documents PouchDB accepted."
  [{:keys [databases] :as learner} ops]
  (let [writes  (loop [ops ops
                       out []]
                  (if-let [[op & more] (seq ops)]
                    (recur more (into out (await (writes-of learner op))))
                    out))
        written (await (pouch/bulk-docs databases documents/vocab-schema (mapv :doc writes)))]
    (await (fresh-memory learner))
    {:writes writes :written written}))


(defn- complete?
  [{:keys [writes written]}]
  (= (count writes) (count written)))


(defn- restorations
  "The documents that put what `attempt` wrote back to the version it
   replaced. PouchDB accepts documents in the order it was given them, so
   each accepted one is the next write with its id."
  [{:keys [writes written]}]
  (loop [writes   writes
         accepted written
         out      []]
    (if-let [[{:keys [doc previous]} & more] (seq writes)]
      (let [[head & rest-accepted] accepted]
        (if (and head (= (:_id head) (:_id doc)) (= (boolean (:_deleted head)) (boolean (:_deleted doc))))
          (recur more
                 rest-accepted
                 (conj out
                       (if previous
                         (assoc previous :_rev (:_rev head))
                         (documents/tombstone head))))
          (recur more accepted out)))
      out)))


(defn ^:async change-all!
  "Applies the operations `ops`, `[entity id change]` each, as `change!`
   does, in one bulk write.

   PouchDB writes the documents of one bulk write one by one, and refuses
   one whose revision another write has just replaced. So when any is
   refused, this reads them again and writes once more. Refused again, it
   has failed: what it wrote goes back to its previous version, the failure
   is logged, and this resolves false. Otherwise it resolves true when it
   wrote something, and nil when it had nothing to write."
  [{:keys [databases] :as learner} ops]
  (let [first-try (await (attempt-writes! learner ops))
        result    (if (complete? first-try) first-try (await (attempt-writes! learner ops)))]
    (if (complete? result)
      (when (seq (:writes first-try)) true)
      (do (when-let [docs (seq (into (restorations first-try) (restorations result)))]
            (await (pouch/bulk-docs databases documents/vocab-schema (vec docs)))
            (await (fresh-memory learner)))
          (log/error :learner/change-failed {:ids (mapv (comp :_id :doc) (:writes result))})
          false))))


(defn ^:async insert!
  "Writes a new `entity` of `value`, and catches memory up. A review or a
   collection is written as it is, and resolves with the entity written.

   An example, `{:word-id :word :collection-id :example}`, is written to
   user-db under the id its content gives it (`documents/example-doc`). One
   user-db holds already is kept as it is. It resolves true when user-db
   holds the example afterwards, and false when its write failed."
  [{:keys [databases] :as learner} entity value]
  (case entity
    :entity/collection (await (persist! learner documents/collection-schema value))
    :entity/review     (await (persist! learner documents/review-schema value))
    :entity/example    (let [{:keys [collection-id example word word-id]} value
                             doc  (documents/example-doc word-id word collection-id example)
                             held (await (pouch/insert-all-if-absent databases documents/example-schema [doc]))]
                         (await (fresh-memory learner))
                         (boolean (held (:_id doc))))))


(defn changed-since
  "Resolves when memory's words or collections differ from `seen`, the ones
   a look read. New reviews and examples don't count. Aborting `signal`
   only drops the watch."
  [{:keys [store]} seen signal]
  (let [{:keys [words collections]} seen]
    (until-store store
                 (fn [{:learner/keys [memory]}]
                   (not (and (identical? words (:words memory))
                             (identical? collections (:collections memory)))))
                 signal)))


(defn on-change
  "Calls `f`, with no arguments, whenever the learner's data in `store`
   changes: memory, or whether it is loaded or unreadable. Returns a function
   that stops it."
  [{:keys [store]} f]
  (let [watch-key (gensym "on-change-")]
    (add-watch store
               watch-key
               (fn [_ _ old state]
                 (when-not (identical? old state)
                   (f))))
    (fn unsubscribe
      []
      (remove-watch store watch-key))))


(def ^:private initialized-wait-ms
  "How long a first sync pass is waited for once memory is loaded."
  60000)


;; Plain, like `capabilities`' callers: an async function would await the promise
;; `sleep` returns. See docs/dev/cljs-async-pitfalls.md.
(defn initialize
  "Resolves nil once memory is loaded and the first sync pass of this start has
   completed (`first-pass`), or once `initialized-wait-ms` have passed
   without it. The wait on the clock is cancelled by `waited`, an
   AbortController, however the wait ends."
  [{:keys [store world]} first-pass ^js waited]
  (let [{:clock/keys [sleep]} (:world/clock world)]
    (-> (until-store store :learner/loaded? nil)
        (.then (fn []
                 (js/Promise.race #js [first-pass
                                       (.then (sleep initialized-wait-ms (.-signal waited))
                                              (constantly ::timed-out))])))
        (.then (fn [won]
                 (when (= ::timed-out won)
                   (log/info :learner/gave-up-waiting-for-sync {:waited-ms initialized-wait-ms}))
                 (.abort waited)
                 nil)))))


(defn capabilities
  "What the learner offers the app, from the parts `start!` started: the
   `learner`, its `databases`, `sync`, what sync offers, and `initialized`,
   `{:ready promise}`.

   The reads take a memory value; `:learner/memory` hands the current one.
   `:learner/on-change` calls back whenever the learner's data changes.
   `:learner/initialized` is a promise that memory is loaded and the first
   sync pass is done, or a minute has passed. `:learner/sync!` asks for a
   sync. `(:learner/on-device-joined nonce f)`: `f` is called once the device
   that opened this device's sync link carrying `nonce` has joined the
   account. `(:learner/stop-waiting-for-device nonce)` ends that wait."
  [{:keys [databases initialized learner sync]}]
  {:learner/active-collection (fn active-collection*
                                []
                                (active-collection learner))
   :learner/change!           (fn change*
                                [entity id change]
                                (change! learner entity id change))
   :learner/change-all!       (fn change-all*
                                [ops]
                                (change-all! learner ops))
   :learner/changed-since     (fn changed-since*
                                [seen signal]
                                (changed-since learner seen signal))
   :learner/collection        memory/collection
   :learner/collection-cards  memory/collection-cards
   :learner/collection-words  memory/collection-words
   :learner/collections       memory/collections
   :learner/collections-of    memory/collections-of
   :learner/examples-of       memory/examples-of
   :learner/export!           (fn export!
                                []
                                (backup/export-data! databases))
   :learner/fresh-memory      (fn fresh-memory*
                                []
                                (fresh-memory learner))
   :learner/has-account?      (:has-account? sync)
   :learner/import!           (fn import!
                                [payload]
                                (backup/import-data! databases payload))
   :learner/initialized       (:ready initialized)
   :learner/insert!           (fn insert*
                                [entity value]
                                (insert! learner entity value))
   :learner/loaded?           (fn loaded?
                                []
                                (boolean (:learner/loaded? @(:store learner))))
   :learner/matching-words    memory/matching-words
   :learner/memory            (fn memory*
                                []
                                (current-memory learner))
   :learner/on-change         (fn on-change*
                                [f]
                                (on-change learner f))
   :learner/on-device-joined  (:on-device-joined sync)
   :learner/review-history    memory/review-history
   :learner/set-active-collection! (fn set-active-collection*
                                     [collection-id]
                                     (set-active-collection! learner collection-id))
   :learner/stop-waiting-for-device (:stop-waiting-for-device sync)
   :learner/sync!             (:sync! sync)
   :learner/sync-link         (:sync-link sync)
   :learner/unreadable?       (fn unreadable?
                                []
                                (boolean (:learner/unreadable? @(:store learner))))
   :learner/word              memory/word
   :learner/word-count        memory/word-count
   :learner/words             memory/words})


(defn ^:async start!
  "Starts the learner's data: the incoming credential is handled before the
   databases open; the databases open, their background migrations run and the
   request that protects storage goes out; memory is read, replication starts
   and memory follows the databases. The parts start in that order
   (`runtime.system`), each returning at once with what it waits for as a
   promise in its value — `:checked` of memory, `:ready` of `:initialized` —
   so that nothing waits on them longer than it must. `world` and `browser`
   come from outside. Memory lives in an atom the learner makes here, as
   `:learner/memory`, `:learner/loaded?` and `:learner/unreadable?`.

   Resolves with `{:capabilities :stops}`: the learner's `capabilities` and
   what `runtime.system/stop!` takes to stop them."
  [{:keys [browser world]}]
  (let [store (atom {:learner/memory memory/empty-memory})
        {:keys [values stops]}
        (await
         (system/start!
          {:credential  {:start sync/check-incoming-auth!}
           :persistence {:start (fn [_] (databases/persist!))}
           :databases   {:after [:credential]
                         :start (fn [_] (databases/open-pouch!))}
           :migrations  {:after [:databases]
                         :start (fn [_] (databases/run-background-migrations!))}
           ;; Reads memory, then follows the databases; returns at once. The
           ;; snapshot check sits in the value as a promise, under :checked. A
           ;; promise returned bare would make every later part wait for it.
           :memory      {:requires {:databases :databases}
                         :start    (fn [{:keys [databases]}]
                                     {:checked   (loader/start-reading! databases store)
                                      :following (loader/start! databases store)})
                         :stop     (fn [{:keys [following]}]
                                     (.then following #(when % (%))))}
           ;; Loads the stored identity, writes the session cookie and, when the
           ;; device has an account, drives replication. Its passes wait for the
           ;; snapshot check (ADR-0018); the parts after it do not.
           :sync        {:requires {:databases :databases
                                    :memory    :memory}
                         :start    (fn [{:keys [databases memory]}]
                                     (sync/start! {:browser         browser
                                                   :databases       databases
                                                   :passes-wait-for (:checked memory)}))
                         :stop     sync/stop!}
           :learner     {:requires {:databases :databases}
                         :start    (fn [{:keys [databases]}]
                                     (new-learner {:databases databases :store store :world world}))}
           ;; Memory is loaded and the first pass done, or a minute passed.
           :initialized {:requires {:learner :learner
                                    :sync    :sync}
                         :start    (fn [{:keys [learner sync]}]
                                     (let [waited (js/AbortController.)]
                                       {:ready  (initialize learner (:first-pass sync) waited)
                                        :waited waited}))
                         :stop     (fn [{:keys [waited]}]
                                     (.abort ^js waited))}}))]
    {:capabilities (capabilities {:databases   (:databases values)
                                  :initialized (:initialized values)
                                  :learner     (:learner values)
                                  :sync        (:sync values)})
     :stops        stops}))

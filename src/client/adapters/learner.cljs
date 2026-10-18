(ns adapters.learner
  "The learner's data: memory to read, every write, the active collection, and what they rest on (`start!`).
   The learner owns its memory in `store`; a write goes to PouchDB, then memory catches up (ADR-0019).
   `learner` is `{:databases :store :world}`."
  (:require
   [abort]
   [adapters.learner.active-id :as active-id]
   [adapters.learner.databases :as databases]
   [adapters.learner.documents :as documents]
   [adapters.learner.loader :as loader]
   [adapters.learner.memory :as memory]
   [adapters.learner.sync :as sync]
   [db.pouch :as pouch]
   [lambdaisland.glogi :as log]))


(defn current-memory
  "The learner's data in memory as it stands now."
  [{:keys [store]}]
  (:learner/memory @store))


(defn active-collection
  "The collection active on this device, or nil for «Всё подряд». The id is kept in localStorage; when
   memory has no collection under it (deleted here or elsewhere), «Всё подряд» is active."
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
  "A promise that resolves once `(pred state)` holds for the store's state, at once if it holds now. When
   `signal` aborts first it stays pending and the watch is gone."
  [store pred signal]
  (js/Promise.
   (fn [resolve]
     (if (pred @store)
       (resolve nil)
       (let [watch-key (gensym "until-store-")
             unlisten  (volatile! (fn []))]
         ;; The watch goes in before the abort listener: an already aborted signal calls back at once and must find it.
         (add-watch store
                    watch-key
                    (fn [_ _ _ state]
                      (when (pred state)
                        (remove-watch store watch-key)
                        (@unlisten)
                        (resolve nil))))
         (vreset! unlisten (abort/on-abort signal #(remove-watch store watch-key))))))))


(defn ^:async fresh-memory
  "Resolves with memory once it has whatever user-db stored that its change feed has not brought yet
   (`adapters.learner.loader/catch-up!`). After a write, this is how memory takes it."
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
  "What `change` makes of the `stored` document of `entity`: nil to write nothing, `:entity/deleted`, or the
   document to write. Fields the stored document has and the entity lacks are kept."
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
      [{:doc      (cond-> (-> doc (dissoc :_rev) (assoc :_id id :type (:type schema)))
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


(defn ^:async change!
  "Applies `ops`, `[entity id change]` each, in one bulk write, and catches memory up; `change` returns the
   new entity, nil to write nothing, or `:entity/deleted`. A refused write is read and retried once, then
   taken back: false; otherwise true, or nil when nothing was written."
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
  "Writes a new `entity` of `value`, and catches memory up. A review or a collection resolves with the entity
   written. An example, `{:word-id :word :collection-id :example}`, is written under the id its content gives
   it (`documents/example-doc`), one already held is kept, and it resolves whether user-db holds it afterwards."
  [{:keys [databases] :as learner} entity value]
  (case entity
    :entity/collection (await (persist! learner documents/collection-schema value))
    :entity/review     (await (persist! learner documents/review-schema value))
    :entity/example    (let [{:keys [collection-id example word word-id]} value
                             doc  (documents/example-doc word-id word collection-id example)
                             held (await (pouch/insert-all-if-absent databases documents/example-schema [doc]))]
                         (await (fresh-memory learner))
                         (boolean (held (:_id doc))))))


(defn on-change
  "Calls `f`, with no arguments, whenever the learner's data in `store`
   changes: memory, or its status. Returns a function that stops it."
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


(defn status
  "`:status/unreadable` when memory could not be read from the databases,
   `:status/loaded` once it is loaded, otherwise `:status/loading`."
  [{:keys [store]}]
  (let [{:learner/keys [loaded? unreadable?]} @store]
    (cond
      unreadable? :status/unreadable
      loaded?     :status/loaded
      :else       :status/loading)))


(def ^:private initialized-wait-ms
  "How long a first sync pass is waited for once memory is loaded."
  60000)


;; Plain, not async: an async function would await the promise `sleep` returns (docs/dev/cljs-async-pitfalls.md).
(defn initialize
  "Resolves nil once memory is loaded and the first sync pass has completed (`first-pass`), or once
   `initialized-wait-ms` have passed without it; `waited` cancels the timer however the wait ends."
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
  "What the learner offers the app: memory and its status with `on-change`, writes, the active collection,
   `:learner/initialized` (a promise: memory loaded and the first sync pass done, or a minute passed) and
   `:learner/account` (`:account/exists?`, `sync!`, `join-link`, `on-device-joined`)."
  [{:keys [initialized learner sync]}]
  {:learner/account                {:account/exists?          (:account/exists? sync)
                                    :account/join-link        (:account/join-link sync)
                                    :account/on-device-joined (:account/on-device-joined sync)
                                    :account/sync!            (:account/sync! sync)}
   :learner/active-collection      (partial active-collection learner)
   :learner/change!                (partial change! learner)
   :learner/fresh-memory           (partial fresh-memory learner)
   :learner/initialized            initialized
   :learner/insert!                (partial insert! learner)
   :learner/memory                 (partial current-memory learner)
   :learner/on-change              (partial on-change learner)
   :learner/set-active-collection! (partial set-active-collection! learner)
   :learner/status                 (partial status learner)})


(defn ^:async start!
  "Starts the learner's data. The incoming credential is handled before the databases open and their background migrations start; memory is read
   and followed without awaiting, and sync passes wait for the snapshot check (ADR-0018). Resolves with the
   capabilities."
  [{:keys [browser world]}]
  (let [store (atom {:learner/memory memory/empty-memory})
        _ (databases/persist!)
        _ (await (sync/check-incoming-auth! nil))
        databases (await (databases/open-pouch!))
        _ (databases/run-background-migrations!)
        checked (loader/start-reading! databases store)
        _ (loader/start! databases store)
        replication (await (sync/start! {:browser         browser
                                         :databases       databases
                                         :passes-wait-for checked}))
        learner (new-learner {:databases databases :store store :world world})
        initialized (initialize learner (:first-pass replication) (js/AbortController.))]
    (capabilities {:initialized initialized
                   :learner     learner
                   :sync        replication})))

(ns adapters.learner.loader
  "Loads the learner's data from user-db into memory, and keeps memory
   following its change feed (ADR-0016, ADR-0017, ADR-0020). A start takes
   memory from a snapshot when one passes its checks, and catches up from
   its feed position (ADR-0018). What memory is, and how documents change
   it, is `adapters.learner.memory`; the snapshot's format is
   `adapters.learner.snapshot`."
  (:require
   [adapters.learner.documents :as documents]
   [adapters.learner.memory :as memory]
   [adapters.learner.snapshot :as snapshot]
   [db.pouch :as dbs]
   [instrumentation :as instrumentation]
   [lambdaisland.glogi :as log]))


(def ^:private db-key
  "The database memory is read from. Every document type memory keeps
   lives in user-db (`adapters.learner.documents`); device-db holds the
   task queue, the identity and the migration records, and memory neither
   reads nor follows it (ADR-0020)."
  :user/db)


(defn- held?
  "Whether user-db still holds the change at `stored`, the position a
   snapshot stored, given `at`, where its feed stands now. A feed that
   stands at the stored position holds it; one that stands behind it does
   not; otherwise user-db is asked (`db.pouch/holds-position?`)."
  [dbs stored at]
  (cond
    (= stored at)                             (js/Promise.resolve true)
    (and stored (<= (:seq stored) (:seq at))) (dbs/holds-position? dbs db-key stored)
    :else                                     (js/Promise.resolve false)))


(def ^:private kept-types
  "The document types memory keeps; the load reads no other."
  (set (map :type documents/schemas)))


(defn- read-all
  "Reads every document of user-db that memory keeps."
  [dbs]
  (dbs/read-docs dbs db-key {:types kept-types}))


(def ^:private ingest-page
  "How many documents, entries or changes memory takes in one task while it
   loads."
  500)


(defn- ^:async loaded
  "Memory built from `items` by `add`, `memory/with-docs` or
   `memory/with-entries`, a page at a time with a task between pages, so
   that the splash keeps painting while it is built."
  [add items]
  (loop [memory memory/empty-memory
         pages  (partition-all ingest-page items)]
    (if-let [[page & more] (seq pages)]
      (let [memory (add memory page)]
        (await (dbs/next-task))
        (recur memory more))
      memory)))


(defn- ^:async caught-up
  "`memory`, built from what user-db held at the feed position `since`,
   with what it stored after it, a page at a time, and with the position
   after that. A full page means there may be more; the next one is read
   in a task of its own."
  [dbs memory since]
  (loop [memory  (memory/with-position memory since)
         changes (await (dbs/read-changes dbs db-key (:seq since) ingest-page))]
    (let [last-position (:position (peek changes))
          memory        (cond-> memory
                          (seq changes) (memory/with-changes (mapv :doc changes) last-position))]
      (if (< (count changes) ingest-page)
        memory
        (do (await (dbs/next-task))
            (recur memory (await (dbs/read-changes dbs db-key (:seq last-position) ingest-page))))))))


(defonce ^:private followers
  ;; For each store, the catch-up of the change feed memory follows.
  (js/WeakMap.))


(defn ^:async catch-up!
  "Applies to the memory in `store` whatever user-db stored that its
   change feed has not brought: one read. Resolves once memory has it; at
   once when memory is not loaded yet."
  [store]
  (when-let [catch-up (.get followers store)]
    (await (catch-up))))


(defn- on-visibility!
  "Calls `shown` each time the page becomes visible again, and `hidden`
   each time it goes to the background. Returns a function that stops it.
   Where there is no document, as in Node, it does nothing."
  [shown hidden]
  (if (exists? js/document)
    (let [listener #(if (= "visible" (.-visibilityState js/document)) (shown) (hidden))]
      (.addEventListener js/document "visibilitychange" listener)
      #(.removeEventListener js/document "visibilitychange" listener))
    (fn [])))


(defn- skip-interpolation
  "Marks what is passed as an effect's argument, so that Nexus hands it over
   as it is instead of walking all of it for placeholders."
  [x]
  (with-meta x {:nexus/skip-interpolation true}))


(defn- ^:async load-once
  "Reads memory from user-db once: it notes where the change feed stands,
   reads every document, and then reads what user-db stored meanwhile.
   Resolves with that memory, and rejects when any step fails."
  [dbs]
  (let [noted  (await (dbs/feed-position dbs db-key))
        memory (await (loaded memory/with-docs (await (read-all dbs))))]
    (await (caught-up dbs memory noted))))


(defn- ^:async restored
  "Memory from the snapshot `{:header :body}`: its entries, taken again,
   with what user-db stored after its feed position. Resolves with
   `{:memory :stored}`, that memory and the snapshot's feed position.
   Rejects when the snapshot does not match its checksum, or when any step
   fails."
  [dbs snapshot]
  (let [stored (:position (:header snapshot))
        memory (await (loaded memory/with-entries (snapshot/decoded snapshot)))]
    {:memory (await (caught-up dbs memory stored))
     :stored stored}))


(defn- ^:async dropped!
  "Deletes the snapshot, which cannot start memory for `reason`, and logs
   both. A failed delete is logged and does not stop the start. Resolves
   nil."
  [reason details]
  (log/info :memory/snapshot-dropped (assoc details :reason reason))
  (try
    (await (snapshot/delete!))
    (catch :default err
      (log/error :memory/snapshot-delete-failed {:error (str err)})))
  nil)


(defn ^:async checked-snapshot
  "The snapshot, `{:header :body}`, when it can start memory from user-db
   as it stands now, or nil. A snapshot that cannot is deleted
   (`adapters.learner.snapshot/refusal`). It never rejects; a failure is
   logged, the snapshot is deleted, and it resolves nil. The interval the
   development build reports as `:memory :load-ms` starts here."
  [dbs]
  (when ^boolean goog/DEBUG
    (instrumentation/memory-start!))
  (try
    (when-let [{:keys [header] :as snapshot} (await (snapshot/read!))]
      (let [[at marked] (await (js/Promise.all #js [(dbs/feed-position dbs db-key) (dbs/marker dbs db-key)]))]
        (if-let [reason (snapshot/refusal header at marked (await (held? dbs (:position header) at)))]
          (await (dropped! reason {}))
          snapshot)))
    (catch :default err
      (await (dropped! :unreadable {:error (str err)})))))


(defn- ^:async from-snapshot
  "What `restored` resolves with, or nil when `snapshot` cannot be taken;
   that snapshot is then deleted. It starts in a task of its own, so that
   whoever waits for the snapshot check goes on before the snapshot is
   decoded."
  [dbs snapshot]
  (await (dbs/next-task))
  (try
    (await (restored dbs snapshot))
    (catch :default err
      (await (dropped! (:reason (ex-data err) :unreadable) {:error (str err)})))))


(defn- ^:async read-memory
  "Reads memory from `snapshot`, the one `checked-snapshot` resolved with,
   when there is one and it can be taken. Otherwise it reads memory from
   user-db (`load-once`), once. Resolves with `{:memory :stored}`: memory
   with everything stored up to then, its feed position included, and the
   feed position of the snapshot the cache holds, or nil. When the read
   from user-db fails, it logs the failure, marks the store
   `:learner/unreadable?`, so that the shell asks for a reload, and
   resolves nil. Nothing reads again: a reload starts the read anew."
  [dbs store snapshot]
  (let [read (or (when snapshot (await (from-snapshot dbs snapshot)))
                 (try
                   {:memory (await (load-once dbs))
                    :stored nil}
                   (catch :default err
                     (log/error :memory/read-failed {:error (str err)})
                     (swap! store assoc :learner/unreadable? true)
                     nil)))]
    (when (and read ^boolean goog/DEBUG)
      (instrumentation/memory-ready! (if (:stored read) :snapshot :databases)))
    read))


(defonce ^:private reads
  ;; For each store, the read `start-reading!` began and `start!` has not
  ;; taken yet.
  (js/WeakMap.))


(defn start-reading!
  "Starts reading memory for `store` from user-db, in `dbs`: it checks
   the snapshot (`checked-snapshot`), then reads memory from the snapshot
   or from user-db. It needs no dispatch, so it can start as soon as
   the databases are open; `start!` hands what it reads over. Returns a
   promise that resolves with nil once the snapshot check is done."
  [dbs store]
  (let [checked (checked-snapshot dbs)]
    (.set reads store (.then checked #(read-memory dbs store %)))
    (.then checked (constantly nil))))


(defn- snapshot-writer
  "A function that writes the memory in `store` as the snapshot, with
   user-db's marker, which it reads once. It writes nothing when memory's
   feed position is that of the snapshot last written or read,
   `stored` at first, or once `stopped?` holds true. Writes run one after
   another, so an older memory is never stored over a newer one. A failed
   write is logged."
  [dbs store stored stopped?]
  (let [last-stored (volatile! stored)
        marked      (volatile! nil)
        queue       (volatile! (js/Promise.resolve))
        write       (fn ^:async write []
                      (let [memory (:learner/memory @store)
                            at     (memory/position memory)]
                        (when-not (or @stopped? (= at @last-stored))
                          (try
                            (when-not @marked
                              (vreset! marked (await (dbs/marker dbs db-key))))
                            (when-not @stopped?
                              (await (snapshot/write! memory @marked))
                              (vreset! last-stored at))
                            (catch :default err
                              (log/warn :memory/snapshot-write-failed {:error (str err)}))))))]
    (fn write! []
      (vswap! queue #(.then % write)))))


(defn ^:async start!
  "Hands memory over to the app once the read `start-reading!` began for
   `store` resolves, and keeps memory following user-db. Without such
   a read, it begins one. The read is taken, so it is not kept for the
   life of the page. Memory reaches the app through two effects:

   - `:effect/memory-loaded` with memory built from everything user-db
     holds;
   - `:effect/memory-changed` with each batch user-db stores after that —
     this app's own writes, a replication, another tab — and the position
     of its last change.

   It follows the change feed from memory's own position. Each time the
   page becomes visible again it catches up (`catch-up!`), since a live feed
   can drop a change unreported. It writes the snapshot once memory is
   handed over, in a task of its own, and each time the page goes to the
   background. Once memory is handed over, resolves with a function that
   stops following. When the read failed (`read-memory`), it hands nothing
   over, follows nothing and resolves nil."
  [dbs store dispatch]
  (when-not (.has reads store)
    (start-reading! dbs store))
  (let [read (.get reads store)
        _ (.delete reads store)]
    (when-let [{:keys [memory stored]} (await read)]
      (let [feed     (dbs/follow-changes dbs
                                         db-key
                                         (:seq (memory/position memory))
                                         (fn [docs position]
                                           (dispatch [[:effect/memory-changed (skip-interpolation docs) position]])))
            stopped? (volatile! false)
            write!   (snapshot-writer dbs store stored stopped?)]
        (.set followers store (:catch-up! feed))
        (dispatch [[:effect/memory-loaded (skip-interpolation memory)]])
        (.then (dbs/next-task) write!)
        (let [stop-visibility (on-visibility! #(-> (catch-up! store)
                                                   (.catch (fn [err]
                                                             (log/error :memory/catch-up-failed {:error (str err)}))))
                                              write!)]
          (fn stop []
            (vreset! stopped? true)
            (stop-visibility)
            (.delete followers store)
            ((:stop! feed))))))))

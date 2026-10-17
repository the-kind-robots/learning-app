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
   [browser :as browser]
   [db.pouch :as pouch]
   [instrumentation :as instrumentation]
   [lambdaisland.glogi :as log]))


(def ^:private db-key
  "The database memory is read from. Every document type memory keeps
   lives in user-db (`adapters.learner.documents`); device-db holds the
   identity and the migration records, and memory neither
   reads nor follows it (ADR-0020)."
  :user/db)


(defn- read-all
  "Reads every document of user-db that memory keeps."
  [databases]
  ;; The document types memory keeps; the load reads no other.
  (pouch/read-docs databases db-key {:types (set (map :type documents/schemas))}))


(def ^:private ingest-page
  "How many changes memory takes in one task while it catches up."
  500)


(def ^:private slice-ms
  "How long memory is built before the page gets a turn."
  12)


(defn- ^:async loaded
  "Memory built from the documents `docs`, in slices of about `slice-ms`
   with a task between them, so that the splash keeps painting while it is
   built."
  [docs]
  (loop [memory memory/empty-memory
         pages  (partition-all 100 docs)
         began  (js/performance.now)]
    (if-let [[page & more] (seq pages)]
      (let [memory (memory/with-docs memory page)]
        (if (< (- (js/performance.now) began) slice-ms)
          (recur memory more began)
          (do (await (browser/yield))
              (recur memory more (js/performance.now)))))
      memory)))


(defn- ^:async caught-up
  "`memory`, built from what user-db held at the feed position `since`,
   with what it stored after it, a page at a time, and with the position
   after that. A full page means there may be more; the next one is read
   in a task of its own."
  [databases memory since]
  (loop [memory  (memory/with-position memory since)
         changes (await (pouch/read-changes databases db-key (:seq since) ingest-page))]
    (let [last-position (:position (peek changes))
          memory        (cond-> memory
                          (seq changes) (memory/with-changes (mapv :doc changes) last-position))]
      (if (< (count changes) ingest-page)
        memory
        (do (await (browser/yield))
            (recur memory (await (pouch/read-changes databases db-key (:seq last-position) ingest-page))))))))


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


(defn- ^:async load-once
  "Reads memory from user-db once: it notes where the change feed stands,
   reads every document, and then reads what user-db stored meanwhile.
   Resolves with that memory, and rejects when any step fails."
  [databases]
  (let [noted  (await (pouch/feed-position databases db-key))
        memory (await (loaded (await (read-all databases))))]
    (await (caught-up databases memory noted))))


(defn- ^:async restored
  "Memory from the snapshot `{:header :body :memory}`: its memory, a promise
   of the decoded one, with what user-db stored after its feed position.
   Resolves with `{:memory :stored}`, that memory and the snapshot's feed
   position. Rejects when the snapshot did not decode, or when any step
   fails."
  [databases snapshot]
  (let [stored (:position (:header snapshot))
        memory (memory/restored (await (:memory snapshot)))]
    {:memory (await (caught-up databases memory stored))
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


(defn- ^:async refusal
  "Why the snapshot with `header` cannot start memory from user-db now, or nil."
  [databases {:keys [position version] :as header}]
  (let [[feed-head db-id] (await (js/Promise.all #js [(pouch/feed-position databases db-key) (pouch/id databases db-key)]))]
    (cond
      (not= snapshot/format-version version) :version
      (not= (:db/id header) db-id) :db-id
      (> (:seq position) (:seq feed-head)) :position
      (not (await (pouch/holds-position? databases db-key position feed-head))) :change)))


(defn ^:async checked-snapshot
  "The snapshot, `{:header :body :memory}`, when it can start memory from
   user-db as it stands now, or nil. It decodes the snapshot in the next task,
   while user-db answers its id and the feed position; `:memory` is a
   promise of the decoded memory. A snapshot that cannot start memory is deleted
   (`refusal`). It never rejects; a failure is
   logged, the snapshot is deleted, and it resolves nil. The interval the
   development build reports as `:memory :load-ms` starts here."
  [databases]
  (when ^boolean goog/DEBUG
    (instrumentation/memory-start!))
  (try
    (when-let [{:keys [header] :as snapshot} (await (snapshot/read!))]
      (let [decoded (.then (browser/yield) #(snapshot/decoded snapshot))
            _ (.catch decoded (fn [_]))]
        (if-let [reason (await (refusal databases header))]
          (await (dropped! reason {}))
          (assoc snapshot :memory decoded))))
    (catch :default err
      (await (dropped! :unreadable {:error (str err)})))))


(defn- ^:async from-snapshot
  "What `restored` resolves with, or nil when `snapshot` cannot be taken;
   that snapshot is then deleted. It starts in a task of its own, so that
   whoever waits for the snapshot check goes on before memory is built."
  [databases snapshot]
  (await (browser/yield))
  (try
    (await (restored databases snapshot))
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
  [databases store snapshot]
  (let [read (or (when snapshot (await (from-snapshot databases snapshot)))
                 (try
                   {:memory (await (load-once databases))
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
  "Starts reading memory for `store` from user-db, in `databases`: it checks
   the snapshot (`checked-snapshot`), then reads memory from the snapshot
   or from user-db. It needs no dispatch, so it can start as soon as
   the databases are open; `start!` hands what it reads over. Returns a
   promise that resolves with nil once the snapshot check is done."
  [databases store]
  (let [checked (checked-snapshot databases)]
    (.set reads store (.then checked #(read-memory databases store %)))
    (.then checked (constantly nil))))


(defn- snapshot-writer
  "A function that writes the memory in `store` as the snapshot, with
   user-db's id, which it reads once. It writes nothing when memory's
   feed position is that of the snapshot last written or read,
   `stored` at first, or once `stopped?` holds true. Writes run one after
   another, so an older memory is never stored over a newer one. A failed
   write is logged."
  [databases store stored stopped?]
  (let [last-stored (volatile! stored)
        db-id       (volatile! nil)
        queue       (volatile! (js/Promise.resolve))
        write       (fn ^:async write []
                      (let [memory (:learner/memory @store)
                            at     (memory/position memory)]
                        (when-not (or @stopped? (= at @last-stored))
                          (try
                            (when-not @db-id
                              (vreset! db-id (await (pouch/id databases db-key))))
                            (when-not @stopped?
                              (await (snapshot/write! memory @db-id))
                              (vreset! last-stored at))
                            (catch :default err
                              (log/warn :memory/snapshot-write-failed {:error (str err)}))))))]
    (fn write! []
      (vswap! queue #(.then % write)))))


(defn ^:async start!
  "Hands memory over to the app once the read `start-reading!` began for
   `store` resolves, and keeps memory following user-db. Without such
   a read, it begins one. The read is taken, so it is not kept for the
   life of the page. Memory built from everything user-db holds goes into the
   store at once, with `:learner/loaded?`. Each batch user-db stores after
   that — this app's own writes, a replication, another tab — and the position
   of its last change go into the store's memory directly; the open screen is
   left as it is.

   It follows the change feed from memory's own position. Each time the
   page becomes visible again it catches up (`catch-up!`), since a live feed
   can drop a change unreported. It writes the snapshot once memory is
   handed over, in a task of its own, and each time the page goes to the
   background. Once memory is handed over, resolves with a function that
   stops following. When the read failed (`read-memory`), it hands nothing
   over, follows nothing and resolves nil."
  [databases store]
  (when-not (.has reads store)
    (start-reading! databases store))
  (let [read (.get reads store)
        _ (.delete reads store)]
    (when-let [{:keys [memory stored]} (await read)]
      (let [feed     (pouch/follow-changes databases
                                         db-key
                                         (:seq (memory/position memory))
                                         (fn [docs position]
                                           (swap! store update :learner/memory memory/with-changes docs position)))
            stopped? (volatile! false)
            write!   (snapshot-writer databases store stored stopped?)]
        (.set followers store (:catch-up! feed))
        (swap! store assoc :learner/memory memory :learner/loaded? true)
        (.then (browser/yield) write!)
        (let [stop-visibility (on-visibility! #(-> (catch-up! store)
                                                   (.catch (fn [err]
                                                             (log/error :memory/catch-up-failed {:error (str err)}))))
                                              write!)]
          (fn stop []
            (vreset! stopped? true)
            (stop-visibility)
            (.delete followers store)
            ((:stop! feed))))))))

(ns adapters.learner.loader
  "Loads the learner's data from the local databases into memory, and keeps
   memory following their change feeds (ADR-0016, ADR-0017). A start takes
   memory from a snapshot when one passes its checks, and catches up from
   its feed positions (ADR-0018). What memory is, and how documents change
   it, is `adapters.learner.memory`; the snapshot's format is
   `adapters.learner.snapshot`."
  (:require
   [adapters.learner.documents :as documents]
   [adapters.learner.memory :as memory]
   [adapters.learner.snapshot :as snapshot]
   [db.pouch :as dbs]
   [instrumentation :as instrumentation]
   [lambdaisland.glogi :as log]))


(def ^:private retry-ms
  "How long to wait before a failed read is tried again: each wait is the
   next one here, and after the last the last is repeated."
  [1000 2000 4000 8000 16000 30000])


(defn- ^:async retried
  "Resolves to what `read` resolves to. When `read` fails — a locked or
   evicted database, a quota error — this logs it as an error, marks the
   store so the splash says so, waits and tries again, so memory does not
   stay unloaded for the life of the page."
  [store read waits]
  (try
    (await (read))
    (catch :default err
      (let [[wait & more] waits]
        (log/error :memory/read-failed {:error (str err) :retry-ms wait})
        (swap! store assoc :learner/read-failed? true)
        (await (js/Promise. (fn [resolve] (js/setTimeout resolve wait))))
        (await (retried store read (or more [wait])))))))


(def ^:private db-keys
  "The databases memory is read from, in the order memory takes them."
  [:user/db :device/db])


(defn- ^:async in-both-dbs
  "What `f` resolves with for each database, `{db-key value}`. The
   databases are asked at once."
  [f]
  (zipmap db-keys (await (js/Promise.all (into-array (map f db-keys))))))


(defn- positions
  "Where the change feed of each database stands now
   (`db.pouch/feed-position`)."
  [dbs]
  (in-both-dbs #(dbs/feed-position dbs %)))


(defn- markers
  "The marker of each database (`db.pouch/marker`)."
  [dbs]
  (in-both-dbs #(dbs/marker dbs %)))


(defn- held
  "Whether each database still holds the change at its position in
   `stored`, `{db-key boolean}`. A database whose feed stands at the stored
   position holds it; one whose feed stands behind it does not; any other
   is asked (`db.pouch/holds-position?`)."
  [dbs stored at]
  (in-both-dbs (fn [db-key]
                 (let [position (stored db-key)]
                   (cond
                     (= position (at db-key)) true
                     (and position (<= (:seq position) (:seq (at db-key)))) (dbs/holds-position? dbs db-key position)
                     :else false)))))


(def ^:private kept-types
  "The document types memory keeps; the load reads no other."
  (set (map :type documents/schemas)))


(defn- ^:async read-all
  "Reads every document of both databases that memory keeps."
  [dbs]
  (into [] cat (vals (await (in-both-dbs #(dbs/read-docs dbs % {:types kept-types}))))))


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


(defn- ^:async caught-up-with
  "`memory` with `changes`, the first page of what the database `db-key`
   stored after memory's position for it, and with the pages after it. A
   full page means there may be more; the next one is read in a task of
   its own."
  [dbs memory db-key changes]
  (loop [memory  memory
         changes changes]
    (let [last-position (:position (peek changes))
          memory        (cond-> memory
                          (seq changes) (memory/with-changes db-key (mapv :doc changes) last-position))]
      (if (< (count changes) ingest-page)
        memory
        (do (await (dbs/next-task))
            (recur memory (await (dbs/read-changes dbs db-key (:seq last-position) ingest-page))))))))


(defn- ^:async caught-up
  "`memory`, built from what the databases held at the feed positions
   `since`, with what they stored after them, and with the positions after
   that. The first page of each database is read at once; memory takes
   user-db's changes, then device-db's."
  [dbs memory since]
  (let [first-pages (await (in-both-dbs #(dbs/read-changes dbs % (get-in since [% :seq]) ingest-page)))]
    (loop [memory (memory/with-positions memory since)
           keys   db-keys]
      (if-let [[db-key & more] (seq keys)]
        (recur (await (caught-up-with dbs memory db-key (first-pages db-key))) more)
        memory))))


(defonce ^:private followers
  ;; For each store, the catch-up of each change feed memory follows, by
  ;; database.
  (js/WeakMap.))


(defn ^:async catch-up!
  "Applies to the memory in `store` whatever the database `db-key`, or
   every database when it is left out, stored that its change feed has not
   brought: one read per database. Resolves once memory has it; at once
   when memory is not loaded yet."
  ([store]
   (await (js/Promise.all (into-array (map #(catch-up! store %) db-keys)))))
  ([store db-key]
   (when-let [catch-up (some-> (.get followers store) db-key)]
     (await (catch-up)))))


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
  "Reads memory from the databases once: it notes where each change feed
   stands, reads both databases, and then reads what they stored
   meanwhile. Resolves with that memory. A failure at any step retries all
   of it, so that a retry starts from fresh feed positions."
  [dbs]
  (let [noted  (await (positions dbs))
        memory (await (loaded memory/with-docs (await (read-all dbs))))]
    (await (caught-up dbs memory noted))))


(defn- ^:async restored
  "Memory from the snapshot `{:header :body}`: its entries, taken again,
   with what the databases stored after its feed positions. Resolves with
   `{:memory :stored}`, that memory and the snapshot's feed positions.
   Rejects when the snapshot does not match its checksum, or when any step
   fails."
  [dbs snapshot]
  (let [stored (snapshot/positions (:header snapshot))
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
  "The snapshot, `{:header :body}`, when it can start memory from the
   databases as they stand now, or nil. A snapshot that cannot is deleted
   (`adapters.learner.snapshot/refusal`). It never rejects; a failure is
   logged, the snapshot is deleted, and it resolves nil. The interval the
   development build reports as `:memory :load-ms` starts here."
  [dbs]
  (when ^boolean goog/DEBUG
    (instrumentation/memory-start!))
  (try
    (when-let [{:keys [header] :as snapshot} (await (snapshot/read!))]
      (let [stored      (snapshot/positions header)
            [at marked] (await (js/Promise.all #js [(positions dbs) (markers dbs)]))]
        (if-let [reason (snapshot/refusal header at marked (await (held dbs stored at)))]
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
   the databases (`load-once`), and reads again from the start when any
   step fails (`retried`). Resolves with `{:memory :stored}`: memory with
   everything stored up to then, its feed positions included, and the feed
   positions of the snapshot the cache holds, or nil."
  [dbs store snapshot]
  (let [read (or (when snapshot (await (from-snapshot dbs snapshot)))
                 {:memory (await (retried store #(load-once dbs) retry-ms)) :stored nil})]
    (when ^boolean goog/DEBUG
      (instrumentation/memory-ready! (if (:stored read) :snapshot :databases)))
    read))


(defonce ^:private reads
  ;; For each store, the read `start-reading!` began and `start!` has not
  ;; taken yet.
  (js/WeakMap.))


(defn start-reading!
  "Starts reading memory for `store` from the databases `dbs`: it checks
   the snapshot (`checked-snapshot`), then reads memory from the snapshot
   or from the databases. It needs no dispatch, so it can start as soon as
   the databases are open; `start!` hands what it reads over. Returns a
   promise that resolves with nil once the snapshot check is done."
  [dbs store]
  (let [checked (checked-snapshot dbs)]
    (.set reads store (.then checked #(read-memory dbs store %)))
    (.then checked (constantly nil))))


(defn- snapshot-writer
  "A function that writes the memory in `store` as the snapshot, with the
   markers of the databases, which it reads once. It writes nothing when
   memory's feed positions are those of the snapshot last written or read,
   `stored` at first, or once `stopped?` holds true. Writes run one after
   another, so an older memory is never stored over a newer one. A failed
   write is logged."
  [dbs store stored stopped?]
  (let [last-stored (volatile! stored)
        marked      (volatile! nil)
        queue       (volatile! (js/Promise.resolve))
        write       (fn ^:async write []
                      (let [memory (:learner/memory @store)
                            at     (memory/positions memory)]
                        (when-not (or @stopped? (= at @last-stored))
                          (try
                            (when-not @marked
                              (vreset! marked (await (markers dbs))))
                            (when-not @stopped?
                              (await (snapshot/write! memory @marked))
                              (vreset! last-stored at))
                            (catch :default err
                              (log/warn :memory/snapshot-write-failed {:error (str err)}))))))]
    (fn write! []
      (vswap! queue #(.then % write)))))


(defn ^:async start!
  "Hands memory over to the app once the read `start-reading!` began for
   `store` resolves, and keeps memory following the databases. Without such
   a read, it begins one. The read is taken, so it is not kept for the
   life of the page. Memory reaches the app through two effects:

   - `:effect/memory-loaded` with memory built from everything the
     databases hold;
   - `:effect/memory-changed` with each batch a database stores after
     that — this app's own writes, a replication, another tab — and the
     position of its last change.

   It follows each change feed from memory's own positions. Each time the
   page becomes visible again it catches up (`catch-up!`), since a live feed
   can drop a change unreported. It writes the snapshot once memory is
   handed over, in a task of its own, and each time the page goes to the
   background. Once memory is handed over, resolves with a function that
   stops following."
  [dbs store dispatch]
  (when-not (.has reads store)
    (start-reading! dbs store))
  (let [read     (.get reads store)
        _ (.delete reads store)
        {:keys [memory stored]} (await read)
        since    (memory/positions memory)
        changed! (fn [db-key]
                   (fn [docs position]
                     (dispatch [[:effect/memory-changed db-key (skip-interpolation docs) position]])))
        feeds    (into {}
                       (map (fn [db-key]
                              [db-key (dbs/follow-changes dbs db-key (get-in since [db-key :seq]) (changed! db-key))]))
                       db-keys)
        stopped? (volatile! false)
        write!   (snapshot-writer dbs store stored stopped?)]
    (.set followers store (update-vals feeds :catch-up!))
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
        (run! #((:stop! %)) (vals feeds))))))

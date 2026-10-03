(ns adapters.learner.loader
  "Loads the learner's data from the local databases into memory, and keeps
   memory following their change feeds (ADR-0016). What memory is, and how
   documents change it, is `adapters.learner.memory`."
  (:require
   [adapters.learner.documents :as documents]
   [adapters.learner.memory :as memory]
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


(defn- ^:async positions
  "Where the change feed of each database stands now."
  [dbs]
  (let [user   (dbs/update-seq dbs :user/db)
        device (dbs/update-seq dbs :device/db)]
    {:device/db (await device)
     :user/db   (await user)}))


(def ^:private kept-types
  "The document types memory keeps; the load reads no other."
  (set (map :type documents/schemas)))


(defn- ^:async read-all
  "Reads every document of both databases that memory keeps."
  [dbs]
  (let [user   (dbs/read-docs dbs :user/db {:types kept-types})
        device (dbs/read-docs dbs :device/db {:types kept-types})]
    (into (await user) (await device))))


(def ^:private ingest-page
  "How many documents memory takes in one task while it loads."
  500)


(defn- ^:async loaded
  "Memory built from `docs`, a page at a time with a task between pages, so
   that the splash keeps painting while it is built."
  [docs]
  (loop [memory memory/empty-memory
         pages  (partition-all ingest-page docs)]
    (if-let [[page & more] (seq pages)]
      (let [memory (memory/with-docs memory page)]
        (await (dbs/next-task))
        (recur memory more))
      memory)))


(defn- ^:async caught-up
  "`memory`, built from what the databases held at `since`, with what they
   stored after it, and the feed positions after that. One read per
   database."
  [dbs memory since]
  (let [user   (await (dbs/read-changes dbs :user/db (:user/db since)))
        device (await (dbs/read-changes dbs :device/db (:device/db since)))]
    {:memory (memory/with-docs memory (into (:docs user) (:docs device)))
     :since  {:device/db (:position device) :user/db (:position user)}}))


(defonce ^:private followers
  ;; For each store, the catch-up of each change feed memory follows.
  (js/WeakMap.))


(defn ^:async catch-up!
  "Applies to the memory in `store` whatever the databases stored that its
   change feeds have not brought: one read per database. Resolves once
   memory has it; at once when memory is not loaded yet."
  [store]
  (when-let [catch-ups (.get followers store)]
    (await (js/Promise.all (into-array (map #(%) catch-ups))))))


(defn- on-visible!
  "Calls `f` each time the page becomes visible again. Returns a function
   that stops it. Where there is no document, as in Node, it does nothing."
  [f]
  (if (exists? js/document)
    (let [listener #(when (= "visible" (.-visibilityState js/document)) (f))]
      (.addEventListener js/document "visibilitychange" listener)
      #(.removeEventListener js/document "visibilitychange" listener))
    (fn [])))


(defn- skip-interpolation
  "Marks what is passed as an effect's argument, so that Nexus hands it over
   as it is instead of walking all of it for placeholders."
  [x]
  (with-meta x {:nexus/skip-interpolation true}))


(defn ^:async start!
  "Loads the learner's data into memory and keeps memory following the
   databases, through two effects:

   - `:effect/memory-loaded` with memory built from everything the
     databases hold;
   - `:effect/memory-changed` with each batch the databases store after
     that: this app's own writes, a replication, another tab.

   It notes where each change feed stands, reads both databases, and then
   reads what they stored meanwhile, so memory is handed over with
   everything stored up to then. It follows each feed from there. Each time
   the page becomes visible again it catches up (`catch-up!`), since a live
   feed can drop a change unreported. Once loaded, resolves with a function
   that stops following."
  [dbs store dispatch]
  (let [noted (await (retried store #(positions dbs) retry-ms))]
    (when ^boolean goog/DEBUG
      (instrumentation/memory-start!))
    (let [{:keys [memory since]} (await (caught-up dbs
                                                   (await (loaded (await (retried store #(read-all dbs) retry-ms))))
                                                   noted))
          changed!               #(dispatch [[:effect/memory-changed (skip-interpolation %)]])
          feeds                  [(dbs/follow-changes dbs :user/db (:user/db since) changed!)
                                  (dbs/follow-changes dbs :device/db (:device/db since) changed!)]]
      (.set followers store (mapv :catch-up! feeds))
      (dispatch [[:effect/memory-loaded (skip-interpolation memory)]])
      (when ^boolean goog/DEBUG
        (instrumentation/memory-ready!))
      (let [stop-visible (on-visible! #(-> (catch-up! store)
                                           (.catch (fn [err] (log/error :memory/catch-up-failed {:error (str err)})))))]
        (fn stop []
          (stop-visible)
          (.delete followers store)
          (run! #((:stop! %)) feeds))))))

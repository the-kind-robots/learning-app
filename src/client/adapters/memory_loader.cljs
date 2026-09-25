(ns adapters.memory-loader
  "Loads the learner's data from the local databases and keeps it following
   them (ADR-0016). It hands documents to three effects and holds nothing
   itself: what memory is, and how documents change it, is `adapters.memory`."
  (:require
   [adapters.collections :as collections]
   [db.pouch :as dbs]
   [domain.vocabulary :as vocabulary]
   [instrumentation :as instrumentation]
   [lambdaisland.glogi :as log]))


(def ^:private retry-ms
  "How long to wait before a failed read is tried again: each wait is the
   next one here, and after the last the last is repeated."
  [1000 2000 4000 8000 16000 30000])


(defn- ^:async retried
  "Resolves to what `read` resolves to. When `read` fails — a locked or
   evicted database, a quota error — this waits and tries again, so memory
   does not stay unloaded for the life of the page."
  [read waits]
  (try
    (await (read))
    (catch :default err
      (let [[wait & more] waits]
        (log/warn :memory/read-failed {:error (str err) :retry-ms wait})
        (await (js/Promise. (fn [resolve] (js/setTimeout resolve wait))))
        (await (retried read (or more [wait])))))))


(defn- ^:async basics
  "Reads the words and the collections, which opening the app and adding a
   word need. The words are read as the one id range they are stored under."
  [dbs]
  (let [words (dbs/read-docs dbs
                             :user/db
                             {:end   (str vocabulary/id-prefix "\ufff0")
                              :start vocabulary/id-prefix})
        colls (dbs/find-all dbs collections/schema {})]
    (into (await words) (:docs (await colls)))))


(defn- ^:async the-rest
  "Reads everything else: user-db on either side of the words' id range,
   which holds the reviews, and device-db, which holds the examples. The
   collections are read again here; memory already holds them, so they
   change nothing."
  [dbs]
  (let [before (dbs/read-docs dbs :user/db {:end vocabulary/id-prefix})
        after  (dbs/read-docs dbs :user/db {:start (str vocabulary/id-prefix "\ufff0")})
        device (dbs/read-docs dbs :device/db {})]
    (-> (await before) (into (await after)) (into (await device)))))


(def ^:private ingest-page
  "How many documents memory takes in one task while the rest loads."
  500)


(defn- skip-interpolation
  "Marks documents passed as an effect's argument, so that Nexus hands them
   over as they are instead of walking every one for placeholders."
  [docs]
  (with-meta docs {:nexus/skip-interpolation true}))


(defn- ^:async handed-over!
  "Dispatches each of `pages` as a change to memory, one task each."
  [dispatch pages]
  (when-let [[page & more] (seq pages)]
    (dispatch [[:effect/memory-changed (skip-interpolation (vec page))]])
    (await (dbs/next-task))
    (await (handed-over! dispatch more))))


(defn ^:async start!
  "Loads the learner's data into memory and keeps it following the
   databases, through three effects:

   - `:effect/memory-loaded-basic` with the words and collections;
   - `:effect/memory-loaded-full` with everything else;
   - `:effect/memory-changed` with each batch written afterwards: this app's
     writes as soon as PouchDB has accepted them, and the writes of another
     tab or of a replication, which the change feed brings.

   The feeds start from where the databases stood before anything was read,
   so a write that lands during the load also arrives through them. If
   memory already holds that revision, it changes nothing. Once loaded, this
   resolves with a function that stops following."
  [dbs dispatch]
  (let [[user-seq device-seq] (await (js/Promise.all #js [(dbs/update-seq dbs :user/db)
                                                          (dbs/update-seq dbs :device/db)]))
        changed! #(dispatch [[:effect/memory-changed (skip-interpolation %)]])]
    (dbs/listen-to-writes! dbs changed!)
    (when ^boolean goog/DEBUG
      (instrumentation/memory-start!))
    (dispatch [[:effect/memory-loaded-basic (skip-interpolation (await (retried #(basics dbs) retry-ms)))]])
    ;; The rest arrives while the learner may already be typing, so it is
    ;; taken in a page at a time with a task between pages: one long task
    ;; over every review would hold up the keystroke that lands during it.
    (let [pages (partition-all ingest-page (await (retried #(the-rest dbs) retry-ms)))]
      (await (handed-over! dispatch (butlast pages)))
      (dispatch [[:effect/memory-loaded-full (skip-interpolation (vec (last pages)))]]))
    (when ^boolean goog/DEBUG
      (instrumentation/memory-ready!))
    (let [stop-user   (dbs/follow-changes dbs :user/db user-seq changed!)
          stop-device (dbs/follow-changes dbs :device/db device-seq changed!)]
      (fn stop []
        (dbs/listen-to-writes! dbs nil)
        (stop-user)
        (stop-device)))))

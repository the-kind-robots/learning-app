(ns db.pouch
  "Storage engine over PouchDB: documents, databases, indexes, change feeds
   and replication. Knows no document type of its own — every call carries the
   `schema` of the aggregate it serves, a value the owning adapter declares:

     {:type \"...\"     ; stored as the document's :type
      :db   :user/db}  ; the database the type lives in

   `init!` opens the databases and builds no index; nothing queries one."
  (:refer-clojure :exclude [get])
  (:require
   [browser :as browser]
   [clojure.string :as str]
   [db :as db]
   [db-migrations :as db-migrations]
   [lambdaisland.glogi :as log]
   [userdb :as userdb]))


(defn- user-db
  []
  (db/use "user-db"))


(defn- device-db
  []
  (db/use "device-db"))


(def ^:private db->remote-name
  "Which databases have a copy on the server, and what that copy is called
   there. device-db is absent on purpose: it holds what belongs to this device
   alone and has nowhere to replicate to."
  {:user/db userdb/db-name})


(defn on-change
  "Calls f after every local write to db-key. Returns a function that stops
   watching."
  [dbs db-key f]
  (let [feed (.changes ^js (db-key dbs) #js {:live true :since "now"})]
    (.on ^js feed "change" f)
    #(.cancel ^js feed)))


(defn- design-doc?
  [^js doc]
  (str/starts-with? (.-_id doc) "_design/"))


(def ^:private page-size
  "How many documents one transaction reads. Each page is its own read, so a
   write that waits for the database can run between two of them."
  1000)


(def ^:private retry-ms
  "How long `retried` waits before it tries again: each wait is the next
   one here, and after the last the last is repeated."
  [1000 2000 4000 8000 16000 30000])


(defn- ^:async retried-after
  [operation failed waits]
  (let [[wait & more] waits
        result        (try
                        {:value (await (operation))}
                        (catch :default err
                          {:error err}))]
    (if (contains? result :value)
      (:value result)
      (do (failed (:error result) wait)
          (await (js/Promise. (fn [resolve] (js/setTimeout resolve wait))))
          (await (retried-after operation failed (or more [wait])))))))


(defn retried
  "Resolves with what `operation`, a function returning a promise, resolves
   with. When the promise rejects — a locked or evicted database, a quota
   error — this calls `failed` with the error and the wait before the next
   try, waits, and calls `operation` again, until it succeeds. The waits
   grow as `retry-ms` says. It never rejects."
  [operation failed]
  (retried-after operation failed retry-ms))


(defn- ^:async pages
  "Reads every document of `db` with an id from `:start` to `:end`, a page
   at a time, with a task between pages. With `:types`, a set, it keeps only
   documents whose `type` is in it, and converts no other."
  [^js db {:keys [end start types]}]
  (loop [docs  []
         after nil]
    (let [options  (cond-> #js {:include_docs true :limit page-size}
                     start (doto (aset "startkey" start))
                     end   (doto (aset "endkey" end))
                     ;; The next page starts right after the last id read,
                     ;; whether or not that document is still there.
                     after (doto (aset "startkey" (str after "\u0000"))))
          ^js page (await (.allDocs db options))
          rows     (.-rows page)
          docs     (into docs
                         (comp (map #(.-doc ^js %))
                               (filter #(or (nil? types) (contains? types (.-type ^js %))))
                               (map db/couch->clj))
                         rows)]
      (if (< (.-length rows) page-size)
        docs
        (do (await (browser/yield))
            (recur docs (.-id ^js (aget rows (dec (.-length rows))))))))))


(defn ^:async read-page
  "One page of the documents of `db-key` with an id after `:after`, or from
   the first when it is nil: `{:docs :next}`. The page reads
   `:limit` ids. `:docs` are the documents among them, only those of the
   types in `:types` when it is given. `:next` is the id to read the next
   page after, or nil when this page was the last."
  [dbs db-key {:keys [after limit types]}]
  (let [options  (cond-> #js {:include_docs true :limit limit}
                   after (doto (aset "startkey" (str after "\u0000"))))
        ^js page (await (.allDocs ^js (db-key dbs) options))
        rows     (.-rows page)]
    {:docs (into []
                 (comp (map #(.-doc ^js %))
                       (filter #(or (nil? types) (contains? types (.-type ^js %))))
                       (map db/couch->clj))
                 rows)
     :next (when (= limit (.-length rows))
             (.-id ^js (aget rows (dec limit))))}))


(defn read-docs
  "Reads the documents of `db-key` with an id from `:start` to `:end`;
   either bound may be left out. With `:types`, only documents of those
   types. It reads a page at a time, with a task between pages."
  [dbs db-key range]
  (pages (db-key dbs) range))


(defn- js-change->position
  "The feed position of a change PouchDB reports: `{:id :rev :seq}`, its
   sequence and the id and winning revision of its document. The id and
   revision tell this change apart from another one that a database stores
   at the same sequence after it lost its last writes."
  [^js change]
  {:id (.-id change) :rev (.-rev (aget (.-changes change) 0)) :seq (.-seq change)})


(defn ^:async feed-position
  "Where the change feed of `db-key` stands now: the position of its last
   change, or `{:seq 0}` when it has none. When this is read before the
   documents, a feed followed from here misses nothing written in
   between."
  [dbs db-key]
  (let [^js answer (await (.changes ^js (db-key dbs) #js {:descending true :limit 1}))]
    (if-let [change (aget (.-results answer) 0)]
      (js-change->position change)
      {:seq 0})))


(defn ^:async holds-position?
  "Whether `db-key` still holds the change at `position`. When a change sits
   at that sequence, it must be the same document at the same revision.
   When none does any more, because a later change of the document
   replaced it, the database must still know that revision. A database
   that lost its last writes and stored other changes under their
   sequences fails both."
  [dbs db-key {:keys [id rev seq] :as position}]
  (let [^js db (db-key dbs)]
    (or (zero? seq)
        (let [^js change (aget (.-results (await (.changes db #js {:limit 1 :since (dec seq)}))) 0)]
          (if (and change (= seq (.-seq change)))
            (= position (js-change->position change))
            (empty? (js-keys (await (.revsDiff db (js-obj id #js [rev]))))))))))


(defn- js-change->change
  "A change PouchDB reports with its document, as `{:doc :position}`."
  [^js change]
  {:doc (db/couch->clj (.-doc change)) :position (js-change->position change)})


(defn ^:async read-changes
  "Reads what `db-key` stored after the sequence `since`: at most `limit`
   changes, or all of them when `limit` is nil. Resolves with each change
   in the order stored, as `{:doc :position}`: its document — a deleted one
   as `{:_id .. :_rev .. :_deleted true}` — and its feed position."
  [dbs db-key since limit]
  (let [options    (cond-> #js {:include_docs true :since since}
                     limit (doto (aset "limit" limit)))
        ^js answer (await (.changes ^js (db-key dbs) options))]
    (mapv js-change->change (.-results answer))))


(defn follow-changes
  "Calls `f` with the documents `db-key` stores after the sequence `since`,
   in the order it stores them, and the position of the last of them: one
   call per batch of changes that arrive together. A deleted document
   arrives as `{:_id .. :_rev .. :_deleted true}`.

   PouchDB can drop a change from a live feed without reporting it, and a
   started feed reports no failure. So this also returns a way to catch up:
   `:catch-up!` reads once what was stored after the last change handed to
   `f`, hands it to `f`, and resolves once it has. Calls made while a
   catch-up reads share one more read after it, which starts once that one
   is done. `:stop!` stops following.

   Only changes after the last change handed over reach `f`. A catch-up and
   the live feed read the same log apart, and either can answer first; a
   change at or below that sequence is one `f` has had, or one a later
   change of the same document has replaced."
  [dbs db-key since f]
  (let [handed-seq (volatile! since)
        batch      (volatile! [])
        stopped?   (volatile! false)
        reading    (volatile! nil)
        next-read  (volatile! nil)
        handed!    (fn [changes]
                     (let [fresh (filterv #(> (:seq (:position %)) @handed-seq) changes)]
                       (when (and (seq fresh) (not @stopped?))
                         (vreset! handed-seq (:seq (:position (peek fresh))))
                         (f (mapv :doc fresh) (:position (peek fresh))))))
        flush!     (fn []
                     (let [changes @batch]
                       (vreset! batch [])
                       (handed! changes)))
        read!      (fn []
                     (vreset! reading
                              (-> (read-changes dbs db-key @handed-seq nil)
                                  (.then handed!)
                                  (.finally #(vreset! reading nil)))))
        catch-up!  (fn catch-up! []
                     (cond
                       @next-read @next-read
                       @reading   (vreset! next-read
                                           (-> @reading
                                               (.catch (fn [_]))
                                               (.then (fn []
                                                        (vreset! next-read nil)
                                                        (read!)))))
                       :else      (read!)))
        feed       (doto ^js (.changes ^js (db-key dbs) #js {:include_docs true :live true :since since})
                     (.on "change"
                          (fn [^js change]
                            ;; A batch is what arrives before the next task:
                            ;; PouchDB emits the changes of one write, such as
                            ;; a replicated batch or a bulk write, one after
                            ;; another. The first change of a batch schedules
                            ;; its flush. A change already handed over is not
                            ;; converted at all.
                            (when (> (.-seq change) @handed-seq)
                              (when (empty? @batch)
                                (js/setTimeout flush! 0))
                              (vswap! batch conj (js-change->change change)))))
                     (.on "error"
                          (fn [err]
                            (log/error :db/follow-failed {:db db-key :error (str err)}))))]
    {:catch-up! catch-up!
     :stop!     (fn stop! []
                  (vreset! stopped? true)
                  (.cancel feed))}))


(def marker-id
  "The local document that holds a database's marker. Local documents do
   not replicate and do not move the change feed."
  "_local/database-marker")


(defn ^:async marker
  "The marker of `db-key`: a random id that tells this database apart from
   one that was re-created or cleared under the same name. It is created
   the first time it is asked for. When another tab creates it at the same
   moment, the one stored first is the marker."
  [dbs db-key]
  (let [db   (db-key dbs)
        read (fn ^:async read [] (:marker (await (db/get db marker-id))))]
    (or (await (read))
        (let [made (str (random-uuid))]
          (if (await (db/insert-if-absent db {:_id marker-id :marker made}))
            made
            (await (read)))))))


(defn user-doc?
  "Replication filter: design documents stay on the device. CouchDB builds
   an index for every design document it receives, and the server never
   queries user-db through one."
  [doc]
  (not (design-doc? doc)))


(defn- docs-written
  [^js direction]
  (or (some-> direction .-docs_written) 0))


(defn- written-revisions
  "The `[id rev]` pairs one batch wrote."
  [^js change]
  (into #{}
        (map (fn [^js doc] [(.-_id doc) (.-_rev doc)]))
        (some-> change .-docs)))


(defn change-revision
  "The `[id rev]` pair one change-feed event reports, comparable with a pass's
   `:pulled-revs`."
  [^js change]
  [(.-id change) (some-> change .-changes (aget 0) .-rev)])


(defn sync-once!
  "Runs one bidirectional replication pass of db-key against the account's copy
   on the server. Resolves with what the pass did — `{:pulled n :pushed n
   :pulled-revs #{[id rev]}}`, documents written on each side and the exact
   revisions the pull wrote here, which is how the change feed tells the
   pull's own writes from local ones — and never rejects: a failed pass
   resolves nil, so a caller can fire it on a trigger without guarding every
   one.

   The revisions come from the pass's own `change` events, where PouchDB
   hands over the batch it has just written; the `complete` result carries
   the counters alone."
  [dbs db-key account-id]
  (let [pulled-revs (atom #{})
        remote      (str (.. js/globalThis -location -origin)
                         "/db/"
                         ((db->remote-name db-key) account-id))]
    (js/Promise.
     (fn [resolve _reject]
       (doto (db/sync (db-key dbs) {:filter user-doc? :live false :remote-url remote})
         (.on "change"
              (fn [^js info]
                (when (= "pull" (.-direction info))
                  (swap! pulled-revs into (written-revisions (.-change info))))))
         (.on "complete"
              (fn [^js info]
                (resolve {:pulled      (docs-written (some-> info .-pull))
                          :pulled-revs @pulled-revs
                          :pushed      (docs-written (some-> info .-push))})))
         (.on "error"
              (fn [err]
                (log/warn :db/sync-failed {:db db-key :error (str err)})
                (resolve nil))))))))


(defn- database
  [dbs schema]
  ((:db schema) dbs))


(defn ^:async insert
  "Writes doc as one of `schema`'s type into the database that holds it.
   Resolves with the document as written, at the revision PouchDB gave it."
  [dbs schema doc]
  (let [doc (assoc doc :type (:type schema))
        {:keys [id rev]} (await (db/insert (database dbs schema) doc))]
    (assoc doc :_id id :_rev rev)))


(defn ^:async insert-all-if-absent
  "Writes each of `docs` as one of `schema`'s type under its `:_id`, in one
   bulk write, unless the database holds a document under that id. Resolves
   with the set of ids among them that the database holds afterwards: the
   ones written and the ones it held already. An id whose write failed for
   any other reason is not in it."
  [dbs schema docs]
  (let [results (await (db/bulk-docs (database dbs schema)
                                     (mapv #(assoc % :type (:type schema)) docs)))]
    (into #{}
          ;; A refused document comes back as PouchDB's error object, which
          ;; the conversion to Clojure leaves as it is.
          (keep (fn [row]
                  (cond
                    (map? row) (when (:ok row) (:id row))
                    (db/conflict? row) (.-id ^js row))))
          results)))


(defn ^:async bulk-docs
  "Writes docs into `schema`'s database in one call. The docs carry their own
   :type: a bulk write moves documents that were read, tombstoned or updated,
   and those may belong to several types of the same database. Resolves with
   the documents PouchDB accepted, each at its new revision; PouchDB refuses
   a document whose revision it does not hold and writes the rest."
  [dbs schema docs]
  (let [results (await (db/bulk-docs (database dbs schema) docs))]
    (into []
          (keep (fn [[doc {:keys [ok id rev]}]]
                  (when ok (assoc doc :_id id :_rev rev))))
          (map vector docs results))))


(defn get
  "The document with `id` from `schema`'s database, or nil."
  [dbs schema id]
  (db/get (database dbs schema) id))


(def ^:private ids-page
  "How many documents a read by id converts in one task."
  500)


(defn ^:async read-ids
  "The documents of `db-key` under `ids`, as PouchDB holds them now: the
   winner of each, and for a deleted one `{:_id .. :_rev .. :_deleted
   true}`. An id with no document is left out. It reads a page of ids at a
   time, with a task between pages."
  [dbs db-key ids]
  (loop [docs  []
         pages (partition-all ids-page ids)]
    (if-let [[page & more] (seq pages)]
      (let [^js answer (await (.allDocs ^js (db-key dbs) #js {:include_docs true :keys (into-array page)}))
            docs       (into docs
                             (keep (fn [^js row]
                                     (cond
                                       (.-doc row)
                                       (db/couch->clj (.-doc row))

                                       (some-> row .-value .-deleted)
                                       {:_deleted true :_id (.-id row) :_rev (.. row -value -rev)})))
                             (.-rows answer))]
        (when more
          (await (browser/yield)))
        (recur docs more))
      docs)))


(defn ^:async init!
  "Opens the databases once their migrations have run. It builds no index:
   bringing an index up to date reads every document written since it was
   last used, and memory waits for the databases."
  []
  (await (db-migrations/ensure-migrated!))
  {:device/db (device-db)
   :user/db   (user-db)})


(defn ^:async write-latest!
  "Reads the document `id` of `schema`'s type — the winning revision — and
   puts what `change` makes of it over that revision. `change` takes the
   document, or nil when there is none, and returns the document to write,
   or nil to write nothing. When the put is refused with a conflict, this
   reads and puts once more. Resolves with `{:stored :written}`, the
   document read and the document written, or with nil when nothing was
   written."
  [dbs schema id change]
  (let [attempt (fn ^:async attempt []
                  (let [stored (await (get dbs schema id))]
                    (when-let [doc (change stored)]
                      (let [doc (cond-> (-> doc (dissoc :_rev) (assoc :_id id))
                                  stored (assoc :_rev (:_rev stored)))]
                        {:stored  stored
                         :written (await (insert dbs schema doc))}))))]
    (try
      (await (attempt))
      (catch :default err
        (if (db/conflict? err)
          (await (attempt))
          (throw err))))))

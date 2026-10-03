(ns db.pouch
  "Storage engine over PouchDB: documents, databases, indexes, change feeds
   and replication. Knows no document type of its own — every call carries the
   `schema` of the aggregate it serves, a value the owning adapter declares:

     {:type    \"...\"            ; stored as the document's :type
      :db      :user/db          ; the database the type lives in
      :indexes [{:name \"...\" :fields [...]}]}  ; optional

   `init!` takes every schema the app declares and gives each database its
   indexes."
  (:refer-clojure :exclude [get find remove])
  (:require
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


(defn ^:async next-task
  "Resolves in a task of its own, after whatever is queued now, such as a
   paint or a keystroke."
  []
  (await (js/Promise. (fn [resolve] (js/setTimeout resolve 0)))))


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
        (do (await (next-task))
            (recur docs (.-id ^js (aget rows (dec (.-length rows))))))))))


(defn read-docs
  "Reads the documents of `db-key` with an id from `:start` to `:end`;
   either bound may be left out. With `:types`, only documents of those
   types. It reads a page at a time, with a task between pages."
  [dbs db-key range]
  (pages (db-key dbs) range))


(defn ^:async update-seq
  "Where the change feed of `db-key` stands now. When this is read before
   the documents, a feed followed from here misses nothing written in
   between."
  [dbs db-key]
  (let [^js info (await (.info ^js (db-key dbs)))]
    (.-update_seq info)))


(defn ^:async read-changes
  "Reads once what `db-key` stored after `since`. Resolves with `{:docs
   [...] :position seq}`: the documents that changed, in the order stored —
   a deleted one as `{:_id .. :_rev .. :_deleted true}` — and the feed's
   sequence after them."
  [dbs db-key since]
  (let [^js answer (await (.changes ^js (db-key dbs) #js {:include_docs true :since since}))]
    {:docs     (mapv #(db/couch->clj (.-doc ^js %)) (.-results answer))
     :position (.-last_seq answer)}))


(defn follow-changes
  "Calls `f` with the documents `db-key` stores after `since`, in the order
   it stores them: one call per batch of changes that arrive together. A
   deleted document arrives as `{:_id .. :_rev .. :_deleted true}`.

   PouchDB can drop a change from a live feed without reporting it, and a
   started feed reports no failure. So this also returns a way to catch up:
   `:catch-up!` reads once what was stored after the last change handed to
   `f`, hands it to `f`, and resolves once it has. A change handed over twice
   is the same document twice. `:stop!` stops following."
  [dbs db-key since f]
  (let [position (volatile! since)
        batch    (volatile! [])
        stopped? (volatile! false)
        handed!  (fn [docs last-seq]
                   (when (and (seq docs) (not @stopped?))
                     ;; A catch-up can hand over changes the feed brings
                     ;; later; the position only moves forward.
                     (vswap! position max last-seq)
                     (f docs)))
        flush!   (fn []
                   (let [changes @batch]
                     (vreset! batch [])
                     (when (seq changes)
                       (handed! (mapv :doc changes) (:seq (peek changes))))))
        feed     (doto ^js (.changes ^js (db-key dbs) #js {:include_docs true :live true :since since})
                   (.on "change"
                        (fn [^js change]
                          ;; A batch is what arrives before the next task:
                          ;; PouchDB emits the changes of one write, such as
                          ;; a replicated batch or a bulk write, one after
                          ;; another. The first change of a batch schedules
                          ;; its flush.
                          (when (empty? @batch)
                            (js/setTimeout flush! 0))
                          (vswap! batch conj {:doc (db/couch->clj (.-doc change)) :seq (.-seq change)})))
                   (.on "error"
                        (fn [err]
                          (log/error :db/follow-failed {:db db-key :error (str err)}))))]
    {:catch-up! (fn ^:async catch-up! []
                  (let [{:keys [docs position]} (await (read-changes dbs db-key @position))]
                    (handed! docs position)))
     :stop!     (fn stop! []
                  (vreset! stopped? true)
                  (.cancel feed))}))


(defn user-doc?
  "Replication filter: design documents stay on the device. CouchDB builds
   an index for every design document it receives, and the server never
   queries user-db through one."
  [doc]
  (not (design-doc? doc)))


(defn- docs-written
  [^js direction]
  (or (some-> direction .-docs_written) 0))


(defn- written-ids-by-type
  "The ids one batch wrote, grouped by the `type` their documents carry. The
   type is read and not interpreted: which of them is worth anything is known
   by whoever declared the schema, and this namespace declares none."
  [^js change]
  (reduce (fn [by-type ^js doc]
            (update by-type (.-type doc) (fnil conj #{}) (.-_id doc)))
          {}
          (some-> change .-docs)))


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
   :pulled-ids {type #{id}} :pulled-revs #{[id rev]}}`, documents written on
   each side, the ids the pull wrote here, grouped by the type their documents
   carry, and the exact revisions it wrote, which is how the change feed tells
   the pull's own writes from local ones — and never
   rejects: a failed pass resolves nil, so a caller can fire it on a trigger
   without guarding every one.

   Grouped rather than listed, so a reader takes the types it owns and nothing
   is read for the rest: a keyed read over ids that name nothing it knows costs
   a lookup per id and answers none of them.

   The ids come from the pass's own `change` events, where PouchDB hands over
   the batch it has just written; the `complete` result carries the counters
   alone. Reading them back off the changes feed afterwards would mean keeping
   a sequence number across passes and would answer with this device's own
   writes as well."
  [dbs db-key account-id]
  (let [pulled-ids  (atom {})
        pulled-revs (atom #{})
        remote      (str (.. js/globalThis -location -origin)
                         "/db/"
                         ((db->remote-name db-key) account-id))]
    (js/Promise.
     (fn [resolve _reject]
       (doto (db/sync (db-key dbs) {:filter user-doc? :live false :remote-url remote})
         (.on "change"
              (fn [^js info]
                (when (= "pull" (.-direction info))
                  (swap! pulled-ids
                    #(merge-with into % (written-ids-by-type (.-change info))))
                  (swap! pulled-revs into (written-revisions (.-change info))))))
         (.on "complete"
              (fn [^js info]
                (resolve {:pulled      (docs-written (some-> info .-pull))
                          :pulled-ids  @pulled-ids
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
          (await (next-task)))
        (recur docs more))
      docs)))


(defn remove
  [dbs schema doc]
  (db/remove (database dbs schema) doc))


(defn- typed
  [schema query]
  (assoc-in query [:selector :type] (:type schema)))


(defn find
  "Documents of `schema`'s type matching the query's selector; the type is
   added to the selector here."
  [dbs schema query]
  (db/find (database dbs schema) (typed schema query)))


(defn find-all
  "Like `find`, without a page limit."
  [dbs schema query]
  (db/find-all (database dbs schema) (typed schema query)))


(defn- ^:async ensure-index!
  "Idempotent, so running it on every start is what gives an installation
   that predates the index its copy. A failure is logged, never raised: a
   missing index slows queries down, it does not break them."
  [db {:keys [fields] index-name :name}]
  (try
    (await (db/create-index db fields {:name index-name :ddoc index-name}))
    (catch :default err
      (log/error :db/index-error {:index index-name :error (str err)}))))


(def ^:private type-index
  "The engine's own index: `typed` puts :type into every selector, so every
   find on a database that holds a schema goes through it. Nobody declares
   it."
  {:name "by-type" :fields [:type]})


(defn indexes-of
  "The indexes a database with `schemas` needs: the engine's type index and
   every one the schemas declare. Nothing for a database no schema lives in."
  [schemas]
  (when (seq schemas)
    (cons type-index (mapcat :indexes schemas))))


(defn ensure-indexes!
  "Every index in `indexes` (see `indexes-of`) exists on `db`."
  [db indexes]
  (js/Promise.all
   (into-array
    (map #(ensure-index! db %) indexes))))


(defn ^:async init!
  "Opens the databases and gives each the indexes of the schemas that live
   in it. Run on every start: that is what gives an existing installation a
   new index."
  [schemas]
  (await (db-migrations/ensure-migrated!))
  (let [dbs {:device/db (device-db)
             :user/db   (user-db)}]
    (await (js/Promise.all
            (into-array
             (map (fn [[db-key db]]
                    (ensure-indexes! db (indexes-of (filter #(= db-key (:db %)) schemas))))
                  dbs))))
    dbs))


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

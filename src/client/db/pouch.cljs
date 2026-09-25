(ns db.pouch
  "Storage engine over PouchDB: documents, databases, indexes, views and
   replication. Knows no document type of its own — every call carries the
   `schema` of the aggregate it serves, a value the owning adapter declares:

     {:type    \"...\"            ; stored as the document's :type
      :db      :user/db          ; the database the type lives in
      :indexes [{:name \"...\" :fields [...]}]   ; optional
      :views   {\"name\" {:map \"function (doc) {...}\"}}}  ; optional

   `init!` takes every schema the app declares and gives each database its
   indexes and design documents."
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


(defn ^:async follow!
  "Hands `on-docs` every document of `db-key` once, then every document the
   database's change feed brings from then on, in batches: one call per
   batch of changes that arrive together. Design documents are left out. A
   deleted document arrives as `{:_id .. :_rev .. :_deleted true}`.

   The update sequence is recorded before the documents are read and the feed
   starts from it, so a write landing during the read is handed over again
   from the feed rather than missed; a reader applies a revision it already
   holds as nothing. Resolves once the documents were handed over, with a
   function that stops the feed."
  [dbs db-key on-docs]
  (let [^js db   (db-key dbs)
        ^js info (await (.info db))
        since    (.-update_seq info)
        ^js all  (await (.allDocs db #js {:include_docs true}))
        rows     (.-rows all)
        pending  (volatile! [])
        flush!   (fn []
                   (let [docs @pending]
                     (vreset! pending [])
                     (on-docs docs)))]
    (on-docs (into []
                   (comp (map #(.-doc ^js %))
                         (clojure.core/remove design-doc?)
                         (map db/couch->clj))
                   rows))
    (let [feed (.changes db #js {:include_docs true :live true :since since})]
      (.on feed
           "change"
           (fn [^js change]
             (let [doc (.-doc change)]
               (when-not (design-doc? doc)
                 ;; A batch is what arrives before the next task: PouchDB
                 ;; emits the changes of one write — a replicated batch, a
                 ;; bulk write — one after another, and one store update
                 ;; for all of them renders once.
                 (when (empty? @pending)
                   (js/setTimeout flush! 0))
                 (vswap! pending conj (db/couch->clj doc))))))
      (.on feed "error" (fn [err] (log/error :db/follow-failed {:db db-key :error (str err)})))
      #(.cancel feed))))


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


(defn on-written!
  "Calls `f` with `db-key` and the documents every write through this
   namespace wrote, each at the revision PouchDB gave it, once PouchDB has
   accepted the write and before the writer's promise resolves. A refused
   write reports nothing. One listener per `dbs`; nil stops listening."
  [dbs f]
  (some-> (:dbs/on-written dbs) (reset! f)))


(defn- written!
  "Hands `docs` to the listener and passes `result` on."
  [dbs db-key docs result]
  (when-let [f (some-> (:dbs/on-written dbs) deref)]
    (when (seq docs)
      (try
        (f db-key docs)
        (catch :default err
          (log/error :db/written-listener-failed {:db db-key :error (str err)})))))
  result)


(defn insert
  "Writes doc as one of `schema`'s type into the database that holds it."
  [dbs schema doc]
  (let [doc (assoc doc :type (:type schema))]
    (.then (db/insert (database dbs schema) doc)
           (fn [{:keys [id rev] :as result}]
             (written! dbs (:db schema) [(assoc doc :_id id :_rev rev)] result)))))


(defn bulk-docs
  "Writes docs atomically into `schema`'s database. The docs carry their own
   :type: a bulk write moves documents that were read, tombstoned or updated,
   and those may belong to several types of the same database."
  [dbs schema docs]
  (.then (db/bulk-docs (database dbs schema) docs)
         (fn [results]
           (written! dbs
                     (:db schema)
                     (into []
                           (keep (fn [[doc {:keys [ok id rev]}]]
                                   (when ok (assoc doc :_id id :_rev rev))))
                           (map vector docs results))
                     results))))


(defn get
  "The document with `id` from `schema`'s database, or nil."
  [dbs schema id]
  (db/get (database dbs schema) id))


(defn remove
  [dbs schema doc]
  (.then (db/remove (database dbs schema) doc)
         (fn [{:keys [id rev] :as result}]
           (written! dbs (:db schema) [{:_deleted true :_id id :_rev rev}] result))))


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


(defn view
  "A reference to one of `schema`'s views, for `query`: the design document
   carries the declared name and its one view is `rows`."
  [schema view-name]
  {:db   (:db schema)
   :view (str view-name "/rows")})


(defn query
  "Rows of a view: `{:rows [{:id .. :key .. :value ..}]}`. `opts` are the
   PouchDB query options (`:keys`, `:startkey`, `:endkey`, ...)."
  [dbs {:keys [db view]} opts]
  (db/query (db dbs) view opts))


(defn- ^:async ensure-index!
  "Idempotent, so running it on every start is what gives an installation
   that predates the index its copy. A failure is logged, never raised: a
   missing index slows queries down, it does not break them."
  [db {:keys [fields] index-name :name}]
  (try
    (await (db/create-index db fields {:name index-name :ddoc index-name}))
    (catch :default err
      (log/error :db/index-error {:index index-name :error (str err)}))))


(defn- design-doc
  "One design document per declared view, named as declared, with its one
   view under the fixed key `rows` — so nothing is renamed on the way in
   or out."
  [view-name {map-source :map}]
  {:_id   (str "_design/" view-name)
   :views {:rows {:map map-source}}})


(defn- ^:async ensure-design-doc!
  "Writes the design document when it is missing or its map function differs
   from `ddoc`, so a changed map replaces the stored one and an unchanged one
   costs one read. Failure is logged, never raised."
  [db {id :_id :as ddoc}]
  (try
    (let [stored (await (db/get db id))]
      (when (not= (get-in stored [:views :rows :map]) (get-in ddoc [:views :rows :map]))
        (await (db/insert db (cond-> ddoc stored (assoc :_rev (:_rev stored)))))))
    (catch :default err
      (log/error :db/design-doc-error {:ddoc id :error (str err)}))))


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


(defn ensure-views!
  "Every design document in `views` (the `:views` declarations of one
   database's schemas, as `[name view]` pairs) is stored on `db` with the
   map source it declares."
  [db views]
  (js/Promise.all
   (into-array
    (for [[view-name view] views]
      (ensure-design-doc! db (design-doc view-name view))))))


(defn ^:async init!
  "Opens the databases and gives each the indexes and views of the schemas
   that live in it. Run on every start: that is what gives an existing
   installation a new index or view."
  [schemas]
  (await (db-migrations/ensure-migrated!))
  (let [dbs {:device/db (device-db)
             :user/db   (user-db)}]
    (await (js/Promise.all
            (into-array
             (mapcat (fn [[db-key db]]
                       (let [own (filter #(= db-key (:db %)) schemas)]
                         [(ensure-indexes! db (indexes-of own))
                          (ensure-views! db (mapcat :views own))]))
              dbs))))
    (assoc dbs :dbs/on-written (atom nil))))

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


(defn user-doc?
  "Replication filter: design documents stay on the device. CouchDB builds
   an index for every design document it receives, and the server never
   queries user-db through one."
  [doc]
  (not (str/starts-with? (.-_id ^js doc) "_design/")))


(defn sync-once!
  "Runs one bidirectional replication pass of db-key against the account's copy
   on the server. Resolves when the pass finishes and never rejects — a failed
   pass resolves nil — so a caller can fire it on a trigger without guarding
   every one."
  [dbs db-key account-id]
  (let [remote (str (.. js/globalThis -location -origin)
                    "/db/"
                    ((db->remote-name db-key) account-id))]
    (js/Promise.
     (fn [resolve _reject]
       (doto (db/sync (db-key dbs) {:filter user-doc? :live false :remote-url remote})
         (.on "complete" (fn [_] (resolve true)))
         (.on "error"
              (fn [err]
                (log/warn :db/sync-failed {:db db-key :error (str err)})
                (resolve nil))))))))


(defn- database
  [dbs schema]
  ((:db schema) dbs))


(defn insert
  "Writes doc as one of `schema`'s type into the database that holds it."
  [dbs schema doc]
  (db/insert (database dbs schema) (assoc doc :type (:type schema))))


(defn bulk-docs
  "Writes docs atomically into `schema`'s database. The docs carry their own
   :type: a bulk write moves documents that were read, tombstoned or updated,
   and those may belong to several types of the same database."
  [dbs schema docs]
  (db/bulk-docs (database dbs schema) docs))


(defn get
  "The document with `id` from `schema`'s database, or nil."
  [dbs schema id]
  (db/get (database dbs schema) id))


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


(defn- stored-view-name
  "Map keys are snake-cased on the way into PouchDB, so a view declared as
   `by-owner` is stored as `by_owner`."
  [view-name]
  (str/replace view-name "-" "_"))


(defn view
  "A reference to one of `schema`'s views, for `query`."
  [schema view-name]
  {:db   (:db schema)
   :view (str view-name "/" (stored-view-name view-name))})


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
  [view-name {map-source :map}]
  {:_id   (str "_design/" view-name)
   :views {(keyword (stored-view-name view-name)) {:map map-source}}})


(defn- view-sources
  "The map functions of a design document by view name, whichever casing the
   keys came back in."
  [{:keys [views]}]
  (into {}
        (map (fn [[view-name {map-source :map}]]
               [(str/replace (name view-name) "_" "-") map-source]))
        views))


(defn- ^:async ensure-design-doc!
  "Writes the design document when it is missing or its map functions differ
   from `ddoc`, so a changed map replaces the stored one and an unchanged one
   costs one read. Failure is logged, never raised."
  [db {id :_id :as ddoc}]
  (try
    (let [stored (await (db/get db id))]
      (when (not= (view-sources stored) (view-sources ddoc))
        (await (db/insert db (cond-> ddoc stored (assoc :_rev (:_rev stored)))))))
    (catch :default err
      (log/error :db/design-doc-error {:ddoc id :error (str err)}))))


(defn prepare!
  "Gives one database the indexes and views of `schemas`, idempotently."
  [db schemas]
  (js/Promise.all
   (into-array
    (concat (for [{:keys [indexes]} schemas
                  index indexes]
              (ensure-index! db index))
            (for [{:keys [views]}  schemas
                  [view-name view] views]
              (ensure-design-doc! db (design-doc view-name view)))))))


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
             (for [[db-key db] dbs]
               (prepare! db (filter #(= db-key (:db %)) schemas))))))
    dbs))

(ns db.pouch
  (:refer-clojure :exclude [get find remove])
  (:require
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


(def doc-type->db
  "Maps PouchDB :type string to the dbs-map key for its owning database."
  {"collection" :user/db
   "example"    :device/db
   "lesson"     :device/db
   "review"     :user/db
   "task"       :device/db
   "vocab"      :user/db})


(defn db-for
  "Returns the database instance for a given doc type string."
  [dbs doc-type]
  (some-> doc-type doc-type->db dbs))


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
       (doto (db/sync (db-key dbs) {:live false :remote-url remote})
         (.on "complete" (fn [_] (resolve true)))
         (.on "error"
              (fn [err]
                (log/warn :db/sync-failed {:db db-key :error (str err)})
                (resolve nil))))))))


(defn insert
  "Inserts doc into the database determined by its :type field."
  [dbs doc]
  (db/insert (db-for dbs (:type doc)) doc))


(defn bulk-docs
  "Atomically writes multiple docs to the database that owns `doc-type`.
   All docs must belong to that same database — caller groups by db."
  [dbs doc-type docs]
  (db/bulk-docs (db-for dbs doc-type) docs))


(defn get
  "Fetches a document by id from the database that owns the given type string."
  [dbs type-str doc-id]
  (db/get (db-for dbs type-str) doc-id))


(defn remove
  "Removes doc from the database determined by its :type field."
  [dbs doc]
  (db/remove (db-for dbs (:type doc)) doc))


(defn find
  "Queries the database determined by [:selector :type] in the query."
  [dbs query]
  (db/find (db-for dbs (get-in query [:selector :type])) query))


(defn find-all
  "Queries (with auto-pagination) the database determined by [:selector :type] in the query."
  [dbs query]
  (db/find-all (db-for dbs (get-in query [:selector :type])) query))


(def ^:private user-db-indexes
  "Every user-db query selects on :type, and reviews are looked up by
   :word-id. Without these pouchdb-find scans the whole database and filters
   in JS on the main thread (#404). Design-document ids sort `by-type` before
   `by-type-word-id`, which is the tie-break pouchdb-find uses for a plain
   {:type ..} selector."
  [{:fields [:type] :name "by-type"}
   {:fields [:type :word-id] :name "by-type-word-id"}])


(defn- ^:async ensure-index!
  "Idempotent, so running it on every start is what gives an installation
   that predates the index its copy. A failure is logged, never raised: a
   missing index slows queries down, it does not break them."
  [db {:keys [fields] index-name :name}]
  (try
    (await (db/create-index db fields {:name index-name :ddoc index-name}))
    (catch :default err
      (log/error :db/index-error {:index index-name :error (str err)}))))


(defn- ensure-user-db-indexes!
  [db]
  (js/Promise.all (into-array (map #(ensure-index! db %) user-db-indexes))))


(defn ^:async init!
  [_deps]
  (await (db-migrations/ensure-migrated!))
  (let [user-db (user-db)]
    (await (ensure-user-db-indexes! user-db))
    {:device/db (device-db)
     :user/db   user-db}))

(ns db.pouch
  (:refer-clojure :exclude [get find remove])
  (:require
   [db :as db]
   [db-migrations :as db-migrations]))


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


(defn insert
  "Inserts doc into the database determined by its :type field."
  [dbs doc]
  (db/insert (db-for dbs (:type doc)) doc))


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


(defn ^:async init!
  [_deps]
  (await (db-migrations/ensure-migrated!))
  {:device/db (device-db)
   :user/db   (user-db)})

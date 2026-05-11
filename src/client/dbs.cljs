(ns dbs
  (:refer-clojure :exclude [get find remove])
  (:require
   [db :as db]))


;;
;; PouchDB connections
;;


(def legacy-local-db-name "local-db")


(def user-db-name "user-db")


(def device-db-name "device-db")


(def legacy-dictionary-db-name "dictionary-db")


(defn local-db
  []
  (db/use legacy-local-db-name))


(defn user-db
  []
  (db/use user-db-name))


(defn device-db
  []
  (db/use device-db-name))


(defn legacy-dictionary-db
  []
  (db/use legacy-dictionary-db-name))


(def doc-type->db
  "Maps PouchDB :type string to the dbs-map key for its owning database."
  {"vocab"   :user/db
   "review"  :user/db
   "example" :device/db
   "task"    :device/db
   "lesson"  :device/db})


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


(defn dbs
  []
  {:user/db   (user-db)
   :device/db (device-db)
   :dictionary/legacy-db (legacy-dictionary-db)})

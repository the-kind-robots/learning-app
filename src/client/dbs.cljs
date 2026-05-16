(ns dbs
  (:refer-clojure :exclude [get find remove])
  (:require
   [db :as db]
   [lambdaisland.glogi :as log]))


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


;;
;; SQLite dictionary — worker proxy
;;


(defonce ^:private worker-state
  (atom {:worker nil :ready? false :next-id 0}))


(defonce ^:private dict-db (atom nil))


(defn dictionary-ready?
  []
  (:ready? @worker-state))


(defn- dictionary-db
  []
  @dict-db)


(defn- make-exec-proxy
  [worker]
  #js {:exec
       (fn [^js opts]
         (js/Promise.
          (fn [resolve reject]
            (let [id  (:next-id (swap! worker-state update :next-id inc))
                  msg (doto (js/Object.assign #js {} opts) (aset "id" id))]
              (..
               worker
               (addEventListener
                "message"
                (fn handler [^js e]
                  (when (= (.. e -data -id) id)
                    (.removeEventListener worker "message" handler)
                    (if (.. e -data -error)
                      (reject (js/Error. (.. e -data -error)))
                      (resolve (.. e -data -result)))))))
              (.postMessage worker msg)))))})


(defn init!
  []
  (when (nil? (:worker @worker-state))
    (let [worker (js/Worker. (str "/js/sqlite3-worker.js?sqlite3.dir=/js"
                                  (when js/goog.DEBUG "&telemetry=1")))]
      (..
       worker
       (addEventListener
        "message"
        (fn [^js e]
          (case (.. e -data -type)
            "ready" (swap! worker-state assoc :ready? true)
            "error" (log/error :dbs/sqlite3-worker-error {:message (.. e -data -message)})
            "phase" (let [d   (.. e -data)
                          ph  (.-phase d)
                          ms  (.-durationMs d)
                          ok? (= "ok" (.-status d))]
                      (if ok?
                        (log/info (keyword "dict-worker" ph) {:duration-ms ms})
                        (log/error (keyword "dict-worker" ph) {:duration-ms ms :reason (.-reason d)})))
            nil))))
      (..
       worker
       (addEventListener
        "error"
        (fn [^js e]
          (log/error :dbs/sqlite3-worker-crashed {:error (str e)})
          (swap! worker-state assoc :ready? false))))
      (reset! dict-db (make-exec-proxy worker))
      (swap! worker-state assoc :worker worker))))


(defn dbs
  []
  {:user/db       (user-db)
   :device/db     (device-db)
   :dictionary/db (dictionary-db)
   :dictionary/legacy-db (legacy-dictionary-db)})

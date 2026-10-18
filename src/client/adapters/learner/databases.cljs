(ns adapters.learner.databases
  "Opening the databases of the learner's data: PouchDB's user-db and
   device-db, the migrations that need no waiting, and the request that
   protects them from eviction."
  (:require
   [db-migrations]
   [db.pouch :as pouch]
   [lambdaisland.glogi :as log]))


(defn open-pouch!
  "Opens PouchDB's databases once their migrations have run. Resolves with the
   map of the databases."
  []
  (pouch/init!))


(defn run-background-migrations!
  "Starts the once-per-device migrations that need no waiting."
  []
  (db-migrations/run-in-background!))


(defn persist!
  "Asks the browser to exempt our storage (device-db, the durable home of the
   account token) from automatic eviction. The auth cookie is rebuilt from
   device-db each boot, so protecting device-db is what protects sign-in.
   Resolves nothing."
  []
  (when (some-> js/navigator .-storage .-persist)
    (.then (js/navigator.storage.persist)
           #(log/info :storage/persisted {:granted %})))
  nil)


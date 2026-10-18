(ns adapters.learner.databases
  "Opening the databases of the learner's data: PouchDB's user-db and
   device-db, and the request that protects them from eviction."
  (:require
   [db.pouch :as pouch]
   [lambdaisland.glogi :as log]))


(defn open-pouch!
  "Opens PouchDB's databases once their migrations have run. Resolves with the
   map of the databases."
  []
  (pouch/init!))


(defn persist!
  "Asks the browser not to evict our storage: the auth cookie is rebuilt from device-db each boot, so
   protecting it protects sign-in."
  []
  (when (some-> js/navigator .-storage .-persist)
    (.then (js/navigator.storage.persist)
           #(log/info :storage/persisted {:granted %})))
  nil)


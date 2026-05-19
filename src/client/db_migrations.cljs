(ns db-migrations
  (:require
   [db :as db]
   [lambdaisland.glogi :as log]
   [utils :as utils]))


(defn local-db [] (db/use "local-db"))


(defn user-db [] (db/use "user-db"))


(defn device-db [] (db/use "device-db"))


(def ^:private doc-type->db
  {"example" :device/db
   "lesson"  :device/db
   "review"  :user/db
   "task"    :device/db
   "vocab"   :user/db})


(def ^:private migration-id "migration:local-db-split")


(defn- conflict?
  [err]
  (let [status (or (.-status err)
                   (:status err)
                   (get-in err [:body :status]))
        name   (or (.-name err) (:name err))]
    (or (= status 409)
        (= status "409")
        (= name "conflict"))))


(defn- strip-rev
  [doc]
  (dissoc doc :_rev))


(defn ^:async copy-doc!
  [db doc]
  (try
    (await (db/insert db (strip-rev doc)))
    (catch js/Error err
      (if (conflict? err)
        nil
        (throw err)))))


(defn ^:async copy-type!
  [local-db dest-db doc-type]
  (let [{:keys [docs]} (await (db/find local-db {:selector {:type doc-type}}))]
    (when (seq docs)
      (log/info :db-migrations/copy-type {:type doc-type :count (count docs)}))
    (await (js/Promise.all (into-array (map #(copy-doc! dest-db %) docs))))))


(defn ^:async run-local-db-split!
  []
  (let [device-db (device-db)]
    (if (await (db/get device-db migration-id))
      (do
        (log/info :db-migrations/already-complete {:id migration-id})
        :already-complete)
      (let [local-db (local-db)
            all-dbs  {:user/db (user-db) :device/db device-db}]
        (log/info :db-migrations/start {:id migration-id})
        (doseq [[doc-type db-key] doc-type->db]
          (await (copy-type! local-db (all-dbs db-key) doc-type)))
        (await (db/insert
                device-db
                {:_id          migration-id
                 :type         "migration"
                 :migration-id "local-db-split"
                 :source       "local-db"
                 :targets      ["user-db" "device-db"]
                 :created-at   (utils/now-iso)}))
        (log/info :db-migrations/complete {:id migration-id})
        :complete))))


(def ^:private task-data-migration-id "migration:task-data-payload")


(defn ^:async run-task-data-payload!
  []
  (let [device-db (device-db)]
    (if (await (db/get device-db task-data-migration-id))
      (do
        (log/info :db-migrations/already-complete {:id task-data-migration-id})
        :already-complete)
      (do
        (let [{:keys [docs]} (await (db/find device-db
                                             {:selector {:type    "task"
                                                         :word-id {:$exists true}
                                                         :data    {:$exists false}}}))]
          (doseq [task docs]
            (await (db/insert device-db
                              (-> task
                                  (assoc :data {:word-id (:word-id task)})
                                  (dissoc :word-id))))))
        (await (db/insert device-db
                          {:_id          task-data-migration-id
                           :type         "migration"
                           :migration-id "task-data-payload"
                           :created-at   (utils/now-iso)}))
        (log/info :db-migrations/complete {:id task-data-migration-id})
        :complete))))


(defn- run-local-db-split-migration!
  []
  (run-local-db-split!))


(defn- run-task-data-payload-migration!
  []
  (run-task-data-payload!))


(def ^:private migrations
  [{:id  "migration:local-db-split"
    :run run-local-db-split-migration!}
   {:id  "migration:task-data-payload"
    :run run-task-data-payload-migration!}])


(def ^:private migration-state
  (atom {:status :not-started :promise nil}))


(defn migration-status [] (:status @migration-state))


(defn ^:async run-all-migrations!
  []
  (try
    (doseq [{:keys [run]} migrations]
      (await (run)))
    (swap! migration-state assoc :status :done :promise nil)
    true
    (catch js/Error err
      (log/error :db-migrations/error {:error (str err)})
      (swap! migration-state assoc :status :failed :promise nil)
      (throw err))))


(defn ensure-migrated!
  []
  (let [{:keys [status promise]} @migration-state]
    (case status
      :done        (js/Promise.resolve true)
      :in-progress promise
      (let [p (run-all-migrations!)]
        (swap! migration-state assoc :status :in-progress :promise p)
        p))))

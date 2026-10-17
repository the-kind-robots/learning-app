(ns db-migrations
  (:require
   [db :as db]
   [lambdaisland.glogi :as log]
   [utils :as utils]))


(defn local-db [] (db/use "local-db"))


(defn user-db [] (db/use "user-db"))


(defn device-db [] (db/use "device-db"))


(def ^:private doc-type->db
  ;; The frozen map of the one-time local-db split: where each type went at
  ;; the time of the migration. Not routing — the engine takes that from the
  ;; schemas — and it does not change when a schema does.
  {"example" :device/db
   "lesson"  :device/db
   "review"  :user/db
   "task"    :device/db
   "vocab"   :user/db})


(def ^:private migration-id "migration:local-db-split")


(defn ^:async copy-type!
  [local-db dest-db doc-type]
  (let [{:keys [docs]} (await (db/find-all local-db {:selector {:type doc-type}}))]
    (when (seq docs)
      (log/info :db-migrations/copy-type {:type doc-type :count (count docs)}))
    (await (js/Promise.all (into-array (map #(db/insert-if-absent dest-db (dissoc % :_rev)) docs))))))


(defn- ^:async once!
  "Runs `run` unless device-db holds the record `id`, and then records the
   run with the fields of `record`. Resolves `:complete`, or
   `:already-complete` when the record was there."
  [id record run]
  (let [device-db (device-db)]
    (if (await (db/get device-db id))
      (do
        (log/info :db-migrations/already-complete {:id id})
        :already-complete)
      (do
        (log/info :db-migrations/start {:id id})
        (await (run))
        (await (db/insert device-db (assoc record :_id id :type "migration" :created-at (utils/now-iso))))
        (log/info :db-migrations/complete {:id id})
        :complete))))


(defn ^:async run-local-db-split!
  []
  (await
   (once! migration-id
          {:migration-id "local-db-split"
           :source       "local-db"
           :targets      ["user-db" "device-db"]}
          (^:async fn
           []
           (let [local-db (local-db)
                 all-dbs  {:user/db (user-db) :device/db (device-db)}]
             (doseq [[doc-type db-key] doc-type->db]
               (await (copy-type! local-db (all-dbs db-key) doc-type))))))))


(defn- run-local-db-split-migration!
  []
  (run-local-db-split!))


(def ^:private before-open
  "The migrations that run before the databases open. The start waits for them."
  [{:id  "migration:local-db-split"
    :run run-local-db-split-migration!}])


(def ^:private migration-state
  (atom {:status :not-started :promise nil}))


(defn migration-status [] (:status @migration-state))


(defn ^:async run-all-migrations!
  []
  (try
    (doseq [{:keys [run]} before-open]
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

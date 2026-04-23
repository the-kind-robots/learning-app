(ns db-migrations
  (:require
   [db :as db]
   [dbs :as dbs]
   [examples :as examples]
   [lambdaisland.glogi :as log]
   [promesa.core :as p]
   [tasks :as tasks]
   [utils :as utils]))


(def ^:private migration-id "migration:local-db-split")



(defn- strip-rev
  [doc]
  (dissoc doc :_rev))


(defn- copy-doc!
  [db doc]
  (p/catch
    (db/insert db (strip-rev doc))
    (fn [err]
      (if (db/conflict? err)
        nil
        (throw err)))))


(defn- copy-type!
  [local-db dest-db doc-type]
  (p/let [{:keys [docs]} (db/find local-db {:selector {:type doc-type}})]
    (when (seq docs)
      (log/info :db-migrations/copy-type {:type doc-type :count (count docs)}))
    (p/all (map #(copy-doc! dest-db %) docs))))


(defn- run-local-db-split!
  []
  (let [device-db (dbs/device-db)]
    (p/let [marker (db/get device-db migration-id)]
      (if marker
        (do
          (log/info :db-migrations/already-complete {:id migration-id})
          :already-complete)
        (let [local-db (dbs/local-db)]
          (log/info :db-migrations/start {:id migration-id})
          (p/do
            (let [all-dbs {:user/db (dbs/user-db) :device/db device-db}]
              (p/doseq [[doc-type db-key] dbs/doc-type->db]
                (copy-type! local-db (all-dbs db-key) doc-type)))
            (db/insert
             device-db
             {:_id          migration-id
              :type         "migration"
              :migration-id "local-db-split"
              :source       "local-db"
              :targets      ["user-db" "device-db"]
              :created-at   (utils/now-iso)})
            (log/info :db-migrations/complete {:id migration-id})
            :complete))))))


(def ^:private task-data-migration-id "migration:task-data-payload")


(defn- run-task-data-payload!
  "Move legacy task payload fields under :data so task handlers read one shape."
  []
  (let [device-db (dbs/device-db)]
    (p/let [marker (db/get device-db task-data-migration-id)]
      (if marker
        (do
          (log/info :db-migrations/already-complete {:id task-data-migration-id})
          :already-complete)
        (p/do
          (p/let [{:keys [docs]} (db/find device-db
                                          {:selector {:type    "task"
                                                      :word-id {:$exists true}
                                                      :data    {:$exists false}}})]
            (p/doseq [task docs]
              (db/insert device-db
                         (-> task
                             (assoc :data {:word-id (:word-id task)})
                             (dissoc :word-id)))))
          (db/insert device-db
                     {:_id          task-data-migration-id
                      :type         "migration"
                      :migration-id "task-data-payload"
                      :created-at   (utils/now-iso)})
          (log/info :db-migrations/complete {:id task-data-migration-id})
          :complete)))))


(def ^:private example-fetch-task-dedupe-migration-id
  "migration:dedupe-example-fetch-tasks")


(defn- active-task?
  [task]
  (not= "failed" (:status task)))


(defn- task-word-id
  [task]
  ;; Keep top-level fallback for partially migrated devices that may fail between
  ;; task-data-payload and this cleanup migration.
  (or (get-in task [:data :word-id])
      (:word-id task)))


(defn- example-fetch-task-id
  [word-id]
  (tasks/id-for "example-fetch" {:word-id word-id}))


(defn- task-sort-key
  [task]
  (let [word-id (task-word-id task)]
    [(if (= (example-fetch-task-id word-id) (:_id task)) 0 1)
     (or (:created-at task) "")
     (or (:_id task) "")]))


(defn- canonical-example-fetch-task
  [task]
  (let [word-id (task-word-id task)]
    (-> task
        (assoc :_id       (example-fetch-task-id word-id)
               :task-type "example-fetch"
               :data      {:word-id word-id})
        (dissoc :_rev :word-id))))


(defn- ensure-canonical-example-fetch-task!
  [device-db task]
  (p/catch
    (db/insert device-db (canonical-example-fetch-task task))
    (fn [err]
      (if (db/conflict? err)
        nil
        (throw err)))))


(defn- canonical-example-fetch-task?
  [task]
  (= (example-fetch-task-id (task-word-id task)) (:_id task)))


(defn- canonicalize-example-fetch-group!
  [device-db tasks]
  (let [[keep & duplicates] (sort-by task-sort-key tasks)
        remove-tasks        (if (canonical-example-fetch-task? keep)
                              duplicates
                              tasks)]
    (p/do
      (when-not (canonical-example-fetch-task? keep)
        (ensure-canonical-example-fetch-task! device-db keep))
      (when (seq remove-tasks)
        (log/info :db-migrations/dedupe-example-fetch-tasks
                  {:word-id (task-word-id keep)
                   :kept    (example-fetch-task-id (task-word-id keep))
                   :removed (mapv :_id remove-tasks)})
        (p/all (map #(db/remove device-db %) remove-tasks))))))


(defn- run-example-fetch-task-dedupe!
  "Collapse active legacy example-fetch duplicates into one canonical task per word."
  []
  (let [device-db (dbs/device-db)]
    (p/let [marker (db/get device-db example-fetch-task-dedupe-migration-id)]
      (if marker
        (do
          (log/info :db-migrations/already-complete {:id example-fetch-task-dedupe-migration-id})
          :already-complete)
        (p/do
          (p/let [{:keys [docs]} (db/find-all device-db
                                              {:selector {:type      "task"
                                                          :task-type "example-fetch"}})
                  groups (->> docs
                              (filter active-task?)
                              (filter task-word-id)
                              (group-by task-word-id)
                              (vals))]
            (p/doseq [tasks groups]
              (canonicalize-example-fetch-group! device-db tasks)))
          (db/insert device-db
                     {:_id          example-fetch-task-dedupe-migration-id
                      :type         "migration"
                      :migration-id "dedupe-example-fetch-tasks"
                      :created-at   (utils/now-iso)})
          (log/info :db-migrations/complete {:id example-fetch-task-dedupe-migration-id})
          :complete)))))


(def ^:private example-docs-source-migration-id
  "migration:example-doc-source-and-structure")


(defn- example-modified-at
  [example]
  (or (:modified-at example)
      (:created-at example)
      (utils/now-iso)))


(defn- user-example?
  [example]
  (= "user" (:source example)))


(defn- migrated-example-doc
  [example source]
  (assoc example
         :source source
         :modified-at (example-modified-at example)))


(defn- ensure-example-fetch-task!
  [device-db word-id]
  (when word-id
    (tasks/ensure! {:device/db device-db} "example-fetch" {:word-id word-id})))


(defn- migrate-example-doc!
  [device-db example]
  (cond
    (user-example? example)
    (db/insert device-db (migrated-example-doc example "user"))

    (examples/valid-structure? (:structure example))
    (db/insert device-db (migrated-example-doc example "ai"))

    :else
    (p/do
      (log/info :db-migrations/remove-unsafe-ai-example
                {:id (:_id example) :word-id (:word-id example)})
      (db/remove device-db example)
      (ensure-example-fetch-task! device-db (:word-id example)))))


(defn- run-example-docs-source!
  "Mark legacy examples by source and remove AI examples that cannot render safely."
  []
  (let [device-db (dbs/device-db)]
    (p/let [marker (db/get device-db example-docs-source-migration-id)]
      (if marker
        (do
          (log/info :db-migrations/already-complete {:id example-docs-source-migration-id})
          :already-complete)
        (p/do
          (p/let [{:keys [docs]} (db/find-all device-db {:selector {:type "example"}})]
            (p/doseq [example docs]
              (migrate-example-doc! device-db example)))
          (db/insert device-db
                     {:_id          example-docs-source-migration-id
                      :type         "migration"
                      :migration-id "example-doc-source-and-structure"
                      :created-at   (utils/now-iso)})
          (log/info :db-migrations/complete {:id example-docs-source-migration-id})
          :complete)))))


;; ---------------------------------------------------------------------------
;; Public state & API
;; ---------------------------------------------------------------------------


(def ^:private migrations
  [{:id  "migration:local-db-split"
    :run #(run-local-db-split!)}
   {:id  "migration:task-data-payload"
    :run #(run-task-data-payload!)}
   {:id  "migration:dedupe-example-fetch-tasks"
    :run #(run-example-fetch-task-dedupe!)}
   {:id  "migration:example-doc-source-and-structure"
    :run #(run-example-docs-source!)}])


(def ^:private migration-state
  (atom {:status :not-started :promise nil}))


(defn migration-status [] (:status @migration-state))


(defn- run-all-migrations!
  []
  (-> (p/do
        (p/doseq [{:keys [run]} migrations]
          (run))
        (swap! migration-state assoc :status :done :promise nil)
        true)
      (p/catch
        (fn [err]
          (log/error :db-migrations/error {:error (str err)})
          (swap! migration-state assoc :status :failed :promise nil)
          (throw err)))))


(defn ensure-migrated!
  []
  (let [{:keys [status promise]} @migration-state]
    (case status
      :done        (p/resolved true)
      :in-progress promise
      ;; :not-started or :failed - (re)try
      (let [p (run-all-migrations!)]
        (swap! migration-state assoc :status :in-progress :promise p)
        p))))

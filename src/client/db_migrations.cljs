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


(defn ^:async known-ids
  "The ids `db` already holds, tombstones included.

   `all-docs` asked for explicit keys answers for a deleted document too, and answers
   with an error row only for an id it has never seen. So a row without an error means
   the destination has already had this document, and copying it again would resurrect
   what the user deleted."
  [db ids]
  (if (empty? ids)
    #{}
    (let [{:keys [rows]} (await (db/all-docs db {:keys (vec ids)}))]
      (into #{} (comp (remove :error) (map :id)) rows))))


(defn ^:async copy-type!
  [local-db dest-db doc-type]
  (let [{:keys [docs]} (await (db/find-all local-db {:selector {:type doc-type}}))
        seen  (await (known-ids dest-db (map :_id docs)))
        fresh (remove (comp seen :_id) docs)]
    (when (seq fresh)
      (log/info :db-migrations/copy-type {:type doc-type :count (count fresh)}))
    (await (js/Promise.all (into-array (map #(copy-doc! dest-db %) fresh))))))


(defn ^:async run-once!
  "Runs `work` unless `marker` is already recorded in device-db, then records it."
  [marker work]
  (let [device-db (device-db)
        marker-id (:_id marker)]
    (if (await (db/get device-db marker-id))
      (do
        (log/info :db-migrations/already-complete {:id marker-id})
        :already-complete)
      (do
        (log/info :db-migrations/start {:id marker-id})
        (await (work))
        (await (db/insert device-db
                          (assoc marker
                                 :type       "migration"
                                 :created-at (utils/now-iso))))
        (log/info :db-migrations/complete {:id marker-id})
        :complete))))


(defn ^:async split-local-db!
  []
  (let [local-db (local-db)
        all-dbs  {:user/db (user-db) :device/db (device-db)}]
    (doseq [[doc-type db-key] doc-type->db]
      (await (copy-type! local-db (all-dbs db-key) doc-type)))))


(defn ^:async run-local-db-split!
  []
  (await (run-once!
          {:_id          migration-id
           :migration-id "local-db-split"
           :source       "local-db"
           :targets      ["user-db" "device-db"]}
          split-local-db!)))


(def ^:private task-data-migration-id "migration:task-data-payload")


(defn ^:async rewrite-legacy-tasks!
  []
  (let [device-db      (device-db)
        {:keys [docs]} (await (db/find-all device-db
                                           {:selector {:type    "task"
                                                       :word-id {:$exists true}
                                                       :data    {:$exists false}}}))]
    (doseq [task docs]
      (await (db/insert device-db
                        (-> task
                            (assoc :data {:word-id (:word-id task)})
                            (dissoc :word-id)))))))


(defn ^:async run-task-data-payload!
  []
  (await (run-once!
          {:_id task-data-migration-id
           :migration-id "task-data-payload"}
          rewrite-legacy-tasks!)))


(def ^:private repair-migration-id "migration:local-db-split-repair")


(defn ^:async run-truncated-copy-repair!
  "Both passes above once read a single page of results — 25 documents — and recorded
   their marker anyway, so everything past the 25th stayed behind in local-db, out of
   reach of a migration that would never run again (#307).

   This runs them once more under a marker of its own. Copying skips ids the destination
   already knows, so a browser that migrated completely changes nothing here, and a word
   deleted since is not resurrected."
  []
  (await (run-once!
          {:_id          repair-migration-id
           :migration-id "local-db-split-repair"
           :source       "local-db"
           :targets      ["user-db" "device-db"]}
          (^:async fn
           []
           (await (split-local-db!))
           (await (rewrite-legacy-tasks!))))))


(defn- run-local-db-split-migration!
  []
  (run-local-db-split!))


(defn- run-task-data-payload-migration!
  []
  (run-task-data-payload!))


(defn- run-truncated-copy-repair-migration!
  []
  (run-truncated-copy-repair!))


(def ^:private migrations
  [{:id  "migration:local-db-split"
    :run run-local-db-split-migration!}
   {:id  "migration:task-data-payload"
    :run run-task-data-payload-migration!}
   {:id  "migration:local-db-split-repair"
    :run run-truncated-copy-repair-migration!}])


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

(ns reconciliation
  (:require
   [db :as db]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as result-set]
   [taoensso.telemere :as t]
   [userdb :as userdb]))


(set! *warn-on-reflection* true)


(defn orphan-userdbs
  "Userdbs in CouchDB whose account row is missing from SQLite:
   [{:id 7 :dbname \"userdb-7\"} ...], sorted by id.

   A report, not a repair (#205): an orphan can be leftovers of a deleted
   account — or a live, legitimate userdb whose row vanished because SQLite
   was restored from an older backup. Auto-tombstoning at that moment would
   destroy real user data, so disposal stays a human decision. Account rows
   without a userdb are a different signal and are not reported here."
  [db]
  (let [account-ids (into #{}
                          (map :id)
                          (jdbc/execute! db
                            ["SELECT id FROM users"]
                            {:builder-fn result-set/as-unqualified-maps}))]
    (->> (db/all-dbs)
         (keep (fn [dbname]
                 (when-some [id (userdb/account-id dbname)]
                   (when-not (contains? account-ids id)
                     {:id id :dbname dbname}))))
         (sort-by :id)
         vec)))


(defn report!
  "Logs the orphan-userdb report and returns it — the REPL entry point for
   the operator, and the startup hook in `-main`. CouchDB being down must
   not block boot: failure to enumerate logs a warning and returns nil."
  [db]
  (try
    (let [orphans (orphan-userdbs db)]
      (t/event! ::orphan-userdbs
                {:level (if (seq orphans) :warn :info)
                 :data  {:orphans orphans}})
      orphans)
    (catch Exception e
      (t/event! ::report-skipped
                {:level :warn
                 :data  {:error (ex-message e)}})
      nil)))

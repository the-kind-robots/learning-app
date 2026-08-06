(ns backend.reconciliation-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [db :as db]
   [migrations :as migrations]
   [next.jdbc :as jdbc]
   [reconciliation :as sut])
  (:import
   [java.io File]))


(set! *warn-on-reflection* true)


(defn- migrated-db
  []
  (let [file (doto (File/createTempFile "reconciliation-test" ".db")
               (.deleteOnExit))]
    ;; SQLite wants to create the file itself.
    (.delete file)
    (doto {:dbtype "sqlite" :dbname (.getAbsolutePath file)}
      (migrations/ensure-migrated!))))


(defn- add-account!
  [db id]
  (jdbc/execute! db
    ["INSERT INTO users (id, token_sha256) VALUES (?, ?)" id (str "sha-" id)]))


(deftest an-orphan-userdb-is-named-and-nothing-else
  (let [db (migrated-db)]
    (add-account! db 1)
    (with-redefs [db/all-dbs (constantly ["_replicator" "_users" "userdb-1" "userdb-7"])]
      (testing "only the userdb without an account row is reported"
        (is (= [{:id 7 :dbname "userdb-7"}]
               (sut/orphan-userdbs db)))))))


(deftest matched-stores-yield-an-empty-report
  (let [db (migrated-db)]
    (add-account! db 1)
    (with-redefs [db/all-dbs (constantly ["_users" "userdb-1"])]
      (is (= [] (sut/orphan-userdbs db))))))


(deftest an-account-row-without-a-userdb-is-not-reported
  (testing "rows without dbs are a different signal, out of scope for #205"
    (let [db (migrated-db)]
      (add-account! db 1)
      (add-account! db 2)
      (with-redefs [db/all-dbs (constantly ["_users" "userdb-1"])]
        (is (= [] (sut/orphan-userdbs db)))))))


(deftest couchdb-being-down-never-raises-out-of-the-report
  (let [db (migrated-db)]
    (with-redefs [db/all-dbs (fn [] (throw (ex-info "Connection refused" {})))]
      (testing "the startup path logs and continues"
        (is (nil? (sut/report! db)))))))

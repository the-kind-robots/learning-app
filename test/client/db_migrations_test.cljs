(ns client.db-migrations-test
  "The one-time moves of an old install's data, driven through `ensure-migrated!`
   and read back from the databases. The databases are the boundary."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.db-seed :as db-seed]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [db-migrations :as sut]))


(def local-db-name (db-fixtures/db-name "db-migrations-test.local"))


(def user-db-name (db-fixtures/db-name "db-migrations-test.user"))


(def device-db-name (db-fixtures/db-name "db-migrations-test.device"))


(defn- forget-the-run!
  "What a restart of the app does: the page no longer remembers it migrated."
  []
  (reset! @#'sut/migration-state {:status :not-started :promise nil}))


(use-fixtures
 :each
 (db-fixtures/db-fixture-multi [local-db-name user-db-name device-db-name])
 {:before forget-the-run!})


(defn- ^:async with-app-dbs
  "Calls `f` with the three databases an install has, which `db/use` hands out
   by name."
  [f]
  (await
   (db-fixtures/with-test-dbs
    [local-db-name user-db-name device-db-name]
    (^:async fn
     [[local-db user-db device-db]]
     (with-redefs [db/use #(case %
                             "local-db"  local-db
                             "user-db"   user-db
                             "device-db" device-db)]
       (await (f {:local-db local-db :user-db user-db :device-db device-db})))))))


(def ^:private more-than-a-page
  "Past pouchdb-find's default limit of 25, where a move that reads one page
   would silently leave documents behind."
  30)


(deftest an-old-local-db-is-split-by-who-owns-the-data
  (async-testing "the learner's data goes to user-db, the device's own to device-db; nothing is cut off at a page"
    (with-app-dbs
     (^:async fn
      [{:keys [local-db user-db device-db]}]
      (await (db-seed/insert-all! local-db
                                  (concat
                                   (for [i (range more-than-a-page)]
                                     {:_id (str "v" i) :type "vocab" :value (str "Wort " i)})
                                   [{:_id "r1" :type "review" :word-id "v1"}
                                    {:_id "t1" :type "task" :task-type "example-fetch" :data {}}
                                    {:_id "e1" :type "example" :word-id "v1"}
                                    {:_id "l1" :type "lesson" :trials []}])))
      (await (sut/ensure-migrated!))
      (is (= more-than-a-page (count (await (db-queries/fetch-by-type user-db "vocab")))))
      (is (= 1 (count (await (db-queries/fetch-by-type user-db "review")))))
      (doseq [doc-type ["example" "lesson"]]
        (is (= 1 (count (await (db-queries/fetch-by-type device-db doc-type)))) doc-type))
      (await (#'sut/run-in-background))
      (is (empty? (await (db-queries/fetch-by-type device-db "task"))) "the sweep, run after the split, clears the task queue")))))


(deftest a-migration-that-ran-does-not-run-again
  (async-testing "after a restart the old local-db is not copied a second time"
    (with-app-dbs
     (^:async fn
      [{:keys [local-db user-db]}]
      (await (db/insert local-db {:_id "v1" :type "vocab" :value "der Hund"}))
      (await (sut/ensure-migrated!))
      (await (db/insert local-db {:_id "v2" :type "vocab" :value "die Katze"}))
      (forget-the-run!)
      (await (sut/ensure-migrated!))
      (is (= ["v1"] (mapv :_id (await (db-queries/fetch-by-type user-db "vocab")))))))))


(deftest callers-asking-at-once-wait-for-the-same-migration
  (async-testing "concurrent calls share one run"
    (with-app-dbs
     (^:async fn
      [_]
      (let [p1 (sut/ensure-migrated!)
            p2 (sut/ensure-migrated!)]
        (is (identical? p1 p2))
        (is (true? (await p1))))))))


(deftest a-migration-that-failed-is-tried-again-on-the-next-ask
  (async-testing "a transient database failure rejects once, then the data moves"
    (with-app-dbs
     (^:async fn
      [{:keys [local-db user-db]}]
      (await (db/insert local-db {:_id "v1" :type "vocab" :value "der Hund"}))
      (let [use-db db/use
            calls  (atom 0)]
        (with-redefs [db/use (fn [name]
                               (if (= 1 (swap! calls inc))
                                 (throw (ex-info "Transient error" {}))
                                 (use-db name)))]
          (is (= "rejected" (try (await (sut/ensure-migrated!)) (catch :default _ "rejected"))))
          (is (true? (await (sut/ensure-migrated!))))
          (is (= ["v1"] (mapv :_id (await (db-queries/fetch-by-type user-db "vocab")))))))))))


(defn- ^:async seed-task-queue!
  "What an earlier build's task queue left in device-db: `n` tasks under
   queue ids, one under a generated id, the queue's index and the engine's
   `by-type` index; and the device's identity."
  [device-db n]
  (await (db/bulk-docs device-db
                       (conj (mapv (fn [i] {:_id (str "task:example-fetch:vocab:w" (+ 1000 i) ":") :type "task"})
                                   (range n))
                             {:_id "0C1D2E3F" :task-type "example-fetch" :type "task"}
                             {:_id "identity" :type "identity"})))
  (await (db/create-index device-db [:type :run-at :created-at]
                          {:ddoc "by-type-run-at-created-at" :name "by-type-run-at-created-at"}))
  (await (db/create-index device-db [:type] {:ddoc "by-type" :name "by-type"})))


(defn- ^:async ids
  [device-db]
  (into #{} (map :id) (:rows (await (db/all-docs device-db)))))


(deftest the-task-queue-is-swept-away-once
  (async-testing "more tasks than a page, whatever their ids, and both indexes go; the identity stays; it runs once"
    (with-app-dbs
     (^:async fn
      [{:keys [device-db]}]
      (await (seed-task-queue! device-db 1200))
      (await (sut/ensure-migrated!))
      (await (#'sut/run-in-background))
      (is (= #{"identity" "migration:local-db-split" "migration:task-queue-sweep"} (await (ids device-db)))
          "no task and no design document stays")
      (is (empty? (await (db-queries/index-names device-db))))
      (await (db/insert device-db {:_id "task:late" :type "task"}))
      (await (#'sut/run-in-background))
      (is (contains? (await (ids device-db)) "task:late") "a recorded sweep does not run again")))))


(deftest the-sweep-is-idempotent-on-a-clean-device
  (async-testing "a device with no queue gets only the record"
    (with-app-dbs
     (^:async fn
      [{:keys [device-db]}]
      (await (sut/ensure-migrated!))
      (await (#'sut/run-in-background))
      (is (= #{"migration:local-db-split" "migration:task-queue-sweep"} (await (ids device-db))))
      (is (empty? (await (db-queries/index-names device-db))))))))


(deftest the-start-does-not-wait-for-background-migrations
  (async-testing "opening the databases resolves while the sweep never does"
    (with-app-dbs
     (^:async fn
      [{:keys [device-db]}]
      (await (seed-task-queue! device-db 3))
      (with-redefs [sut/run-in-background (fn [] (js/Promise. (fn [_ _])))]
        (is (true? (await (sut/ensure-migrated!))))
        (is (nil? (sut/run-in-background!))))
      (is (not (contains? (await (ids device-db)) "migration:task-queue-sweep"))
          "the start leaves the sweep unrecorded")))))


(deftest a-failed-background-migration-is-logged-and-run-again-at-the-next-start
  (async-testing "no record is made, and the next run deletes the tasks"
    (with-app-dbs
     (^:async fn
      [{:keys [device-db]}]
      (await (seed-task-queue! device-db 3))
      (with-redefs [db/bulk-docs (fn [& _] (js/Promise.reject (js/Error. "boom")))]
        (await (#'sut/run-in-background)))
      (is (not (contains? (await (ids device-db)) "migration:task-queue-sweep")))
      (await (#'sut/run-in-background))
      (is (contains? (await (ids device-db)) "migration:task-queue-sweep"))))))

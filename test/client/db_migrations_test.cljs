(ns client.db-migrations-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [client.support.db-fixtures :as db-fixtures]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [db-migrations :as sut]
   [utils :as utils]))


(def local-db-name (db-fixtures/db-name "db-migrations-test.local"))


(def user-db-name (db-fixtures/db-name "db-migrations-test.user"))


(def device-db-name (db-fixtures/db-name "db-migrations-test.device"))


(use-fixtures
 :each
 (db-fixtures/db-fixture-multi [local-db-name user-db-name device-db-name])
 {:before (fn [] (reset! @#'sut/migration-state {:status :not-started :promise nil}))})


(defn- with-test-dbs
  [f]
  (db-fixtures/with-test-dbs
   [local-db-name user-db-name device-db-name]
   (^:async fn
    [[local-db user-db device-db]]
    (with-redefs [sut/local-db  (constantly local-db)
                  sut/user-db   (constantly user-db)
                  sut/device-db (constantly device-db)
                  utils/now-iso (constantly "2024-01-01T00:00:00.000Z")]
      (await (f {:local-db local-db :user-db user-db :device-db device-db}))))))


(defn ^:async seed-local-docs!
  [local-db]
  (await (js/Promise.all
          (into-array
           [(db/insert local-db {:_id "v1" :type "vocab" :value "der Hund"})
            (db/insert local-db {:_id "v2" :type "vocab" :value "die Katze"})
            (db/insert local-db {:_id "r1" :type "review" :word-id "v1"})
            (db/insert local-db {:_id "t1" :type "task" :task-type "example-fetch"})
            (db/insert local-db {:_id "e1" :type "example" :word-id "v1"})
            (db/insert local-db {:_id "l1" :type "lesson" :trials []})]))))


(defn ^:async fetch-by-type
  [db doc-type]
  (let [{:keys [docs]} (await (db/find db {:selector {:type doc-type}}))]
    docs))


(deftest run-local-db-split-copies-docs-to-target-dbs
  (async-testing "copies user docs to user-db and device docs to device-db"
    (with-test-dbs
     (^:async fn
      [{:keys [local-db user-db device-db]}]
      (await (seed-local-docs! local-db))
      (await (#'sut/run-local-db-split!))
      (let [user-vocabs    (await (fetch-by-type user-db "vocab"))
            user-reviews   (await (fetch-by-type user-db "review"))
            device-tasks   (await (fetch-by-type device-db "task"))
            device-exs     (await (fetch-by-type device-db "example"))
            device-lessons (await (fetch-by-type device-db "lesson"))]
        (is (= 2 (count user-vocabs)))
        (is (= 1 (count user-reviews)))
        (is (= 1 (count device-tasks)))
        (is (= 1 (count device-exs)))
        (is (= 1 (count device-lessons))))))))


(deftest run-local-db-split-writes-migration-marker
  (async-testing "writes a migration marker document to device-db"
    (with-test-dbs
     (^:async fn
      [{:keys [device-db]}]
      (await (#'sut/run-local-db-split!))
      (let [marker (await (db/get device-db "migration:local-db-split"))]
        (is (some? marker))
        (is (= "migration" (:type marker))))))))


(deftest run-local-db-split-is-idempotent
  (async-testing "returns :already-complete when marker exists"
    (with-test-dbs
     (^:async fn
      [{:keys [local-db device-db user-db]}]
      (await (seed-local-docs! local-db))
      (await (#'sut/run-local-db-split!))
      (let [result      (await (#'sut/run-local-db-split!))
            user-vocabs (await (fetch-by-type user-db "vocab"))]
        (is (= :already-complete result))
        (is (= 2 (count user-vocabs))))))))


(deftest run-local-db-split-strips-rev-from-copied-docs
  (async-testing "copied docs don't carry over the _rev from local-db"
    (with-test-dbs
     (^:async fn
      [{:keys [local-db user-db]}]
      (await (seed-local-docs! local-db))
      (await (#'sut/run-local-db-split!))
      (let [vocabs (await (fetch-by-type user-db "vocab"))]
        (is (= 2 (count vocabs)))
        (is (every? :_rev vocabs)))))))


(deftest run-task-data-payload-rewrites-legacy-tasks
  (async-testing "rewrites legacy tasks with :word-id into :data map"
    (with-test-dbs
     (^:async fn
      [{:keys [device-db]}]
      (await (db/insert device-db {:_id "t1" :type "task" :task-type "example-fetch" :word-id "w1" :attempts 0}))
      (await (#'sut/run-task-data-payload!))
      (let [task (await (db/get device-db "t1"))]
        (is (= {:word-id "w1"} (:data task)))
        (is (nil? (:word-id task))))))))


(deftest run-task-data-payload-skips-already-migrated-tasks
  (async-testing "leaves tasks that already have :data unchanged"
    (with-test-dbs
     (^:async fn
      [{:keys [device-db]}]
      (await (db/insert device-db
                        {:_id "t2" :type "task" :task-type "example-fetch" :data {:word-id "w2"} :attempts 0}))
      (await (#'sut/run-task-data-payload!))
      (let [task (await (db/get device-db "t2"))]
        (is (= {:word-id "w2"} (:data task)))
        (is (nil? (:word-id task))))))))


(deftest run-task-data-payload-is-idempotent
  (async-testing "returns :already-complete on second run"
    (with-test-dbs
     (^:async fn
      [{:keys [device-db]}]
      (await (db/insert device-db {:_id "t3" :type "task" :task-type "example-fetch" :word-id "w3" :attempts 0}))
      (await (#'sut/run-task-data-payload!))
      (let [result (await (#'sut/run-task-data-payload!))]
        (is (= :already-complete result)))))))


(deftest run-task-data-payload-writes-marker
  (async-testing "writes migration marker document to device-db"
    (with-test-dbs
     (^:async fn
      [{:keys [device-db]}]
      (await (#'sut/run-task-data-payload!))
      (let [marker (await (db/get device-db "migration:task-data-payload"))]
        (is (some? marker))
        (is (= "migration" (:type marker))))))))


(deftest ensure-migrated-resolves-on-success
  (async-testing "resolves to true when migration succeeds"
    (with-test-dbs
     (^:async fn
      [_]
      (let [result (await (sut/ensure-migrated!))]
        (is (true? result))
        (is (= :done (sut/migration-status))))))))


(deftest ensure-migrated-returns-resolved-when-already-done
  (async-testing "returns resolved true on subsequent calls"
    (with-test-dbs
     (^:async fn
      [_]
      (await (sut/ensure-migrated!))
      (let [result (await (sut/ensure-migrated!))]
        (is (true? result)))))))


(deftest ensure-migrated-concurrent-calls-share-promise
  (async-testing "concurrent calls return the same promise"
    (with-test-dbs
     (^:async fn
      [_]
      (let [p1 (sut/ensure-migrated!)
            p2 (sut/ensure-migrated!)]
        (is (identical? p1 p2))
        (let [r1 (await p1)
              r2 (await p2)]
          (is (true? r1))
          (is (true? r2))))))))


(deftest ensure-migrated-retries-on-transient-failure
  (async-testing "retries and succeeds after transient error"
    (with-test-dbs
     (^:async fn
      [{:keys [device-db]}]
      (let [call-count (atom 0)]
        (with-redefs [sut/run-local-db-split!
                      (^:async fn
                       []
                       (if (< (swap! call-count inc) 2)
                         (throw (ex-info "Transient error" {}))
                         (let [marker (await (db/get device-db "migration:local-db-split"))]
                           (when-not marker
                             (await (db/insert device-db
                                               {:_id          "migration:local-db-split"
                                                :type         "migration"
                                                :migration-id "local-db-split"
                                                :created-at   (utils/now-iso)})))
                           :complete)))]
          (try
            (await (sut/ensure-migrated!))
            (is false "First ensure-migrated! call should reject")
            (catch :default _
              (is (= :failed (sut/migration-status)))))
          (let [result2 (await (sut/ensure-migrated!))]
            (is (true? result2))
            (is (= 2 @call-count))
            (is (= :done (sut/migration-status))))))))))

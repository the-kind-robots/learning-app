(ns client.db-migrations-test
  (:require
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [client.support.db-fixtures :as db-fixtures]
   [db :as db]
   [db-migrations :as sut]
   [dbs :as dbs]
   [promesa.core :as p]
   [tasks :as tasks]
   [utils :as utils])
  (:require-macros
   [client.support.test :refer [async-testing]]))


;; =============================================================================
;; Test Helpers
;; =============================================================================


(def local-db-name (db-fixtures/db-name "db-migrations-test.local"))


(def user-db-name (db-fixtures/db-name "db-migrations-test.user"))


(def device-db-name (db-fixtures/db-name "db-migrations-test.device"))


(use-fixtures
 :each
 (db-fixtures/db-fixture-multi [local-db-name user-db-name device-db-name])
 {:before (fn [] (reset! @#'sut/migration-state {:status :not-started :promise nil}))})


(defn- with-test-dbs
  "Sets up local, user, and device test DBs, wires dbs/* to them, calls (f {:local-db ... :user-db ... :device-db ...})."
  [f]
  (db-fixtures/with-test-dbs
   [local-db-name user-db-name device-db-name]
   (fn [[local-db user-db device-db]]
     (p/with-redefs [dbs/local-db  (constantly local-db)
                     dbs/user-db   (constantly user-db)
                     dbs/device-db (constantly device-db)
                     utils/now-iso (constantly "2024-01-01T00:00:00.000Z")]
       (f {:local-db local-db :user-db user-db :device-db device-db})))))


(defn- seed-local-docs!
  "Inserts sample docs into the local-db to simulate pre-migration data."
  [local-db]
  (p/all
   [(db/insert local-db {:_id "v1" :type "vocab" :value "der Hund"})
    (db/insert local-db {:_id "v2" :type "vocab" :value "die Katze"})
    (db/insert local-db {:_id "r1" :type "review" :word-id "v1"})
    (db/insert local-db {:_id "t1" :type "task" :task-type "example-fetch"})
    (db/insert local-db {:_id "e1" :type "example" :word-id "v1"})
    (db/insert local-db {:_id "l1" :type "lesson" :trials []})]))


(defn- fetch-by-type
  [db doc-type]
  (p/let [{:keys [docs]} (db/find db {:selector {:type doc-type}})]
    docs))


(defn- example-fetch-id
  [word-id]
  (tasks/id-for "example-fetch" {:word-id word-id}))


(def example-structure
  [{:usedForm       "Hund"
    :dictionaryForm "der Hund"
    :translation    "собака"
    :wordIndex      1}])


;; =============================================================================
;; run-local-db-split!
;; =============================================================================


(deftest run-local-db-split-copies-docs-to-target-dbs
  (async-testing "copies user docs to user-db and device docs to device-db"
    (with-test-dbs
     (fn [{:keys [local-db user-db device-db]}]
       (p/do
         (seed-local-docs! local-db)
         (#'sut/run-local-db-split!)
         (p/let [user-vocabs    (fetch-by-type user-db "vocab")
                 user-reviews   (fetch-by-type user-db "review")
                 device-tasks   (fetch-by-type device-db "task")
                 device-exs     (fetch-by-type device-db "example")
                 device-lessons (fetch-by-type device-db "lesson")]
           (is (= 2 (count user-vocabs)))
           (is (= 1 (count user-reviews)))
           (is (= 1 (count device-tasks)))
           (is (= 1 (count device-exs)))
           (is (= 1 (count device-lessons)))))))))


(deftest run-local-db-split-writes-migration-marker
  (async-testing "writes a migration marker document to device-db"
    (with-test-dbs
     (fn [{:keys [device-db]}]
       (p/do
         (#'sut/run-local-db-split!)
         (p/let [marker (db/get device-db "migration:local-db-split")]
           (is (some? marker))
           (is (= "migration" (:type marker)))))))))


(deftest run-local-db-split-is-idempotent
  (async-testing "returns :already-complete when marker exists"
    (with-test-dbs
     (fn [{:keys [local-db device-db user-db]}]
       (p/do
         (seed-local-docs! local-db)
         (#'sut/run-local-db-split!)
         ;; Run again - should detect marker and skip
         (p/let [result (#'sut/run-local-db-split!)]
           (is (= :already-complete result))
           ;; Should still only have original docs (no duplicates)
           (p/let [user-vocabs (fetch-by-type user-db "vocab")]
             (is (= 2 (count user-vocabs))))))))))


(deftest run-local-db-split-strips-rev-from-copied-docs
  (async-testing "copied docs don't carry over the _rev from local-db"
    (with-test-dbs
     (fn [{:keys [local-db user-db]}]
       (p/do
         (seed-local-docs! local-db)
         (#'sut/run-local-db-split!)
         (p/let [vocabs (fetch-by-type user-db "vocab")]
           ;; _rev should be a fresh rev, not the one from local-db
           ;; Verify we can read them (if rev was wrong, PouchDB would error)
           (is (= 2 (count vocabs)))
           (is (every? :_rev vocabs))))))))


;; =============================================================================
;; run-task-data-payload!
;; =============================================================================


(deftest run-task-data-payload-rewrites-legacy-tasks
  (async-testing "rewrites legacy tasks with :word-id into :data map"
    (with-test-dbs
     (fn [{:keys [device-db]}]
       (p/do
         (db/insert device-db {:_id "t1" :type "task" :task-type "example-fetch" :word-id "w1" :attempts 0})
         (#'sut/run-task-data-payload!)
         (p/let [task (db/get device-db "t1")]
           (is (= {:word-id "w1"} (:data task)))
           (is (nil? (:word-id task)))))))))


(deftest run-task-data-payload-skips-already-migrated-tasks
  (async-testing "leaves tasks that already have :data unchanged"
    (with-test-dbs
     (fn [{:keys [device-db]}]
       (p/do
         (db/insert device-db {:_id "t2" :type "task" :task-type "example-fetch" :data {:word-id "w2"} :attempts 0})
         (#'sut/run-task-data-payload!)
         (p/let [task (db/get device-db "t2")]
           (is (= {:word-id "w2"} (:data task)))
           (is (nil? (:word-id task)))))))))


(deftest run-task-data-payload-is-idempotent
  (async-testing "returns :already-complete on second run"
    (with-test-dbs
     (fn [{:keys [device-db]}]
       (p/do
         (db/insert device-db {:_id "t3" :type "task" :task-type "example-fetch" :word-id "w3" :attempts 0})
         (#'sut/run-task-data-payload!)
         (p/let [result (#'sut/run-task-data-payload!)]
           (is (= :already-complete result))))))))


(deftest run-task-data-payload-writes-marker
  (async-testing "writes migration marker document to device-db"
    (with-test-dbs
     (fn [{:keys [device-db]}]
       (p/do
         (#'sut/run-task-data-payload!)
         (p/let [marker (db/get device-db "migration:task-data-payload")]
           (is (some? marker))
           (is (= "migration" (:type marker)))))))))


;; =============================================================================
;; run-example-fetch-task-dedupe!
;; =============================================================================


(deftest run-example-fetch-task-dedupe-removes-extra-active-tasks
  (async-testing "dedupes active example-fetch tasks by word id"
    (with-test-dbs
     (fn [{:keys [device-db]}]
       (p/do
         (db/insert device-db
                    {:_id        "legacy-1"
                     :type       "task"
                     :task-type  "example-fetch"
                     :data       {:word-id "w1"}
                     :attempts   0
                     :run-at     "2024-01-01T00:00:00.000Z"
                     :created-at "2024-01-01T00:00:00.000Z"})
         (db/insert device-db
                    {:_id        (example-fetch-id "w1")
                     :type       "task"
                     :task-type  "example-fetch"
                     :data       {:word-id "w1"}
                     :attempts   0
                     :run-at     "2024-01-01T00:01:00.000Z"
                     :created-at "2024-01-01T00:01:00.000Z"})
         (db/insert device-db
                    {:_id        "legacy-2"
                     :type       "task"
                     :task-type  "example-fetch"
                     :data       {:word-id "w1"}
                     :attempts   0
                     :run-at     "2024-01-01T00:02:00.000Z"
                     :created-at "2024-01-01T00:02:00.000Z"})

         (#'sut/run-example-fetch-task-dedupe!)

         (p/let [{:keys [docs]} (db/find device-db
                                         {:selector {:type      "task"
                                                     :task-type "example-fetch"}})]
           (is (= 1 (count docs)))
           (is (= (example-fetch-id "w1") (:_id (first docs))))))))))


(deftest run-example-fetch-task-dedupe-canonicalizes-single-legacy-task
  (async-testing "moves a single active legacy example-fetch task to canonical id"
    (with-test-dbs
     (fn [{:keys [device-db]}]
       (p/do
         (db/insert device-db
                    {:_id        "legacy-1"
                     :type       "task"
                     :task-type  "example-fetch"
                     :data       {:word-id "w1"}
                     :attempts   2
                     :run-at     "2024-01-01T00:00:00.000Z"
                     :created-at "2024-01-01T00:00:00.000Z"
                     :last-error "Still pending"})

         (#'sut/run-example-fetch-task-dedupe!)

         (p/let [{:keys [docs]} (db/find device-db
                                         {:selector {:type      "task"
                                                     :task-type "example-fetch"}})
                 task (first docs)]
           (is (= 1 (count docs)))
           (is (= (example-fetch-id "w1") (:_id task)))
           (is (= 2 (:attempts task)))
           (is (= "Still pending" (:last-error task)))))))))


(deftest run-example-fetch-task-dedupe-ignores-failed-tasks
  (async-testing "does not delete failed task history"
    (with-test-dbs
     (fn [{:keys [device-db]}]
       (p/do
         (db/insert device-db
                    {:_id        "active-task"
                     :type       "task"
                     :task-type  "example-fetch"
                     :data       {:word-id "w1"}
                     :attempts   0
                     :run-at     "2024-01-01T00:00:00.000Z"
                     :created-at "2024-01-01T00:00:00.000Z"})
         (db/insert device-db
                    {:_id            "failed-task"
                     :type           "task"
                     :task-type      "example-fetch"
                     :data           {:word-id "w1"}
                     :attempts       5
                     :run-at         nil
                     :created-at     "2024-01-01T00:00:00.000Z"
                     :status         "failed"
                     :failure-reason "retry-limit-reached"})

         (#'sut/run-example-fetch-task-dedupe!)

         (p/let [{:keys [docs]} (db/find device-db
                                         {:selector {:type      "task"
                                                     :task-type "example-fetch"}})]
           (is (= 2 (count docs)))
           (is (= #{(example-fetch-id "w1") "failed-task"}
                  (set (map :_id docs))))))))))


(deftest run-example-fetch-task-dedupe-writes-marker
  (async-testing "writes migration marker document to device-db"
    (with-test-dbs
     (fn [{:keys [device-db]}]
       (p/do
         (#'sut/run-example-fetch-task-dedupe!)
         (p/let [marker (db/get device-db "migration:dedupe-example-fetch-tasks")]
           (is (some? marker))
           (is (= "migration" (:type marker)))))))))


;; =============================================================================
;; run-example-docs-source!
;; =============================================================================


(deftest run-example-docs-source-adds-source-to-safe-ai-docs
  (async-testing "adds source and modified-at to AI examples with valid structure"
    (with-test-dbs
     (fn [{:keys [device-db]}]
       (p/do
         (db/insert device-db
                    {:_id        "example-1"
                     :type       "example"
                     :word-id    "w1"
                     :word       "der Hund"
                     :value      "Der Hund bellt."
                     :translation "Собака лает."
                     :structure  example-structure
                     :created-at "2024-01-01T00:00:00.000Z"})

         (#'sut/run-example-docs-source!)

         (p/let [example (db/get device-db "example-1")]
           (is (= "ai" (:source example)))
           (is (= "2024-01-01T00:00:00.000Z" (:modified-at example)))))))))


(deftest run-example-docs-source-removes-unsafe-ai-and-schedules-fetch
  (async-testing "removes AI examples without valid structure and schedules regeneration"
    (with-test-dbs
     (fn [{:keys [device-db]}]
       (p/do
         (db/insert device-db
                    {:_id        "example-1"
                     :type       "example"
                     :word-id    "w1"
                     :word       "der Hund"
                     :value      "Der Hund bellt."
                     :translation "Собака лает."
                     :created-at "2024-01-01T00:00:00.000Z"})

         (#'sut/run-example-docs-source!)

         (p/let [example (db/get device-db "example-1")
                 task    (db/get device-db (example-fetch-id "w1"))]
           (is (nil? example))
           (is (= "task" (:type task)))
           (is (= {:word-id "w1"} (:data task)))))))))


(deftest run-example-docs-source-keeps-user-example-without-structure
  (async-testing "user examples do not need structure"
    (with-test-dbs
     (fn [{:keys [device-db]}]
       (p/do
         (db/insert device-db
                    {:_id        "example-1"
                     :type       "example"
                     :word-id    "w1"
                     :word       "der Hund"
                     :value      "Mein Hund schläft."
                     :translation "Моя собака спит."
                     :source     "user"
                     :created-at "2024-01-01T00:00:00.000Z"})

         (#'sut/run-example-docs-source!)

         (p/let [example (db/get device-db "example-1")]
           (is (= "user" (:source example)))
           (is (nil? (:structure example)))
           (is (= "2024-01-01T00:00:00.000Z" (:modified-at example)))))))))


(deftest run-example-docs-source-writes-marker
  (async-testing "writes migration marker document to device-db"
    (with-test-dbs
     (fn [{:keys [device-db]}]
       (p/do
         (#'sut/run-example-docs-source!)
         (p/let [marker (db/get device-db "migration:example-doc-source-and-structure")]
           (is (some? marker))
           (is (= "migration" (:type marker)))))))))


;; =============================================================================
;; ensure-migrated!
;; =============================================================================


(deftest ensure-migrated-resolves-on-success
  (async-testing "resolves to true when migration succeeds"
    (with-test-dbs
     (fn [_]
       (p/let [result (sut/ensure-migrated!)]
         (is (true? result))
         (is (= :done (sut/migration-status))))))))


(deftest ensure-migrated-runs-example-fetch-task-dedupe
  (async-testing "ensure-migrated! runs example-fetch task dedupe migration"
    (with-test-dbs
     (fn [{:keys [device-db]}]
       (p/do
         (db/insert device-db
                    {:_id        "legacy-1"
                     :type       "task"
                     :task-type  "example-fetch"
                     :data       {:word-id "w1"}
                     :attempts   0
                     :run-at     "2024-01-01T00:00:00.000Z"
                     :created-at "2024-01-01T00:00:00.000Z"})
         (db/insert device-db
                    {:_id        "legacy-2"
                     :type       "task"
                     :task-type  "example-fetch"
                     :data       {:word-id "w1"}
                     :attempts   0
                     :run-at     "2024-01-01T00:01:00.000Z"
                     :created-at "2024-01-01T00:01:00.000Z"})

         (sut/ensure-migrated!)

         (p/let [{:keys [docs]} (db/find device-db
                                         {:selector {:type      "task"
                                                     :task-type "example-fetch"}})
                 marker (db/get device-db "migration:dedupe-example-fetch-tasks")]
           (is (= 1 (count docs)))
           (is (= (example-fetch-id "w1") (:_id (first docs))))
           (is (some? marker))))))))


(deftest ensure-migrated-runs-example-docs-source-migration
  (async-testing "ensure-migrated! runs example doc source/structure migration"
    (with-test-dbs
     (fn [{:keys [device-db]}]
       (p/do
         (db/insert device-db
                    {:_id        "example-1"
                     :type       "example"
                     :word-id    "w1"
                     :word       "der Hund"
                     :value      "Der Hund bellt."
                     :translation "Собака лает."
                     :structure  example-structure
                     :created-at "2024-01-01T00:00:00.000Z"})

         (sut/ensure-migrated!)

         (p/let [example (db/get device-db "example-1")
                 marker  (db/get device-db "migration:example-doc-source-and-structure")]
           (is (= "ai" (:source example)))
           (is (some? marker))))))))


(deftest ensure-migrated-returns-resolved-when-already-done
  (async-testing "returns (p/resolved true) on subsequent calls"
    (with-test-dbs
     (fn [_]
       (p/do
         (sut/ensure-migrated!)
         (p/let [result (sut/ensure-migrated!)]
           (is (true? result))))))))


(deftest ensure-migrated-concurrent-calls-share-promise
  (async-testing "concurrent calls return the same promise"
    (with-test-dbs
     (fn [_]
       (let [p1 (sut/ensure-migrated!)
             p2 (sut/ensure-migrated!)]
         (is (identical? p1 p2))
         (p/let [r1 p1
                 r2 p2]
           (is (true? r1))
           (is (true? r2))))))))


(deftest ensure-migrated-retries-on-transient-failure
  (async-testing "retries and succeeds after transient error"
    (with-test-dbs
     (fn [{:keys [device-db]}]
       (let [call-count (atom 0)]
         ;; Make run-local-db-split! fail once, then succeed
         (p/with-redefs [sut/run-local-db-split!
                         (fn []
                           (if (< (swap! call-count inc) 2)
                             (p/rejected (ex-info "Transient error" {}))
                             (p/let [marker (db/get device-db "migration:local-db-split")]
                               (when-not marker
                                 (db/insert device-db
                                            {:_id          "migration:local-db-split"
                                             :type         "migration"
                                             :migration-id "local-db-split"
                                             :created-at   (utils/now-iso)}))
                               :complete)))]
          ;; First call fails - rejects and sets status to :failed.
           (p/do
             (p/catch
               (p/do
                 (sut/ensure-migrated!)
                 (is false "First ensure-migrated! call should reject"))
               (fn [_]
                 (is (= :failed (sut/migration-status)))))
             ;; Second call retries - succeeds.
             (p/let [result2 (sut/ensure-migrated!)]
               (is (true? result2))
               (is (= 2 @call-count))
               (is (= :done (sut/migration-status)))))))))))

(ns client.tasks-test
  (:require
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [client.support.db-fixtures :as db-fixtures]
   [db :as db]
   [promesa.core :as p]
   [tasks :as sut]
   [utils :as utils])
  (:require-macros
   [client.support.test :refer [async-testing]]))


;; =============================================================================
;; Test Helpers
;; =============================================================================


(def test-db-name (db-fixtures/db-name "client.tasks-test"))


(use-fixtures
 :each
 (db-fixtures/db-fixture test-db-name)
 {:before (fn [] (reset! @#'sut/state {:enabled? true}))
  :after  sut/stop!})


(defn- with-mocked-env
  "Sets up test DB and utilities, calls (f dbs), returns promise."
  [opts f]
  (let [now-ms  (or (:now-ms opts) 1000)
        now-iso (or (:now-iso opts) (utils/ms->iso now-ms))
        online? (if (contains? opts :online?) (:online? opts) true)]
    (db-fixtures/with-test-db
      test-db-name
      (fn [db]
        (let [dbs {:device/db db :user/db db}]
          (p/with-redefs [db/use        (constantly db)
                          utils/now-ms  (constantly now-ms)
                          utils/now-iso (constantly now-iso)
                          sut/online?   (constantly online?)]
            (p/let [_ (p/catch
                        (db/create-index
                         db
                         [:type :run-at :created-at]
                         {:name "by-type-run-at-created-at"
                          :ddoc "by-type-run-at-created-at"})
                        (fn [_] nil))]
              (f dbs))))))))


(defn- get-docs
  "Returns all task docs from test db."
  [db]
  (p/-> (db/find db {:selector {:type "task"}}) :docs))


(defn- get-task-by-id
  [db task-id]
  (p/let [tasks (get-docs db)]
    (first
     (filter
      (fn [task]
        (or (= task-id (:_id task))
            (= task-id (get-in task [:data :word-id]))))
      tasks))))


(defn- get-tasks-by-type
  [db task-type]
  (p/let [tasks (get-docs db)]
    (filter #(= task-type (:task-type %)) tasks)))


(defn- get-tasks-by-status
  [db status]
  (p/let [tasks (get-docs db)]
    (filter #(= status (:status %)) tasks)))


(defn- example-fetch-data
  [word-id]
  {:word-id word-id})


(defn- example-fetch-id
  [word-id]
  (sut/id-for "example-fetch" (example-fetch-data word-id)))


;; =============================================================================
;; Test Handlers
;; =============================================================================


(defmethod sut/execute-task "succeed-task"
  [_task _dbs]
  (p/resolved true))


(defmethod sut/execute-task "fail-task"
  [_task _dbs]
  (p/resolved false))


(defmethod sut/execute-task "hinted-fail-task"
  [_task _dbs]
  (p/resolved {:retry-after-ms 2500}))


(defmethod sut/execute-task "error-task"
  [_task _dbs]
  (p/rejected (ex-info "Boom!" {})))


(defmethod sut/execute-task "detail-fail-task"
  [_task _dbs]
  (p/resolved (sut/retry {:last-error "No credits"})))


(def ^:private handled-tasks (atom []))


(defmethod sut/execute-task "tracking-task"
  [task _dbs]
  (swap! handled-tasks conj (:_id task))
  (p/resolved true))


;; =============================================================================
;; Unit Tests: Task Document Creation
;; =============================================================================


(deftest create-task-builds-correct-document
  (let [now-iso "2024-01-01T00:00:00.000Z"
        task    (sut/create-task "my-type" {:word-id "word-123"} now-iso)]
    (is (= "task" (:type task)))
    (is (= "my-type" (:task-type task)))
    (is (= {:word-id "word-123"} (:data task)))
    (is (nil? (:word-id task)))
    (is (= 0 (:attempts task)))
    (is (= now-iso (:run-at task)))
    (is (= now-iso (:created-at task)))))


(deftest create-task-can-use-explicit-id
  (let [now-iso "2024-01-01T00:00:00.000Z"
        task    (sut/create-task "my-type"
                                 {:word-id "word-123"}
                                 now-iso
                                 {:id "task:my-type:word-123"})]
    (is (= "task:my-type:word-123" (:_id task)))))


(deftest id-for-normalizes-data
  (is (= (sut/id-for "example-fetch" {:b 2 :a 1 :unused nil})
         (sut/id-for "example-fetch" {"a" 1 "b" 2})))
  (is (= (sut/id-for "example-fetch" {:word {:value "Hund" :extra nil}})
         (sut/id-for "example-fetch" {"word" {"value" "Hund"}})))
  (is (not= (sut/id-for "example-fetch" {:ids ["a" "b"]})
            (sut/id-for "example-fetch" {:ids ["b" "a"]})))
  (is (re-matches #"task:example-fetch:[0-9a-f]{8}"
                  (sut/id-for "example-fetch" {:word-id "word-1"}))))


(deftest ensure-task-handles-repeated-create-via-conflict
  (async-testing "`ensure!` returns full task doc on create and conflict"
    (with-mocked-env {}
      (fn [{device-db :device/db :as dbs}]
        (let [task-id (example-fetch-id "word-1")
              data    (example-fetch-data "word-1")]
          (p/let [created  (sut/ensure! dbs "example-fetch" data)
                  repeated (sut/ensure! dbs "example-fetch" data)
                  tasks    (get-tasks-by-type device-db "example-fetch")]
            (is (= task-id (:_id created)))
            (is (= task-id (:_id repeated)))
            (is (= 1 (count tasks)))
            (is (= task-id (:_id (first tasks))))))))))


(deftest ensure-task-normalizes-equivalent-data
  (async-testing "`ensure!` treats string/keyword keys, key order, and nil values as same data"
    (with-mocked-env {}
      (fn [{device-db :device/db :as dbs}]
        (let [task-id (sut/id-for "example-fetch" {:a 1 :b 2})]
          (p/let [created  (sut/ensure! dbs "example-fetch" {:b 2 :a 1 :ignored nil})
                  repeated (sut/ensure! dbs "example-fetch" {"a" 1 "b" 2})
                  tasks    (get-tasks-by-type device-db "example-fetch")]
            (is (= task-id (:_id created)))
            (is (= task-id (:_id repeated)))
            (is (= 1 (count tasks)))))))))


(deftest ensure-task-keeps-vector-order-significant
  (async-testing "`ensure!` keeps vector order as part of task data"
    (with-mocked-env {}
      (fn [{device-db :device/db :as dbs}]
        (p/do
          (sut/ensure! dbs "batch-task" {:ids ["a" "b"]})
          (sut/ensure! dbs "batch-task" {:ids ["b" "a"]})
          (p/let [tasks (get-tasks-by-type device-db "batch-task")]
            (is (= 2 (count tasks)))))))))


(deftest retry-task-creates-missing-task
  (async-testing "`retry!` creates deterministic task when missing"
    (with-mocked-env {}
      (fn [{device-db :device/db :as dbs}]
        (let [task-id (example-fetch-id "word-1")]
          (p/let [task (sut/retry! dbs "example-fetch" (example-fetch-data "word-1"))]
            (is (= task-id (:_id task)))
            (p/let [tasks (get-tasks-by-type device-db "example-fetch")]
              (is (= 1 (count tasks)))
              (is (= task-id (:_id (first tasks)))))))))))


(deftest retry-task-reactivates-failed-task
  (async-testing "`retry!` clears failed state and schedules retry now"
    (with-mocked-env {:now-iso "2024-01-01T00:00:00.000Z"}
      (fn [{device-db :device/db :as dbs}]
        (let [task-id (example-fetch-id "word-1")]
          (p/do
            (db/insert device-db
                       {:_id            task-id
                        :type           "task"
                        :task-type      "example-fetch"
                        :data           {:word-id "word-1"}
                        :attempts       5
                        :run-at         nil
                        :created-at     "2024-01-01T00:00:00.000Z"
                        :status         "failed"
                        :failure-reason "retry-limit-reached"
                        :failed-at      "2024-01-01T00:10:00.000Z"
                        :last-error     "No credits"})
            (sut/retry! dbs "example-fetch" (example-fetch-data "word-1"))
            (p/let [task (db/get device-db task-id)]
              (is (= 0 (:attempts task)))
              (is (= "2024-01-01T00:00:00.000Z" (:run-at task)))
              (is (nil? (:status task)))
              (is (nil? (:failure-reason task)))
              (is (nil? (:failed-at task)))
              (is (nil? (:last-error task))))))))))


(deftest retry-task-leaves-active-task-unchanged
  (async-testing "`retry!` does not reset active/in-flight tasks"
    (with-mocked-env {:now-iso "2024-01-01T00:00:00.000Z"}
      (fn [{device-db :device/db :as dbs}]
        (let [task-id (example-fetch-id "word-1")]
          (p/do
            (db/insert device-db
                       {:_id        task-id
                        :type       "task"
                        :task-type  "example-fetch"
                        :data       {:word-id "word-1"}
                        :attempts   2
                        :run-at     "2024-01-01T00:10:00.000Z"
                        :created-at "2024-01-01T00:00:00.000Z"
                        :last-error "Old error"})
            (p/let [result (sut/retry! dbs "example-fetch" (example-fetch-data "word-1"))
                    task   (db/get device-db task-id)]
              (is (= 2 (:attempts result)))
              (is (= 2 (:attempts task)))
              (is (= "2024-01-01T00:10:00.000Z" (:run-at task)))
              (is (= "Old error" (:last-error task))))))))))


;; =============================================================================
;; Unit Tests: Backoff Calculation
;; =============================================================================


(deftest backoff-increases-exponentially
  (is (= 1000 (#'sut/backoff-ms 0)))
  (is (= 2000 (#'sut/backoff-ms 1)))
  (is (= 4000 (#'sut/backoff-ms 2)))
  (is (= 8000 (#'sut/backoff-ms 3))))


(deftest backoff-caps-at-max
  (is (= 60000 (#'sut/backoff-ms 10)))
  (is (= 60000 (#'sut/backoff-ms 100))))


;; =============================================================================
;; Integration Tests: Run Cycle
;; =============================================================================


(deftest run-cycle-with-empty-queue-completes
  (async-testing "`run-cycle!` succeeds when queue is empty"
    (with-mocked-env {}
      (fn [{device-db :device/db :as dbs}]
        (p/do
          (#'sut/run-cycle! dbs)
          (p/let [docs (get-docs device-db)]
            (is (empty? docs))))))))


(deftest run-cycle-removes-successful-tasks
  (async-testing "`run-cycle!` removes tasks after success"
    (with-mocked-env {}
      (fn [{device-db :device/db :as dbs}]
        (p/do
          (sut/schedule! dbs "succeed-task" {:word-id "word-1"})
          (sut/schedule! dbs "succeed-task" {:word-id "word-2"})
          (sut/schedule! dbs "succeed-task" {:word-id "word-3"})

          (#'sut/run-cycle! dbs)

          (p/let [tasks (get-tasks-by-type device-db "succeed-task")]
            (is (empty? tasks))))))))


(deftest run-cycle-tracks-handled-tasks
  (async-testing "`run-cycle!` invokes handler for each task"
    (with-mocked-env {}
      (fn [dbs]
        (reset! handled-tasks [])
        (p/do
          (sut/schedule! dbs "tracking-task" {:word-id "word-1"})
          (sut/schedule! dbs "tracking-task" {:word-id "word-2"})

          (#'sut/run-cycle! dbs)

          (is (= 2 (count @handled-tasks))))))))


(deftest run-cycle-marks-failed-tasks-for-retry
  (async-testing "`run-cycle!` schedules retry on failure"
    (with-mocked-env {:now-ms 1000}
      (fn [{device-db :device/db :as dbs}]
        (p/do
          (sut/schedule! dbs "fail-task" {:word-id "word-1"})

          (#'sut/run-cycle! dbs)

          (p/let [tasks (get-tasks-by-type device-db "fail-task")
                  task  (first tasks)]
            (is (= 1 (count tasks)))
            (is (= 1 (:attempts task)))
            (is (> (utils/iso->ms (:run-at task)) 1000))))))))


(deftest run-cycle-uses-retry-hint-when-provided
  (async-testing "`run-cycle!` uses retry-after hints from task handlers"
    (with-mocked-env {:now-ms 1000}
      (fn [{device-db :device/db :as dbs}]
        (p/do
          (sut/schedule! dbs "hinted-fail-task" {:word-id "word-1"})

          (#'sut/run-cycle! dbs)

          (p/let [tasks (get-tasks-by-type device-db "hinted-fail-task")
                  task  (first tasks)]
            (is (= 1 (count tasks)))
            (is (= 1 (:attempts task)))
            (is (= 3500 (utils/iso->ms (:run-at task))))))))))


(deftest run-cycle-handles-task-exceptions
  (async-testing "`run-cycle!` catches handler exceptions"
    (with-mocked-env {:now-ms 1000}
      (fn [{device-db :device/db :as dbs}]
        (p/do
          (sut/schedule! dbs "error-task" {:word-id "word-1"})

          (#'sut/run-cycle! dbs)

          (p/let [tasks (get-tasks-by-type device-db "error-task")]
            (is (= 1 (count tasks)))
            (is (= 1 (:attempts (first tasks))))))))))


(deftest run-cycle-stores-last-error-on-retry-result
  (async-testing "`run-cycle!` stores last-error when handler returns retry details"
    (with-mocked-env {:now-ms 1000}
      (fn [{device-db :device/db :as dbs}]
        (p/do
          (sut/schedule! dbs "detail-fail-task" {:word-id "word-1"})

          (#'sut/run-cycle! dbs)

          (p/let [tasks (get-tasks-by-type device-db "detail-fail-task")
                  task  (first tasks)]
            (is (= 1 (count tasks)))
            (is (= 1 (:attempts task)))
            (is (= "No credits" (:last-error task)))))))))


(deftest run-cycle-clears-stale-last-error-on-blank-failure
  (async-testing "`run-cycle!` clears old last-error when next failure has no message"
    (with-mocked-env {:now-ms 1000}
      (fn [{device-db :device/db :as dbs}]
        (p/do
          (db/insert device-db
                     {:_id        "task-with-old-error"
                      :type       "task"
                      :task-type  "fail-task"
                      :data       {:word-id "word-1"}
                      :attempts   1
                      :run-at     (utils/ms->iso 1000)
                      :created-at (utils/ms->iso 0)
                      :last-error "Old error"})

          (#'sut/run-cycle! dbs)

          (p/let [task (db/get device-db "task-with-old-error")]
            (is (= 2 (:attempts task)))
            (is (nil? (:last-error task)))))))))


(deftest run-cycle-dead-letters-after-retry-limit
  (async-testing "`run-cycle!` dead-letters retryable tasks at max attempts"
    (with-mocked-env {:now-ms 1000
                      :now-iso "2024-01-01T00:00:01.000Z"}
      (fn [{device-db :device/db :as dbs}]
        (p/do
          (db/insert device-db
                     {:_id        "task-near-limit"
                      :type       "task"
                      :task-type  "fail-task"
                      :data       {:word-id "word-1"}
                      :attempts   4
                      :run-at     (utils/ms->iso 1000)
                      :created-at (utils/ms->iso 0)
                      :last-error "Old error"})

          (#'sut/run-cycle! dbs)

          (p/let [tasks (get-tasks-by-type device-db "fail-task")
                  task  (first tasks)]
            (is (= 1 (count tasks)))
            (is (= 5 (:attempts task)))
            (is (= "failed" (:status task)))
            (is (= "retry-limit-reached" (:failure-reason task)))
            (is (= "2024-01-01T00:00:01.000Z" (:failed-at task)))
            (is (nil? (:run-at task)))
            (is (nil? (:last-error task)))))))))


(deftest run-cycle-dead-letters-unknown-task-types
  (async-testing "`run-cycle!` dead-letters unknown task types"
    (with-mocked-env {}
      (fn [{device-db :device/db :as dbs}]
        (p/do
          (sut/schedule! dbs "unknown-task" {:word-id "word-1"})
          (sut/schedule! dbs "unknown-task" {:word-id "word-2"})

          (#'sut/run-cycle! dbs)

          (p/let [dead-letters (get-tasks-by-status device-db "failed")]
            (is (= 2 (count dead-letters)))
            (is (every? #(= "unknown-task-type" (:failure-reason %)) dead-letters))))))))


(deftest run-cycle-skips-when-offline
  (async-testing "`run-cycle!` skips processing when offline"
    (with-mocked-env {:online? false :now 1000}
      (fn [{device-db :device/db :as dbs}]
        (p/do
          (sut/schedule! dbs "succeed-task" {:word-id "word-1"})

          (#'sut/run-cycle! dbs)

          (p/let [task  (get-task-by-id device-db "word-1")
                  tasks (get-tasks-by-type device-db "succeed-task")]
            (is (some? task))
            (is (= 1 (count tasks)))))))))


(deftest run-cycle-reacts-to-stop-signal
  (async-testing "`run-cycle!` reacts to stop signal"
    (with-mocked-env {}
      (fn [{device-db :device/db :as dbs}]
        (p/do
          (sut/schedule! dbs "succeed-task" {:word-id "word-1"})

          (sut/stop!)

          (#'sut/run-cycle! dbs)

          (p/let [task  (get-task-by-id device-db "word-1")
                  tasks (get-tasks-by-type device-db "succeed-task")]
            (is (some? task))
            (is (= 1 (count tasks)))))))))


(deftest run-cycle-only-processes-due-tasks
  (async-testing "`run-cycle!` only processes due tasks"
    (with-mocked-env {:now-ms 1000}
      (fn [{device-db :device/db :as dbs}]
        (p/do
          ;; Future task (not due yet)
          (db/insert device-db
                     {:type       "task"
                      :task-type  "succeed-task"
                      :data       {:word-id "future-word"}
                      :run-at     (utils/ms->iso 9999)
                      :created-at (utils/ms->iso 0)
                      :attempts   0})

          ;; Due task
          (sut/schedule! dbs "succeed-task" {:word-id "now-word"})

          (#'sut/run-cycle! dbs)

          (p/let [tasks (get-tasks-by-type device-db "succeed-task")]
            (is (= 1 (count tasks)))
            (is (= "future-word" (get-in (first tasks) [:data :word-id])))))))))


;; =============================================================================
;; Integration Tests: Eager Dispatch
;; =============================================================================


(deftest schedule-task-triggers-immediate-execution
  (async-testing "`schedule!` triggers flush which processes the task"
    (with-mocked-env {}
      (fn [{device-db :device/db :as dbs}]
        ;; Put dbs in state so flush! can find them
        (reset! @#'sut/state {:enabled? true :dbs dbs})
        (p/do
          ;; schedule! returns immediately after insert (fire-and-forget flush)
          (sut/schedule! dbs "succeed-task" {:word-id "eager-word"})
          ;; Give the fire-and-forget flush time to complete
          (p/delay 100)
          (p/let [tasks (get-tasks-by-type device-db "succeed-task")]
            (is (empty? tasks) "Task should be processed immediately after creation")))))))

(ns client.tasks-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [client.support.db-fixtures :as db-fixtures]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [tasks :as sut]
   [utils :as utils]))


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
      (^:async fn [db]
        (let [dbs {:device/db db :user/db db}]
          (with-redefs [db/use        (constantly db)
                        utils/now-ms  (constantly now-ms)
                        utils/now-iso (constantly now-iso)
                        sut/online?   (constantly online?)]
            (try
              (await (db/create-index
                      db
                      [:type :run-at :created-at]
                      {:name "by-type-run-at-created-at"
                       :ddoc "by-type-run-at-created-at"}))
              (catch :default _ nil))
            (await (f dbs))))))))


(defn ^:async get-docs
  "Returns all task docs from test db."
  [db]
  (:docs (await (db/find db {:selector {:type "task"}}))))


(defn ^:async get-task-by-id
  [db task-id]
  (let [tasks (await (get-docs db))]
    (first
     (filter
      (fn [task]
        (or (= task-id (:_id task))
            (= task-id (get-in task [:data :word-id]))))
      tasks))))


(defn ^:async get-tasks-by-type
  [db task-type]
  (let [tasks (await (get-docs db))]
    (filter #(= task-type (:task-type %)) tasks)))


(defn ^:async get-tasks-by-status
  [db status]
  (let [tasks (await (get-docs db))]
    (filter #(= status (:status %)) tasks)))


(defmethod sut/execute-task "succeed-task"
  [_task _dbs]
  (js/Promise.resolve true))


(defmethod sut/execute-task "fail-task"
  [_task _dbs]
  (js/Promise.resolve false))


(defmethod sut/execute-task "hinted-fail-task"
  [_task _dbs]
  (js/Promise.resolve {:retry-after-ms 2500}))


(defmethod sut/execute-task "error-task"
  [_task _dbs]
  (js/Promise.reject (ex-info "Boom!" {})))


(def ^:private handled-tasks (atom []))


(defmethod sut/execute-task "tracking-task"
  [task _dbs]
  (swap! handled-tasks conj (:_id task))
  (js/Promise.resolve true))


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


(deftest backoff-increases-exponentially
  (is (= 1000 (#'sut/backoff-ms 0)))
  (is (= 2000 (#'sut/backoff-ms 1)))
  (is (= 4000 (#'sut/backoff-ms 2)))
  (is (= 8000 (#'sut/backoff-ms 3))))


(deftest backoff-caps-at-max
  (is (= 60000 (#'sut/backoff-ms 10)))
  (is (= 60000 (#'sut/backoff-ms 100))))


(deftest run-cycle-with-empty-queue-completes
  (async-testing "`run-cycle!` succeeds when queue is empty"
    (with-mocked-env {}
      (^:async fn [{device-db :device/db :as dbs}]
        (await (#'sut/run-cycle! dbs))
        (let [docs (await (get-docs device-db))]
          (is (empty? docs)))))))


(deftest run-cycle-removes-successful-tasks
  (async-testing "`run-cycle!` removes tasks after success"
    (with-mocked-env {}
      (^:async fn [{device-db :device/db :as dbs}]
        (await (sut/create-task! "succeed-task" {:word-id "word-1"}))
        (await (sut/create-task! "succeed-task" {:word-id "word-2"}))
        (await (sut/create-task! "succeed-task" {:word-id "word-3"}))
        (await (#'sut/run-cycle! dbs))
        (let [tasks (await (get-tasks-by-type device-db "succeed-task"))]
          (is (empty? tasks)))))))


(deftest run-cycle-tracks-handled-tasks
  (async-testing "`run-cycle!` invokes handler for each task"
    (with-mocked-env {}
      (^:async fn [dbs]
        (reset! handled-tasks [])
        (await (sut/create-task! "tracking-task" {:word-id "word-1"}))
        (await (sut/create-task! "tracking-task" {:word-id "word-2"}))
        (await (#'sut/run-cycle! dbs))
        (is (= 2 (count @handled-tasks)))))))


(deftest run-cycle-marks-failed-tasks-for-retry
  (async-testing "`run-cycle!` schedules retry on failure"
    (with-mocked-env {:now-ms 1000}
      (^:async fn [{device-db :device/db :as dbs}]
        (await (sut/create-task! "fail-task" {:word-id "word-1"}))
        (await (#'sut/run-cycle! dbs))
        (let [tasks (await (get-tasks-by-type device-db "fail-task"))
              task  (first tasks)]
          (is (= 1 (count tasks)))
          (is (= 1 (:attempts task)))
          (is (> (utils/iso->ms (:run-at task)) 1000)))))))


(deftest run-cycle-uses-retry-hint-when-provided
  (async-testing "`run-cycle!` uses retry-after hints from task handlers"
    (with-mocked-env {:now-ms 1000}
      (^:async fn [{device-db :device/db :as dbs}]
        (await (sut/create-task! "hinted-fail-task" {:word-id "word-1"}))
        (await (#'sut/run-cycle! dbs))
        (let [tasks (await (get-tasks-by-type device-db "hinted-fail-task"))
              task  (first tasks)]
          (is (= 1 (count tasks)))
          (is (= 1 (:attempts task)))
          (is (= 3500 (utils/iso->ms (:run-at task)))))))))


(deftest run-cycle-handles-task-exceptions
  (async-testing "`run-cycle!` catches handler exceptions"
    (with-mocked-env {:now-ms 1000}
      (^:async fn [{device-db :device/db :as dbs}]
        (await (sut/create-task! "error-task" {:word-id "word-1"}))
        (await (#'sut/run-cycle! dbs))
        (let [tasks (await (get-tasks-by-type device-db "error-task"))]
          (is (= 1 (count tasks)))
          (is (= 1 (:attempts (first tasks)))))))))


(deftest run-cycle-dead-letters-unknown-task-types
  (async-testing "`run-cycle!` dead-letters unknown task types"
    (with-mocked-env {}
      (^:async fn [{device-db :device/db :as dbs}]
        (await (sut/create-task! "unknown-task" {:word-id "word-1"}))
        (await (sut/create-task! "unknown-task" {:word-id "word-2"}))
        (await (#'sut/run-cycle! dbs))
        (let [dead-letters (await (get-tasks-by-status device-db "failed"))]
          (is (= 2 (count dead-letters)))
          (is (every? #(= "unknown-task-type" (:failure-reason %)) dead-letters)))))))


(deftest run-cycle-skips-when-offline
  (async-testing "`run-cycle!` skips processing when offline"
    (with-mocked-env {:online? false :now 1000}
      (^:async fn [{device-db :device/db :as dbs}]
        (await (sut/create-task! "succeed-task" {:word-id "word-1"}))
        (await (#'sut/run-cycle! dbs))
        (let [task  (await (get-task-by-id device-db "word-1"))
              tasks (await (get-tasks-by-type device-db "succeed-task"))]
          (is (some? task))
          (is (= 1 (count tasks))))))))


(deftest run-cycle-reacts-to-stop-signal
  (async-testing "`run-cycle!` reacts to stop signal"
    (with-mocked-env {}
      (^:async fn [{device-db :device/db :as dbs}]
        (await (sut/create-task! "succeed-task" {:word-id "word-1"}))
        (sut/stop!)
        (await (#'sut/run-cycle! dbs))
        (let [task  (await (get-task-by-id device-db "word-1"))
              tasks (await (get-tasks-by-type device-db "succeed-task"))]
          (is (some? task))
          (is (= 1 (count tasks))))))))


(deftest run-cycle-only-processes-due-tasks
  (async-testing "`run-cycle!` only processes due tasks"
    (with-mocked-env {:now-ms 1000}
      (^:async fn [{device-db :device/db :as dbs}]
        (await (db/insert device-db
                          {:type       "task"
                           :task-type  "succeed-task"
                           :data       {:word-id "future-word"}
                           :run-at     (utils/ms->iso 9999)
                           :created-at (utils/ms->iso 0)
                           :attempts   0}))
        (await (sut/create-task! "succeed-task" {:word-id "now-word"}))
        (await (#'sut/run-cycle! dbs))
        (let [tasks (await (get-tasks-by-type device-db "succeed-task"))]
          (is (= 1 (count tasks)))
          (is (= "future-word" (get-in (first tasks) [:data :word-id]))))))))


(deftest create-task-triggers-immediate-execution
  (async-testing "`create-task!` triggers flush which processes the task"
    (with-mocked-env {}
      (^:async fn [{device-db :device/db :as dbs}]
        (reset! @#'sut/state {:enabled? true :dbs dbs})
        (await (sut/create-task! "succeed-task" {:word-id "eager-word"}))
        (await (js/Promise. (fn [res] (js/setTimeout res 100))))
        (let [tasks (await (get-tasks-by-type device-db "succeed-task"))]
          (is (empty? tasks) "Task should be processed immediately after creation"))))))

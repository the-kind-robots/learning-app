(ns client.tasks-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.wait :as wait]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [db-migrations :as db-migrations]
   [db.pouch :as pouch]
   [ports.task-queue :as task-queue]
   [tasks :as sut]
   [utils :as utils]))


(def test-db-name (db-fixtures/db-name "client.tasks-test"))


(def user-db-name
  "user-db for the tests that open the databases the way the app does."
  (db-fixtures/db-name "client.tasks-test.app-user"))


(def device-db-name
  "device-db for the tests that open the databases the way the app does."
  (db-fixtures/db-name "client.tasks-test.app-device"))


(use-fixtures
 :each
 (db-fixtures/db-fixture-multi [test-db-name user-db-name device-db-name])
 {:before (fn [] (reset! @#'sut/state {:enabled? true}))
  :after  sut/stop!})


(defn- test-clock
  []
  {:clock/now-iso utils/now-iso
   :clock/now-ms  utils/now-ms})


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
        task    (sut/create-task "task:my-type:word-123" "my-type" {:word-id "word-123"} now-iso)]
    (is (nil? (:type task)) "the engine stamps the type from tasks/schema on insert")
    (is (= "task" (:type sut/schema)))
    (is (= "task:my-type:word-123" (:_id task))
        "the caller names the work, so asking twice writes once")
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
        (await (#'sut/run-cycle! dbs (test-clock)))
        (let [docs (await (get-docs device-db))]
          (is (empty? docs)))))))


(deftest run-cycle-removes-successful-tasks
  (async-testing "`run-cycle!` removes tasks after success"
    (with-mocked-env {}
      (^:async fn [{device-db :device/db :as dbs}]
        (await (sut/create-tasks! dbs (test-clock) "succeed-task" [{:id (str "succeed-task" ":" "word-1") :data {:word-id "word-1"}}]))
        (await (sut/create-tasks! dbs (test-clock) "succeed-task" [{:id (str "succeed-task" ":" "word-2") :data {:word-id "word-2"}}]))
        (await (sut/create-tasks! dbs (test-clock) "succeed-task" [{:id (str "succeed-task" ":" "word-3") :data {:word-id "word-3"}}]))
        (await (#'sut/run-cycle! dbs (test-clock)))
        (let [tasks (await (get-tasks-by-type device-db "succeed-task"))]
          (is (empty? tasks)))))))


(deftest run-cycle-tracks-handled-tasks
  (async-testing "`run-cycle!` invokes handler for each task"
    (with-mocked-env {}
      (^:async fn [dbs]
        (reset! handled-tasks [])
        (await (sut/create-tasks! dbs (test-clock) "tracking-task" [{:id (str "tracking-task" ":" "word-1") :data {:word-id "word-1"}}]))
        (await (sut/create-tasks! dbs (test-clock) "tracking-task" [{:id (str "tracking-task" ":" "word-2") :data {:word-id "word-2"}}]))
        (await (#'sut/run-cycle! dbs (test-clock)))
        (is (= 2 (count @handled-tasks)))))))


(deftest run-cycle-marks-failed-tasks-for-retry
  (async-testing "`run-cycle!` schedules retry on failure"
    (with-mocked-env {:now-ms 1000}
      (^:async fn [{device-db :device/db :as dbs}]
        (await (sut/create-tasks! dbs (test-clock) "fail-task" [{:id (str "fail-task" ":" "word-1") :data {:word-id "word-1"}}]))
        (await (#'sut/run-cycle! dbs (test-clock)))
        (let [tasks (await (get-tasks-by-type device-db "fail-task"))
              task  (first tasks)]
          (is (= 1 (count tasks)))
          (is (= 1 (:attempts task)))
          (is (> (utils/iso->ms (:run-at task)) 1000)))))))


(deftest run-cycle-uses-retry-hint-when-provided
  (async-testing "`run-cycle!` uses retry-after hints from task handlers"
    (with-mocked-env {:now-ms 1000}
      (^:async fn [{device-db :device/db :as dbs}]
        (await (sut/create-tasks! dbs (test-clock) "hinted-fail-task" [{:id (str "hinted-fail-task" ":" "word-1") :data {:word-id "word-1"}}]))
        (await (#'sut/run-cycle! dbs (test-clock)))
        (let [tasks (await (get-tasks-by-type device-db "hinted-fail-task"))
              task  (first tasks)]
          (is (= 1 (count tasks)))
          (is (= 1 (:attempts task)))
          (is (= 3500 (utils/iso->ms (:run-at task)))))))))


(deftest run-cycle-handles-task-exceptions
  (async-testing "`run-cycle!` catches handler exceptions"
    (with-mocked-env {:now-ms 1000}
      (^:async fn [{device-db :device/db :as dbs}]
        (await (sut/create-tasks! dbs (test-clock) "error-task" [{:id (str "error-task" ":" "word-1") :data {:word-id "word-1"}}]))
        (await (#'sut/run-cycle! dbs (test-clock)))
        (let [tasks (await (get-tasks-by-type device-db "error-task"))]
          (is (= 1 (count tasks)))
          (is (= 1 (:attempts (first tasks)))))))))


(deftest run-cycle-dead-letters-unknown-task-types
  (async-testing "`run-cycle!` dead-letters unknown task types"
    (with-mocked-env {}
      (^:async fn [{device-db :device/db :as dbs}]
        (await (sut/create-tasks! dbs (test-clock) "unknown-task" [{:id (str "unknown-task" ":" "word-1") :data {:word-id "word-1"}}]))
        (await (sut/create-tasks! dbs (test-clock) "unknown-task" [{:id (str "unknown-task" ":" "word-2") :data {:word-id "word-2"}}]))
        (await (#'sut/run-cycle! dbs (test-clock)))
        (let [dead-letters (await (get-tasks-by-status device-db "failed"))]
          (is (= 2 (count dead-letters)))
          (is (every? #(= "unknown-task-type" (:failure-reason %)) dead-letters)))))))


(deftest run-cycle-skips-when-offline
  (async-testing "`run-cycle!` skips processing when offline"
    (with-mocked-env {:online? false :now 1000}
      (^:async fn [{device-db :device/db :as dbs}]
        (await (sut/create-tasks! dbs (test-clock) "succeed-task" [{:id (str "succeed-task" ":" "word-1") :data {:word-id "word-1"}}]))
        (await (#'sut/run-cycle! dbs (test-clock)))
        (let [task  (await (get-task-by-id device-db "word-1"))
              tasks (await (get-tasks-by-type device-db "succeed-task"))]
          (is (some? task))
          (is (= 1 (count tasks))))))))


(deftest run-cycle-reacts-to-stop-signal
  (async-testing "`run-cycle!` reacts to stop signal"
    (with-mocked-env {}
      (^:async fn [{device-db :device/db :as dbs}]
        (await (sut/create-tasks! dbs (test-clock) "succeed-task" [{:id (str "succeed-task" ":" "word-1") :data {:word-id "word-1"}}]))
        (sut/stop!)
        (await (#'sut/run-cycle! dbs (test-clock)))
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
        (await (sut/create-tasks! dbs (test-clock) "succeed-task" [{:word-id "now-word"}]))
        (await (#'sut/run-cycle! dbs (test-clock)))
        (let [tasks (await (get-tasks-by-type device-db "succeed-task"))]
          (is (= 1 (count tasks)))
          (is (= "future-word" (get-in (first tasks) [:data :word-id]))))))))


(deftest create-task-triggers-immediate-execution
  (async-testing "`create-tasks!` triggers flush which processes the task"
    (with-mocked-env {}
      (^:async fn [{device-db :device/db :as dbs}]
        (reset! @#'sut/state {:enabled? true :dbs dbs :clock (test-clock)})
        (await (sut/create-tasks! dbs (test-clock) "succeed-task" [{:word-id "eager-word"}]))
        (await (js/Promise. (fn [res] (js/setTimeout res 100))))
        (let [tasks (await (get-tasks-by-type device-db "succeed-task"))]
          (is (empty? tasks) "Task should be processed immediately after creation"))))))


(def ^:private throttled-runs
  "Ids of the throttle-once tasks in the order the queue started them."
  (atom []))


(defmethod sut/execute-task "throttle-once-task"
  [task _dbs]
  (swap! throttled-runs conj (:_id task))
  (js/Promise.resolve (if (= 1 (count @throttled-runs))
                        {:retry-after-ms 300}
                        true)))


(defn- sleep
  [ms]
  (js/Promise. (fn [res] (js/setTimeout res ms))))


(defn ^:async wait-until
  "Waits until `done?` answers true or three seconds pass."
  [done?]
  (let [waited (atom 0)]
    (while (and (not (done?)) (< @waited 3000))
      (await (sleep 20))
      (swap! waited + 20))))


(deftest throttled-answer-pauses-the-whole-queue
  (async-testing "a throttled answer stops the queue until Retry-After elapses, then it drains"
    (with-mocked-env {}
      (^:async fn
       [{device-db :device/db :as dbs}]
       (reset! throttled-runs [])
       (let [now   (atom 1000)
             clock {:clock/now-iso #(utils/ms->iso @now)
                    :clock/now-ms  #(deref now)}]
         (reset! @#'sut/state {:enabled? true :dbs dbs :clock clock})
         (await (sut/create-tasks! dbs
                                   clock
                                   "throttle-once-task"
                                   (mapv (fn [i] {:id (str "throttle-once-task:" i) :data {}})
                                         (range 10))))
         (await (sleep 300))
         (is (= 3 (count @throttled-runs))
             "only the tasks already started when the answer came are run")
         (let [waiting (await (get-tasks-by-type device-db "throttle-once-task"))
               started (set @throttled-runs)]
           (is (= 8 (count waiting)) "the throttled task stays queued")
           (is (every? #(= (:created-at %) (:run-at %))
                       (remove #(started (:_id %)) waiting))
               "the tasks never started keep their run-at"))
         (sut/flush!)
         (await (sut/create-tasks! dbs clock "throttle-once-task" [{:id "throttle-once-task:late" :data {}}]))
         (await (sleep 400))
         (is (= 3 (count @throttled-runs)) "an earlier flush does not end the wait")
         (reset! now 1300)
         (await (wait-until #(= 12 (count @throttled-runs))))
         (is (= 12 (count @throttled-runs))
             "after the wait the queue resumes on its own and runs everything, the throttled task included")
         (is (empty? (await (get-tasks-by-type device-db "throttle-once-task")))))))))


(defn- deferred
  "A promise and the function that resolves it."
  []
  (let [resolve (atom nil)]
    {:promise (js/Promise. #(reset! resolve %))
     :resolve #(@resolve nil)}))


(defn- ^:async with-app-dbs
  "Calls `f` with a user-db and a device-db of their own, the runner
   stopped, and the browser reporting that it is online."
  [f]
  (reset! @#'sut/state {})
  (with-redefs [sut/online? (constantly true)]
    (await (f {:device/db (db/use device-db-name)
               :user/db   (db/use user-db-name)}))))


(defn- start-runner!
  "Starts the runner as the app does, with memory loaded once `loaded`
   resolves."
  [dbs loaded]
  (task-queue/start! {:clock   (test-clock)
                      :db      dbs
                      :learner {:learner/loaded (constantly loaded)}}))


(deftest the-queue-index-is-built-once-memory-is-loaded
  (async-testing "start builds no index; the runner builds its own once memory is loaded, in device-db only"
    (await
     (with-app-dbs
      (^:async fn
       [{device :device/db user :user/db}]
       (with-redefs [db/use #(case %
                               "user-db"   user
                               "device-db" device)
                     db-migrations/ensure-migrated! #(js/Promise.resolve nil)]
         (let [dbs (await (pouch/init!))
               {:keys [promise resolve]} (deferred)]
           (is (empty? (await (db-queries/index-names user))) "user-db has no index after start")
           (is (empty? (await (db-queries/index-names device))) "device-db has no index after start")
           (start-runner! dbs promise)
           ;; Nothing is expected to happen here, so there is nothing to wait
           ;; for but time.
           (await (sleep 200))
           (is (empty? (await (db-queries/index-names device))) "no index while memory loads")
           (is (not (:enabled? @@#'sut/state)) "the runner waits while memory loads")
           (resolve)
           (await (wait/until #(:enabled? @@#'sut/state)))
           (is (= #{"by-type-run-at-created-at"} (await (db-queries/index-names device))))
           (is (empty? (await (db-queries/index-names user))) "user-db still has no index"))))))))


(deftest a-due-task-waits-until-memory-is-loaded
  (async-testing "the runner starts a due task only once memory is loaded"
    (await
     (with-app-dbs
      (^:async fn
       [{device :device/db :as dbs}]
       (reset! handled-tasks [])
       (await (sut/create-tasks! dbs (test-clock) "tracking-task" [{:id "tracking-task:due" :data {}}]))
       (let [{:keys [promise resolve]} (deferred)]
         (start-runner! dbs promise)
         ;; Nothing is expected to happen here either.
         (await (sleep 200))
         (is (empty? @handled-tasks) "no task runs while memory loads")
         (resolve)
         (await (wait/until #(and (seq @handled-tasks) (not (:running? @@#'sut/state)))))
         (is (= ["tracking-task:due"] @handled-tasks))
         (is (empty? (await (get-docs device))) "the task ran and is gone")))))))


(deftest a-stop-before-the-runner-has-started-keeps-it-stopped
  (async-testing "a stop while memory loads, or while the index is built: the runner stays stopped"
    (await
     (with-app-dbs
      (^:async fn
       [dbs]
       (loop [cases [{:label "while memory loads" :loaded-before-stop? false}
                     {:label "while the index is built" :loaded-before-stop? true}]]
         (when-let [[{:keys [label loaded-before-stop?]} & more] (seq cases)]
           (let [memory (deferred)
                 index  (deferred)
                 asked  (atom 0)]
             (with-redefs [pouch/ensure-index! (fn [_dbs _schema _index]
                                                 (swap! asked inc)
                                                 (:promise index))]
               (start-runner! dbs (:promise memory))
               (when loaded-before-stop?
                 ((:resolve memory))
                 (await (wait/until #(pos? @asked))))
               (sut/stop!)
               ((:resolve memory))
               ((:resolve index))
               (await (wait/settled))
               (is (not (:enabled? @@#'sut/state)) label)
               (when-not loaded-before-stop?
                 (is (zero? @asked) (str label ": no index is built")))))
           (recur more))))))))


(deftest a-failed-index-build-keeps-the-runner-stopped
  (async-testing "the index cannot be built: the runner stays stopped"
    (await
     (with-app-dbs
      (^:async fn
       [dbs]
       (with-redefs [pouch/ensure-index! (fn [_dbs _schema _index] (js/Promise.reject (js/Error. "quota")))]
         (start-runner! dbs (js/Promise.resolve nil))
         (await (wait/settled)))
       (is (not (:enabled? @@#'sut/state))))))))


(deftest a-flush-survives-a-rejection-that-is-not-an-error
  (async-testing "a query that rejects with a plain object ends the cycle, and the queue can run again"
    (await
     (with-app-dbs
      (^:async fn
       [dbs]
       (reset! @#'sut/state {:enabled? true :dbs dbs :clock (test-clock)})
       (with-redefs [sut/fetch-due-tasks (fn [& _] (js/Promise.reject #js {:error "no_usable_index"}))]
         (sut/flush!)
         (await (wait/until #(not (:running? @@#'sut/state)))))
       (is (:enabled? @@#'sut/state)))))))

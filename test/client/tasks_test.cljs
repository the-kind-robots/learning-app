(ns client.tasks-test
  "The task queue seen from outside: what a queued task becomes once the
   runner has had it. Time is a clock the test holds; the one boundary
   stubbed is the browser's online flag."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [client.support.db-fixtures :as db-fixtures]
   [client.support.wait :as wait]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [ports.task-queue :as task-queue]
   [tasks :as sut]
   [utils :as utils]))


(def ^:private run-id (js/Date.now))


(def ^:private test-db-name
  "A database of its own for each test: the queue may still be finishing a
   query when its test ends, and a database destroyed under it hangs."
  (atom nil))


(use-fixtures
 :each
 {:before (fn [] (reset! test-db-name (db-fixtures/db-name (str "client.tasks-test." run-id "." (gensym)))))
  :after  (fn [] (db-fixtures/destroy-test-db @test-db-name) nil)})


(def ^:private handled
  "Ids of the tracking tasks the queue ran, with the clock reading each started at."
  (atom []))


(defmethod sut/execute-task "succeed-task"
  [_task _env]
  (js/Promise.resolve true))


(defmethod sut/execute-task "fail-task"
  [_task _env]
  (js/Promise.resolve false))


(defmethod sut/execute-task "hinted-fail-task"
  [_task _env]
  (js/Promise.resolve {:retry-after-ms 2500}))


(defmethod sut/execute-task "error-task"
  [_task _env]
  (js/Promise.reject (ex-info "Boom!" {})))


(def ^:private clock-now
  "The clock reading in ms; the test moves it."
  (atom 1000))


(defmethod sut/execute-task "tracking-task"
  [task _env]
  (swap! handled conj [(:_id task) @clock-now])
  (js/Promise.resolve true))


(def ^:private throttle-runs
  "Ids of the throttle-once tasks in the order the queue started them, with the clock reading."
  (atom []))


(defmethod sut/execute-task "throttle-once-task"
  [task _env]
  (swap! throttle-runs conj [(:_id task) @clock-now])
  (js/Promise.resolve (if (= 1 (count @throttle-runs))
                        {:retry-after-ms 300}
                        true)))


(defn- set-online!
  "The browser's online flag, the one thing the queue asks of its host."
  [online?]
  (aset js/globalThis "self" #js {:navigator #js {:onLine online?}}))


(def ^:private clock
  {:clock/now-iso #(utils/ms->iso @clock-now)
   :clock/now-ms  #(deref clock-now)})


(defn- ^:async with-queue
  "Runs `f` with the db the queue works on and a started runner, the clock at
   1000 ms, the browser online."
  [f]
  (reset! handled [])
  (reset! clock-now 1000)
  (set-online! true)
  (await
   (db-fixtures/with-test-db
     @test-db-name
     (^:async fn [db]
       (let [dbs {:device/db db :user/db db}]
         (try
           (await (sut/start! dbs clock (js/Promise.resolve)))
           (await (f dbs db))
           (finally
            (sut/stop!))))))))


(defn- queue
  [dbs task-type ids]
  (sut/create-tasks! dbs clock task-type (mapv (fn [id] {:id (str "task:" id) :data {:word-id id}}) ids)))


(defn- ^:async tasks
  [db]
  (let [{rows :rows} (await (db/all-docs db {:startkey "task:" :endkey "task:\ufff0" :include-docs true}))]
    (mapv :doc rows)))


(defn- ^:async queue-drained
  [db]
  (wait/until (^:async fn [] (empty? (await (tasks db))))))


(defn- ^:async task-where
  "Waits for the one task of `id` to satisfy `pred` and returns it."
  [db id pred]
  (let [found (atom nil)]
    (await (wait/until
            (^:async fn []
              (reset! found (first (filter #(and (= (str "task:" id) (:_id %)) (pred %))
                                           (await (tasks db)))))
              @found)))
    @found))


(deftest a-task-that-succeeds-leaves-the-queue
  (async-testing "success removes the task"
    (with-queue
      (^:async fn [dbs db]
        (await (queue dbs "succeed-task" ["a" "b" "c"]))
        (await (queue-drained db))
        (is (empty? (await (tasks db))))))))


(deftest a-task-that-fails-is-scheduled-for-a-retry
  (async-testing "failure and a thrown error both keep the task, one attempt older, due later"
    (with-queue
      (^:async fn [dbs db]
        (await (queue dbs "fail-task" ["f"]))
        (await (queue dbs "error-task" ["e"]))
        (doseq [id ["f" "e"]]
          (let [task (await (task-where db id #(= 1 (:attempts %))))]
            (is (= 3000 (utils/iso->ms (:run-at task))) id)))))))


(deftest a-retry-is-delayed-by-the-attempts-so-far-up-to-a-cap
  (async-testing "the delay doubles from one second and stops at a minute"
    (with-queue
      (^:async fn [_dbs db]
        (doseq [attempts [0 1 3 10 100]]
          (await (db/insert db {:_id        (str "task:n" attempts)
                                :type       "task"
                                :task-type  "fail-task"
                                :data       {}
                                :attempts   attempts
                                :run-at     (utils/ms->iso 0)
                                :created-at (utils/ms->iso 0)})))
        (sut/resume!)
        (doseq [[attempts delay] [[0 2000] [1 4000] [3 16000] [10 60000] [100 60000]]]
          (let [task (await (task-where db (str "n" attempts) #(= (inc attempts) (:attempts %))))]
            (is (= (+ 1000 delay) (utils/iso->ms (:run-at task))) (str attempts " attempts"))))))))


(deftest a-retry-hint-from-the-handler-sets-the-retry-time
  (async-testing "the task is due when the handler said, not by the backoff"
    (with-queue
      (^:async fn [dbs db]
        (await (queue dbs "hinted-fail-task" ["h"]))
        (let [task (await (task-where db "h" #(= 1 (:attempts %))))]
          (is (= 3500 (utils/iso->ms (:run-at task)))))))))


(deftest a-task-of-an-unknown-type-goes-to-the-dead-letters
  (async-testing "it is not retried; it is marked failed with the reason"
    (with-queue
      (^:async fn [dbs db]
        (await (queue dbs "unknown-task" ["u1" "u2"]))
        (doseq [id ["u1" "u2"]]
          (let [task (await (task-where db (str id ":failed") #(= "failed" (:status %))))]
            (is (= "unknown-task-type" (:failure-reason task)))))))))


(deftest nothing-runs-while-the-device-is-offline
  (async-testing "tasks wait in the queue, and run when the device is back"
    (with-queue
      (^:async fn [dbs db]
        (set-online! false)
        (await (queue dbs "tracking-task" ["o"]))
        (is (empty? @handled))
        (is (= 1 (count (await (tasks db)))))
        (set-online! true)
        (sut/resume!)
        (await (queue-drained db))
        (is (= [["task:o" 1000]] @handled))))))


(deftest only-due-tasks-run
  (async-testing "a task scheduled for later stays until its time comes"
    (with-queue
      (^:async fn [dbs db]
        (await (sut/create-tasks! dbs clock "tracking-task"
                                  [{:id "task:later" :data {} :delay-ms 5000}
                                   {:id "task:now" :data {}}]))
        (await (wait/until (fn [] (seq @handled))))
        (await (wait/until (^:async fn [] (= ["task:later"] (map :_id (await (tasks db)))))))
        (is (= ["task:now"] (map first @handled)))
        (reset! clock-now 6000)
        (sut/resume!)
        (await (queue-drained db))
        (is (= ["task:now" "task:later"] (map first @handled)))))))


(deftest asking-for-the-same-work-twice-queues-it-once
  (async-testing "the second request neither fails nor duplicates"
    (with-queue
      (^:async fn [dbs db]
        (set-online! false)
        (await (queue dbs "tracking-task" ["same"]))
        (await (queue dbs "tracking-task" ["same"]))
        (is (= 1 (count (await (tasks db)))))))))


(deftest a-throttled-answer-pauses-the-whole-queue-until-the-wait-is-over
  (async-testing "no task starts before the Retry-After has passed on the clock; then all run"
    (with-queue
      (^:async fn [dbs db]
        (reset! throttle-runs [])
        (await (sut/create-tasks! dbs clock "throttle-once-task"
                                  (mapv (fn [i] {:id (str "task:t" i) :data {}}) (range 10))))
        (await (wait/until (fn [] (= 3 (count @throttle-runs)))))
        (await (wait/settled))
        (sut/resume!)
        (await (wait/settled))
        (is (= 3 (count @throttle-runs)) "only the tasks already started when the answer came")
        (reset! clock-now 1300)
        (sut/resume!)
        (await (queue-drained db))
        (is (= 11 (count @throttle-runs)) "every task ran, the throttled one a second time")
        (is (every? #(>= (second %) 1300) (drop 3 @throttle-runs)))))))


(deftest a-due-task-waits-until-memory-is-loaded
  (async-testing "the runner leaves the queue alone while memory loads"
    (set-online! true)
    (reset! handled [])
    (reset! clock-now 1000)
    (await
     (db-fixtures/with-test-db
       @test-db-name
       (^:async fn [db]
         (let [dbs    {:device/db db :user/db db}
               loaded (atom nil)
               memory (js/Promise. (fn [resolve] (reset! loaded resolve)))]
           (try
             (await (queue dbs "tracking-task" ["m"]))
             (task-queue/start! {:clock clock :db dbs :learner {:learner/loaded (constantly memory)}})
             (await (wait/settled))
             (is (empty? @handled) "nothing runs while memory loads")
             (@loaded nil)
             (await (queue-drained db))
             (is (= [["task:m" 1000]] @handled))
             (finally (sut/stop!)))))))))


(deftest a-stop-while-memory-loads-keeps-the-runner-stopped
  (async-testing "a runner stopped before it started never runs a task"
    (set-online! true)
    (reset! handled [])
    (await
     (db-fixtures/with-test-db
       @test-db-name
       (^:async fn [db]
         (let [dbs    {:device/db db :user/db db}
               loaded (atom nil)
               memory (js/Promise. (fn [resolve] (reset! loaded resolve)))]
           (task-queue/start! {:clock clock :db dbs :learner {:learner/loaded (constantly memory)}})
           (sut/stop!)
           (@loaded nil)
           (await (wait/settled))
           (await (queue dbs "tracking-task" ["s"]))
           (await (wait/settled))
           (is (empty? @handled))
           (is (= 1 (count (await (tasks db)))))))))))

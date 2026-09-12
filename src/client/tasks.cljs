(ns tasks
  "Simple task runner: scans DB for tasks, runs them in parallel, pauses when offline."
  (:require
   [db.pouch :as dbs]
   [lambdaisland.glogi :as log]
   [utils :as utils]))


(def ^:private config
  {:max-backoff-ms 60000
   :max-concurrent 3})


(def schema
  {:type    "task"
   :db      :device/db
   :indexes [{:name "by-type-run-at-created-at" :fields [:type :run-at :created-at]}]})


(defn- now-iso
  [clock]
  ((:clock/now-iso clock)))


(defn- now-ms
  [clock]
  ((:clock/now-ms clock)))


(defn create-task
  "Create a task document."
  [task-type data now-iso]
  {:task-type  task-type
   :data       data
   :attempts   0
   :run-at     now-iso
   :created-at now-iso})


(defmulti execute-task
  "Execute a task. Dispatches on :task-type.
   Returns a promise resolving to truthy (success), falsy (retry), or ::unknown-task."
  (fn [task _env] (:task-type task)))


(defmethod execute-task :default
  [task _env]
  (log/warn :tasks/unknown-type {:task-type (:task-type task)})
  ::unknown-task)


(defn- online?
  []
  js/self.navigator.onLine)


(defn- backoff-ms
  [attempts]
  (min (:max-backoff-ms config)
       (* 1000 (Math/pow 2 (max 0 attempts)))))


(def ^:private page-size 50)


(defn ^:async fetch-due-tasks
  [dbs now-iso]
  (let [{:keys [docs]}
        (await (dbs/find dbs
                         schema
                         {:selector  {:run-at     {:$lte now-iso}
                                      :created-at {:$exists true}
                                      :$or        [{:status {:$exists false}}
                                                   {:status {:$ne "failed"}}]}
                          :sort      [{:type :asc}
                                      {:run-at :asc}
                                      {:created-at :asc}]
                          :limit     page-size
                          :use-index "by-type-run-at-created-at"}))]
    (vec docs)))


(defn- mark-failed!
  ([dbs clock task]
   (mark-failed! dbs clock task nil))
  ([dbs clock task retry-after-ms]
   (let [attempts    (inc (or (:attempts task) 0))
         delay-ms    (or retry-after-ms (backoff-ms attempts))
         next-run-ms (+ (now-ms clock) delay-ms)
         next-run    (utils/ms->iso next-run-ms)]
     (dbs/insert dbs schema (assoc task :attempts attempts :run-at next-run)))))


(defn- dead-letter!
  [dbs clock task reason]
  (let [now-iso (now-iso clock)]
    (dbs/insert dbs
                schema
                (assoc task
                       :status         "failed"
                       :failure-reason reason
                       :failed-at      now-iso
                       :run-at         nil))))


(defn- remove-with-latest-rev!
  [dbs task]
  (-> (dbs/remove dbs schema task)
      (.catch (fn ^:async f [err]
                (let [status (or (:status err) (get-in err [:body :status]))]
                  (cond
                    (= status 404) true
                    (= status 409) (let [fresh (await (dbs/get dbs schema (:_id task)))]
                                     (if fresh
                                       (await (dbs/remove dbs schema fresh))
                                       true))
                    :else          true))))))


(defn- run-task!
  [{:keys [clock dbs] :as env} task]
  (-> ((fn ^:async f []
         (let [result (await (execute-task task env))]
           (cond
             (= result ::unknown-task)
             (do
               (log/warn :tasks/dead-letter {:id (:_id task) :reason :unknown-task})
               (await (dead-letter! dbs clock task "unknown-task-type")))

             (:retry-after-ms result)
             (do
               (log/debug :tasks/retrying-with-hint
                          {:id (:_id task)
                           :retry-after-ms (:retry-after-ms result)})
               (await (mark-failed! dbs clock task (:retry-after-ms result))))

             result
             (do
               (log/debug :tasks/completed {:id (:_id task)})
               (await (remove-with-latest-rev! dbs task)))

             :else
             (do
               (log/debug :tasks/failed {:id (:_id task)})
               (await (mark-failed! dbs clock task)))))))
      (.catch (fn [err]
                (log/error :tasks/error {:id (:_id task) :error (str err)})
                (mark-failed! dbs clock task)))))


(defn ^:async run-worker!
  [env queue]
  (let [running (atom true)]
    (while @running
      (if-let [task (let [result (atom nil)]
                      (swap! queue
                        (fn [tasks]
                          (if (seq tasks)
                            (do (reset! result (first tasks)) (subvec tasks 1))
                            tasks)))
                      @result)]
        (await (run-task! env task))
        (reset! running false)))))


(defn ^:async run-workers!
  [env tasks]
  (let [queue (atom (vec tasks))]
    (await (js/Promise.all
            (into-array (repeatedly (:max-concurrent config)
                                    #(run-worker! env queue)))))))


(def ^:private state (atom {}))


(declare flush!)


(defn- schedule-retry!
  [delay-ms]
  (js/setTimeout flush! delay-ms))


(defn ^:async nearest-retry-delay
  [dbs clock]
  (let [{:keys [docs]}
        (await (dbs/find dbs
                         schema
                         {:selector  {:run-at     {:$exists true}
                                      :created-at {:$exists true}
                                      :$or        [{:status {:$exists false}}
                                                   {:status {:$ne "failed"}}]}
                          :sort      [{:type :asc}
                                      {:run-at :asc}
                                      {:created-at :asc}]
                          :limit     1
                          :use-index "by-type-run-at-created-at"}))]
    (when-let [task (first docs)]
      (max 0 (- (utils/iso->ms (:run-at task)) (now-ms clock))))))


(defn ^:async run-cycle!
  [dbs clock]
  (let [running (atom true)]
    (while (and (online?) (:enabled? @state) @running)
      (let [tasks (await (fetch-due-tasks dbs (now-iso clock)))]
        (log/debug :run-cycle/tasks tasks)
        (if (seq tasks)
          (await (run-workers! {:dbs dbs :clock clock} tasks))
          (reset! running false)))))
  (let [delay (await (nearest-retry-delay dbs clock))]
    (when delay
      (log/debug :tasks/scheduling-retry {:delay-ms delay})
      (schedule-retry! delay))))


(defn flush!
  "Trigger a run cycle if enabled and online. Fire-and-forget."
  []
  (when-let [{:keys [clock dbs enabled? running?]} @state]
    (when (and (some? dbs) (some? clock) enabled? (online?) (not running?))
      (swap! state assoc :running? true)
      (-> ((fn ^:async f []
             (try
               (await (run-cycle! dbs clock))
               (catch js/Error err
                 (log/error :tasks/flush-error {:error (str err)}))
               (finally
                (swap! state assoc :running? false)))))
          (.catch identity))))
  nil)


(defn start!
  "Start the task runner. The index it queries by is part of `schema`, which
   the engine installs at start-up."
  [dbs clock]
  (reset! state {:enabled? true :dbs dbs :clock clock})
  (log/info :tasks/starting config)
  (flush!))


(defn stop!
  []
  (reset! state {})
  (log/info :tasks/stopped {}))


(defn resume!
  []
  (log/debug :tasks/resuming {})
  (flush!))


(defn ^:async create-task!
  [dbs clock task-type data]
  (let [now-iso (now-iso clock)]
    (await (dbs/insert dbs schema (create-task task-type data now-iso)))
    (flush!)))

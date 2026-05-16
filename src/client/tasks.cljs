(ns tasks
  "Simple task runner: scans DB for tasks, runs them in parallel, pauses when offline."
  (:require
   [db :as db]
   [dbs :as dbs]
   [lambdaisland.glogi :as log]
   [utils :as utils]))


(def ^:private config
  {:max-backoff-ms 60000
   :max-concurrent 3})


(defn create-task
  "Create a task document."
  [task-type data now-iso]
  {:type       "task"
   :task-type  task-type
   :data       data
   :attempts   0
   :run-at     now-iso
   :created-at now-iso})


(defmulti execute-task
  "Execute a task. Dispatches on :task-type.
   Returns a promise resolving to truthy (success), falsy (retry), or ::unknown-task."
  (fn [task _dbs] (:task-type task)))


(defmethod execute-task :default
  [task _dbs]
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


(defn- ensure-task-index!
  [dbs]
  (-> (db/create-index
       (dbs/db-for dbs "task")
       [:type :run-at :created-at]
       {:name "by-type-run-at-created-at"
        :ddoc "by-type-run-at-created-at"})
      (.catch (fn [err]
                (log/error :tasks/index-error {:error (str err)})))))


(defn ^:async fetch-due-tasks
  [dbs now-iso]
  (let [{:keys [docs]}
        (await (dbs/find dbs
                         {:selector  {:type       "task"
                                      :run-at     {:$lte now-iso}
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
  ([dbs task]
   (mark-failed! dbs task nil))
  ([dbs task retry-after-ms]
   (let [attempts    (inc (or (:attempts task) 0))
         delay-ms    (or retry-after-ms (backoff-ms attempts))
         next-run-ms (+ (utils/now-ms) delay-ms)
         next-run    (utils/ms->iso next-run-ms)]
     (dbs/insert dbs (assoc task :attempts attempts :run-at next-run)))))


(defn- dead-letter!
  [dbs task reason]
  (let [now-iso (utils/now-iso)]
    (dbs/insert dbs
                (assoc task
                       :status         "failed"
                       :failure-reason reason
                       :failed-at      now-iso
                       :run-at         nil))))


(defn- remove-with-latest-rev!
  [dbs task]
  (-> (dbs/remove dbs task)
      (.catch (fn ^:async f [err]
                (let [status (or (:status err) (get-in err [:body :status]))]
                  (cond
                    (= status 404) true
                    (= status 409) (let [fresh (await (dbs/get dbs "task" (:_id task)))]
                                     (if fresh
                                       (await (dbs/remove dbs fresh))
                                       true))
                    :else          true))))))


(defn- run-task!
  [dbs task]
  (-> ((fn ^:async f []
         (let [result (await (execute-task task dbs))]
           (cond
             (= result ::unknown-task)
             (do
               (log/warn :tasks/dead-letter {:id (:_id task) :reason :unknown-task})
               (await (dead-letter! dbs task "unknown-task-type")))

             (:retry-after-ms result)
             (do
               (log/debug :tasks/retrying-with-hint
                          {:id (:_id task)
                           :retry-after-ms (:retry-after-ms result)})
               (await (mark-failed! dbs task (:retry-after-ms result))))

             result
             (do
               (log/debug :tasks/completed {:id (:_id task)})
               (await (remove-with-latest-rev! dbs task)))

             :else
             (do
               (log/debug :tasks/failed {:id (:_id task)})
               (await (mark-failed! dbs task)))))))
      (.catch (fn [err]
                (log/error :tasks/error {:id (:_id task) :error (str err)})
                (mark-failed! dbs task)))))


(defn ^:async run-worker!
  [dbs queue]
  (let [running (atom true)]
    (while @running
      (if-let [task (let [result (atom nil)]
                      (swap! queue
                        (fn [tasks]
                          (if (seq tasks)
                            (do (reset! result (first tasks)) (subvec tasks 1))
                            tasks)))
                      @result)]
        (await (run-task! dbs task))
        (reset! running false)))))


(defn ^:async run-workers!
  [dbs tasks]
  (let [queue (atom (vec tasks))]
    (await (js/Promise.all
            (into-array (repeatedly (:max-concurrent config)
                                    #(run-worker! dbs queue)))))))


(def ^:private state (atom {}))


(declare flush!)


(defn- schedule-retry!
  [delay-ms]
  (js/setTimeout flush! delay-ms))


(defn ^:async nearest-retry-delay
  [dbs]
  (let [{:keys [docs]}
        (await (dbs/find dbs
                         {:selector  {:type       "task"
                                      :run-at     {:$exists true}
                                      :created-at {:$exists true}
                                      :$or        [{:status {:$exists false}}
                                                   {:status {:$ne "failed"}}]}
                          :sort      [{:type :asc}
                                      {:run-at :asc}
                                      {:created-at :asc}]
                          :limit     1
                          :use-index "by-type-run-at-created-at"}))]
    (when-let [task (first docs)]
      (max 0 (- (utils/iso->ms (:run-at task)) (utils/now-ms))))))


(defn ^:async run-cycle!
  [dbs]
  (let [running (atom true)]
    (while (and (online?) (:enabled? @state) @running)
      (let [tasks (await (fetch-due-tasks dbs (utils/now-iso)))]
        (log/debug :run-cycle/tasks tasks)
        (if (seq tasks)
          (await (run-workers! dbs tasks))
          (reset! running false)))))
  (let [delay (await (nearest-retry-delay dbs))]
    (when delay
      (log/debug :tasks/scheduling-retry {:delay-ms delay})
      (schedule-retry! delay))))


(defn flush!
  "Trigger a run cycle if enabled and online. Fire-and-forget."
  []
  (when-let [{:keys [dbs enabled? running?]} @state]
    (when (and (some? dbs) enabled? (online?) (not running?))
      (swap! state assoc :running? true)
      (-> ((fn ^:async f []
             (try
               (await (run-cycle! dbs))
               (catch js/Error err
                 (log/error :tasks/flush-error {:error (str err)}))
               (finally
                (swap! state assoc :running? false)))))
          (.catch identity))))
  nil)


(defn ^:async start!
  "Start the task runner."
  []
  (let [task-dbs (dbs/dbs)]
    (reset! state {:enabled? true :dbs task-dbs})
    (log/info :tasks/starting config)
    (await (ensure-task-index! task-dbs))
    (flush!)))


(defn stop!
  []
  (reset! state {})
  (log/info :tasks/stopped {}))


(defn resume!
  []
  (log/debug :tasks/resuming {})
  (flush!))


(defn ^:async create-task!
  [task-type data]
  (let [now-iso (utils/now-iso)]
    (await (dbs/insert (dbs/dbs) (create-task task-type data now-iso)))
    (flush!)))

(ns tasks
  "Task runner: scan DB, run tasks in parallel, pause when offline."
  (:require
   [db :as db]
   [dbs :as dbs]
   [lambdaisland.glogi :as log]
   [promesa.core :as p]
   [utils :as utils]))


;; =============================================================================
;; Configuration
;; =============================================================================


(def ^:private config
  {:max-backoff-ms 60000
   :max-concurrent 3
   :max-attempts   5})


;; =============================================================================
;; Task Documents
;; =============================================================================


(declare normalized-data)


(defn- normalized-map-entry
  [[k v]]
  (when (and (some? k) (some? v))
    [(name k) (normalized-data v)]))


(defn- normalized-data
  [value]
  (cond
    (map? value)
    (->> value
         (keep normalized-map-entry)
         (sort-by first)
         (into (sorted-map)))

    (vector? value)
    (mapv normalized-data value)

    (sequential? value)
    (mapv normalized-data value)

    :else
    value))


(defn- hex8
  [n]
  ;; convert number to base-16 string
  (let [hex (.toString (unsigned-bit-shift-right n 0) 16)]
    ;; pad left with zeroes to make 8-chars string
    (subs (str "00000000" hex) (count hex))))


(defn- hash32
  [value]
  ;; FNV-1a over the canonical task data string. Good enough for tiny local queues.
  (loop [i 0 h 0x811c9dc5]
    (if (< i (count value))
      (recur (inc i) (js/Math.imul (bit-xor h (.charCodeAt value i)) 0x01000193))
      (hex8 h))))


(defn id-for
  "Deterministic short document id for task work deduped by task-type + data."
  [task-type data]
  (let [task-hash (-> data normalized-data pr-str hash32)]
   (str "task:" task-type ":" task-hash)))


(defn create-task
  "Build a task document."
  ([task-type data now-iso]
   (create-task task-type data now-iso nil))
  ([task-type data now-iso {:keys [id]}]
   (cond-> {:type       "task"
            :task-type  task-type
            :data       data
            :attempts   0
            :run-at     now-iso
            :created-at now-iso}
     id (assoc :_id id))))


(defn retry
  "Signal from an `execute-task` handler asking the runner to schedule a retry.
   Optional keys: :last-error, :retry-after-ms."
  ([] (retry {}))
  ([data]
   (assoc data :task-result :retry)))


;; =============================================================================
;; Task Execution
;; =============================================================================


(defmulti execute-task
  "Execute a task. Dispatches on :task-type.

   Arguments:
     task - the task document (map with :task-type, :data, etc.)
     dbs  - standard dbs map with :user/db, :device/db, etc.

   Returns a promise that resolves to:
     - truthy  -> task succeeded, will be removed
     - falsy   -> task failed, will be retried with exponential backoff
     - {:task-result :retry ...} -> task failed, optional :retry-after-ms and :last-error
     - {:retry-after-ms n} -> task failed, will be retried after the suggested delay
     - ::unknown-task -> unknown task type, will be dead-lettered"
  (fn [task _dbs] (:task-type task)))


(defmethod execute-task :default
  [task _dbs]
  (log/warn :tasks/unknown-type {:task-type (:task-type task)})
  ::unknown-task)


;; =============================================================================
;; Core Runner
;; =============================================================================


(defn- online?
  []
  js/self.navigator.onLine)


(defn- backoff-ms
  [attempts]
  (min (:max-backoff-ms config)
       (* 1000 (Math/pow 2 (max 0 attempts)))))


(defn- record-last-error
  "Replace stale error text; blank means no current error."
  [task last-error]
  (if (utils/non-blank last-error)
    (assoc task :last-error last-error)
    (dissoc task :last-error)))


(def ^:private page-size 50)


(defn- ensure-task-index!
  [dbs]
  (p/catch
    (db/create-index
     (dbs/db-for dbs "task")
     [:type :run-at :created-at]
     {:name "by-type-run-at-created-at"
      :ddoc "by-type-run-at-created-at"})
    (fn [err]
      (log/error :tasks/index-error {:error (str err)}))))


(defn- fetch-due-tasks
  [dbs now-iso]
  (p/let [{:keys [docs]}
          (dbs/find dbs
                    {:selector  {:type       "task"
                                 :run-at     {:$lte now-iso}
                                 :created-at {:$exists true}
                                 :$or        [{:status {:$exists false}}
                                              {:status {:$ne "failed"}}]}

                     ;; Every index field in selector must appear in sort
                     :sort      [{:type :asc}
                                 {:run-at :asc}
                                 {:created-at :asc}]
                     :limit     page-size
                     :use-index "by-type-run-at-created-at"})]
    (vec docs)))


(defn- dead-letter!
  [dbs task {:keys [reason last-error]}]
  (dbs/insert
   dbs
   (record-last-error
    (assoc task
           :status         "failed"
           :failure-reason reason
           :failed-at      (utils/now-iso)
           :run-at         nil)
    last-error)))


(defn- mark-failed!
  [dbs task {:keys [retry-after-ms last-error]}]
  (let [attempts (inc (or (:attempts task) 0))]
    (if (>= attempts (:max-attempts config))
      (dead-letter!
       dbs
       (assoc task :attempts attempts)
       {:reason     "retry-limit-reached"
        :last-error last-error})
      (let [delay-ms    (or retry-after-ms (backoff-ms attempts))
            next-run-ms (+ (utils/now-ms) delay-ms)
            next-run    (utils/ms->iso next-run-ms)]
        (dbs/insert
         dbs
         (record-last-error
          (assoc task :attempts attempts :run-at next-run)
          last-error))))))


(defn- error-message
  [err]
  (or (ex-message err)
      (.-message err)
      (str err)))


(defn- remove-task!
  [dbs task]
  (p/catch
    (dbs/remove dbs task)
    (fn [err]
      (let [status (or (:status err) (get-in err [:body :status]))]
        (cond
          (= status 404) true
          (= status 409) (p/let [fresh (dbs/get dbs "task" (:_id task))]
                           (if fresh
                             (dbs/remove dbs fresh)
                             true))
          :else          (do
                           (log/warn :tasks/remove-failed
                                     {:id (:_id task) :error (str err)})
                           true))))))


(defn- run-task!
  [dbs task]
  (p/catch
    (p/let [result (execute-task task dbs)]
      (cond
        (= result ::unknown-task)
        (do
          (log/warn :tasks/dead-letter {:id (:_id task) :reason :unknown-task})
          (dead-letter! dbs task {:reason "unknown-task-type"}))

        (= (:task-result result) :retry)
        (do
          (log/debug :tasks/retrying {:id (:_id task)
                                      :retry-after-ms (:retry-after-ms result)})
          (mark-failed! dbs task {:retry-after-ms (:retry-after-ms result)
                                  :last-error     (:last-error result)}))

        (:retry-after-ms result)
        (do
          (log/debug :tasks/retrying-with-hint {:id (:_id task)
                                                :retry-after-ms (:retry-after-ms result)})
          (mark-failed! dbs task {:retry-after-ms (:retry-after-ms result)}))

        result
        (do
          (log/debug :tasks/completed {:id (:_id task)})
          (remove-task! dbs task))

        :else
        (do
          (log/debug :tasks/failed {:id (:_id task)})
          (mark-failed! dbs task {}))))

    (fn [err]
      (log/error :tasks/error {:id (:_id task) :error (str err)})
      (mark-failed! dbs task {:last-error (error-message err)}))))


(defn- take-next-task!
  [queue]
  (let [result (atom nil)]
    (swap! queue
      (fn [tasks]
        (if (seq tasks)
          (do
            (reset! result (first tasks))
            (subvec tasks 1))
          tasks)))
    @result))


(defn- run-worker!
  [dbs queue]
  (p/loop []
    (when-let [task (take-next-task! queue)]
      (p/do
        (run-task! dbs task)
        (p/recur)))))


(defn- run-workers!
  [dbs tasks]
  (let [queue (atom (vec tasks))]
    (p/all
     (repeatedly (:max-concurrent config) #(run-worker! dbs queue)))))


(def ^:private state (atom {}))


(declare flush!)


(defn- schedule-retry!
  "Schedule a delayed flush after a failed task."
  [delay-ms]
  (js/setTimeout flush! delay-ms))


(defn- nearest-retry-delay
  "ms until the earliest run-at among remaining tasks, or nil."
  [dbs]
  (p/let [{:keys [docs]}
          (dbs/find dbs
                    {:selector  {:type       "task"
                                 :run-at     {:$exists true}
                                 :created-at {:$exists true}
                                 :$or        [{:status {:$exists false}}
                                              {:status {:$ne "failed"}}]}
                     :sort      [{:type :asc}
                                 {:run-at :asc}
                                 {:created-at :asc}]
                     :limit     1
                     :use-index "by-type-run-at-created-at"})]
    (when-let [task (first docs)]
      (max 0 (- (utils/iso->ms (:run-at task)) (utils/now-ms))))))


(defn- run-cycle!
  [dbs]
  (p/do
    (p/loop []
      (when (and (online?) (:enabled? @state))
        (p/let [tasks (fetch-due-tasks dbs (utils/now-iso))]
          (log/debug :run-cycle/tasks tasks)
          (when (seq tasks)
            (p/do
              (run-workers! dbs tasks)
              (p/recur))))))
    (p/let [delay (nearest-retry-delay dbs)]
      (when delay
        (log/debug :tasks/scheduling-retry {:delay-ms delay})
        (schedule-retry! delay)))))


;; =============================================================================
;; Public API
;; =============================================================================


(defn flush!
  "Trigger a run cycle if enabled and online. Fire-and-forget: the promise
   chain is self-contained with its own error handling, so callers never
   block on task execution."
  []
  (when-let [{:keys [dbs enabled? running?]} @state]
    (when (and (some? dbs) enabled? (online?) (not running?))
      (swap! state assoc :running? true)
      (-> (run-cycle! dbs)
          (p/catch #(log/error :tasks/flush-error {:error (str %)}))
          (p/finally #(swap! state assoc :running? false)))))
  nil)


(defn start!
  "Start the task runner."
  []
  (let [task-dbs (dbs/dbs)]
    (reset! state {:enabled? true :dbs task-dbs})
    (log/info :tasks/starting config)
    (p/do
      (ensure-task-index! task-dbs)
      (flush!))))


(defn stop!
  []
  (reset! state {})
  (log/info :tasks/stopped {}))


(defn resume!
  []
  (log/debug :tasks/resuming {})
  (flush!))


;; -- Unique tasks -------------------------------------------------------------


(defn- insert-task!
  "Insert a task doc. Return the doc with its _id and _rev set."
  [dbs task]
  (p/let [{:keys [id rev]} (dbs/insert dbs task)]
    (assoc task :_id id :_rev rev)))


(defn- insert-or-get-existing!
  "Insert a unique task. On 409 conflict, return the existing doc instead."
  [dbs task-id task]
  (p/catch
    (insert-task! dbs task)
    (fn [err]
      (if (db/conflict? err)
        (dbs/get dbs "task" task-id)
        (throw err)))))


(defn ensure!
  "Ensure one task exists for task-type + normalized data.
   Same type + same normalized data means same task.
   If duplicate work is intentional, use `schedule!`."
  [dbs task-type data]
  (let [task-id (id-for task-type data)
        task    (create-task task-type data (utils/now-iso) {:id task-id})]
    (p/let [doc (insert-or-get-existing! dbs task-id task)]
      (flush!)
      doc)))


(defn- reset-failed-doc
  [task task-type data now-iso]
  (-> task
      (assoc :task-type task-type
             :data      data
             :attempts  0
             :run-at    now-iso)
      (dissoc :status :failure-reason :failed-at :last-error)))


(defn- retry-on-conflict!
  [dbs task-id task-type data now-iso err]
  (if-not (db/conflict? err)
    (throw err)
    (p/let [fresh (dbs/get dbs "task" task-id)]
      (if (= "failed" (:status fresh))
        (insert-task! dbs (reset-failed-doc fresh task-type data now-iso))
        fresh))))


(defn- reset-failed!
  [dbs task-id task task-type data now-iso]
  (p/catch
    (insert-task! dbs (reset-failed-doc task task-type data now-iso))
    #(retry-on-conflict! dbs task-id task-type data now-iso %)))


(defn retry!
  "Retry unique task work for task-type + normalized data.
   - Missing task: create it (same as `ensure!`).
   - Active task:  return it unchanged.
   - Failed task:  clear failure state and schedule an immediate run."
  [dbs task-type data]
  (let [task-id (id-for task-type data)
        now-iso (utils/now-iso)]
    (p/let [existing (dbs/get dbs "task" task-id)]
      (cond
        (not existing)
        (ensure! dbs task-type data)

        (not= "failed" (:status existing))
        existing

        :else
        (p/let [doc (reset-failed! dbs task-id existing task-type data now-iso)]
          (flush!)
          doc)))))


;; -- One-off tasks ------------------------------------------------------------


(defn schedule!
  "Create a new task for the given payload. Each call inserts a fresh doc - no dedupe."
  [dbs task-type data]
  (p/let [doc (insert-task! dbs (create-task task-type data (utils/now-iso)))]
    (flush!)
    doc))

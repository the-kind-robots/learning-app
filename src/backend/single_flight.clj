(ns single-flight
  "One run at a time per task, within this process."
  (:import
   [java.util.concurrent TimeoutException]))


(def ^:private longest-join-wait-ms
  "How long a caller waits for a run it joined. It is five seconds under
   nginx's 100 s `proxy_read_timeout`, so the caller answers before the proxy
   does."
  95000)


(defonce ^:private flights
  ;; The runs going on now: a promise of each run's outcome, by task id.
  (atom {}))


(defn- joined
  "Called when a caller joins a running task. It does nothing; tests redefine
   it to see the join."
  [_task-id])


(defn run
  "Callers with the same `task-id` share one run of `task`: the first runs it,
   the rest wait for its result, at most `longest-join-wait-ms`."
  [task-id task]
  (let [mine       (promise)
        [before _] (swap-vals! flights #(if (contains? % task-id) % (assoc % task-id mine)))
        flight     (get before task-id mine)]
    (if (identical? flight mine)
      (let [outcome (try
                      {:result (task)}
                      (catch Throwable thrown
                        {:thrown thrown})
                      (finally
                       (swap! flights
                         #(if (identical? (get % task-id) mine)
                            (dissoc % task-id)
                            %))))]
        (deliver mine outcome)
        (if-let [thrown (:thrown outcome)]
          (throw thrown)
          (:result outcome)))
      (do
        (joined task-id)
        (let [outcome (deref flight longest-join-wait-ms nil)]
          (cond
            (nil? outcome)
            (throw (TimeoutException.
                    (str "The run for "
                         (pr-str task-id)
                         " gave no result within "
                         longest-join-wait-ms
                         " ms")))

            (:thrown outcome)
            (throw (:thrown outcome))

            :else
            (:result outcome)))))))

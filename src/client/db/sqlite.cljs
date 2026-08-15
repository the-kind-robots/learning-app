(ns db.sqlite
  (:require
   [instrumentation :as instrumentation]
   [lambdaisland.glogi :as log]))


(defn- make-exec-proxy
  [worker state]
  #js {:exec
       (fn [^js opts]
         (js/Promise.
          (fn [resolve reject]
            (let [id  (:next-id (swap! state update :next-id inc))
                  msg (doto (js/Object.assign #js {} opts) (aset "id" id))]
              (..
               worker
               (addEventListener
                "message"
                (fn handler
                  [^js e]
                  (when (= (.. e -data -id) id)
                    (.removeEventListener worker "message" handler)
                    (if (.. e -data -error)
                      (reject (js/Error. (.. e -data -error)))
                      (resolve (.. e -data -result)))))))
              (.postMessage worker msg)))))})


(defn exec
  [db opts]
  (.exec (:proxy db) opts))


(defn ready?
  [db]
  (:ready? @(:state db)))


(defn init!
  [_deps]
  (when goog/DEBUG
    (instrumentation/dictionary-start!))
  (let [state  (atom {:ready? false :next-id 0})
        worker (js/Worker. (str "/js/sqlite3-worker.js?sqlite3.dir=/js"
                                (when goog/DEBUG "&telemetry=1")))
        proxy  (make-exec-proxy worker state)]
    (..
     worker
     (addEventListener
      "message"
      (fn [^js e]
        (case (.. e -data -type)
          "ready" (do
                    (swap! state assoc :ready? true)
                    (when goog/DEBUG
                      (instrumentation/dictionary-ready!)))
          "error" (log/error :dbs/sqlite3-worker-error {:message (.. e -data -message)})
          "phase" (let [d   (.. e -data)
                        ph  (.-phase d)
                        ms  (.-durationMs d)
                        ok? (= "ok" (.-status d))]
                    (when goog/DEBUG
                      (instrumentation/dictionary-phase! ph ms (.-status d)))
                    (if ok?
                      (log/info (keyword "dict-worker" ph) {:duration-ms ms})
                      (log/error (keyword "dict-worker" ph) {:duration-ms ms :reason (.-reason d)})))
          nil))))
    (..
     worker
     (addEventListener
      "error"
      (fn [^js e]
        (log/error :dbs/sqlite3-worker-crashed {:error (str e)})
        (swap! state assoc :ready? false))))
    {:proxy  proxy
     :state  state
     :worker worker}))

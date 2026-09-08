(ns db.sqlite
  "The page's half of the dictionary protocol.

   Every tab has a worker of its own and the workers never speak to each
   other; which of them has the SQLite file open is decided by the browser's
   lock manager (#351). A worker without it answers with no completions, so
   the answer alone cannot say whether the prefix has none or the dictionary
   is elsewhere — `ready?` is what tells those apart."
  (:require
   [instrumentation :as instrumentation]
   [lambdaisland.glogi :as log]))


(defn- make-exec-proxy
  [worker requests]
  #js {:exec
       (fn [^js opts]
         (js/Promise.
          (fn [resolve reject]
            (let [id  (:next-id (swap! requests update :next-id inc))
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
  "Whether this tab has the dictionary right now. Not a gate in front of a
   query — the worker answers either way — but the only thing that can tell an
   empty answer from a prefix with no completions (#312)."
  [db]
  @(:holding? db))


(defn- handle-lifecycle!
  "Where the worker is: it takes the database on every turn and gives it back
   when the tab leaves the foreground, and says so each time.

   Queries are sent whatever it says — nothing here gates them, because a
   worker without the database answers empty by itself. What is kept is the
   reading `ready?` returns, for a caller that has to describe the difference
   (#312); the rest feeds the measurement and the log."
  [holding? ^js e]
  (case (.. e -data -type)
    "loading" (reset! holding? false)
    "ready"   (do
                (reset! holding? true)
                (when goog/DEBUG
                  (instrumentation/dictionary-ready!)))
    "error"   (do
                (reset! holding? false)
                (log/error :dbs/sqlite3-worker-error {:message (.. e -data -message)}))
    "phase"   (let [d   (.. e -data)
                    ph  (.-phase d)
                    ms  (.-durationMs d)
                    ok? (= "ok" (.-status d))]
                (when goog/DEBUG
                  (instrumentation/dictionary-phase! ph ms (.-status d)))
                (if ok?
                  (log/info (keyword "dict-worker" ph) {:duration-ms ms})
                  (log/error (keyword "dict-worker" ph) {:duration-ms ms :reason (.-reason d)})))
    nil))


(defn attach
  "Builds the `:db/sqlite` component around a worker. Split out of `init!` so
   the protocol can be driven from a test, in a runtime with no workers to
   spawn."
  [worker]
  ;; Two atoms because they answer to two different things: `requests` is the
  ;; id counter the proxy needs and nobody outside it reads, `holding?` is the
  ;; reading `ready?` hands out.
  (let [requests (atom {:next-id 0})
        holding? (atom false)]
    (.addEventListener worker "message" (partial handle-lifecycle! holding?))
    (.addEventListener worker
                       "error"
                       (fn [^js e]
                         (reset! holding? false)
                         (log/error :dbs/sqlite3-worker-crashed {:error (str e)})))
    {:holding? holding?
     :proxy    (make-exec-proxy worker requests)
     :worker   worker}))


(defn- foreground?
  "Whether this is the tab being typed into: on screen and holding the
   keyboard. On screen alone is not enough — two tabs can be visible at once,
   side by side or in split screen, and `hidden` is false for both, so the
   database would go to whichever took the lock first rather than to the one
   the user is working in."
  []
  (and (= "visible" (.-visibilityState js/document))
       (.hasFocus js/document)))


(defn- publish-foreground!
  "Tells the worker whether it is allowed to hold the database. A worker
   cannot see the document, and only the foreground tab may hold it: a
   backgrounded one is frozen holding it and stops answering (#351). The
   worker is given the answer, never the reasoning."
  [worker]
  (.postMessage worker #js {:type "foreground" :foreground (foreground?)}))


(defn init!
  [_deps]
  (when goog/DEBUG
    (instrumentation/dictionary-start!))
  (let [worker (js/Worker. (str "/js/sqlite3-worker.js?sqlite3.dir=/js"
                                (when goog/DEBUG "&telemetry=1")))
        report #(publish-foreground! worker)]
    ;; Posted before the worker's script has run — the message waits for it —
    ;; so the first thing it hears is whether it may take the database at all.
    (report)
    ;; Three events for two conditions, and they overlap: leaving for another
    ;; application fires `blur` and `visibilitychange` both. The worker
    ;; ignores a report that repeats the value it already has, so the overlap
    ;; costs nothing and neither condition can be missed.
    (js/document.addEventListener "visibilitychange" report)
    (js/window.addEventListener "focus" report)
    (js/window.addEventListener "blur" report)
    (attach worker)))

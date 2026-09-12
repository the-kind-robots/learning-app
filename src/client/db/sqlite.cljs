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
  "Sends one query and resolves with its rows.

   A port per query rather than an id per query: the query carries a channel
   and the worker answers on it, so nothing has to be matched up afterwards
   and no counter has to be kept. It also keeps replies off the worker's own
   port, which carries the status messages this tab's worker sends it — the
   two used to share one listener, and every request had to learn to ignore
   the status.

   Measured, since a channel per keystroke sounds expensive: 0.137 ms per
   round trip against 0.103 ms for id matching, over 20k trips. The 34 µs
   difference sits behind a 100 ms debounce and in front of a query that
   costs 20-27 ms (#179)."
  [worker]
  #js {:exec
       (fn [^js opts]
         (js/Promise.
          (fn [resolve reject]
            (let [channel (js/MessageChannel.)
                  port    (.-port1 channel)]
              ;; Assigning `onmessage` starts the port; `addEventListener`
              ;; would leave it closed until an explicit `.start`.
              (set! (.-onmessage port)
                    (fn [^js e]
                      (.close port)
                      (if (.. e -data -error)
                        (reject (js/Error. (.. e -data -error)))
                        (resolve (.. e -data -result)))))
              (.postMessage worker opts #js [(.-port2 channel)])))))})


(defn exec
  [db opts]
  (.exec (:proxy db) opts))


(defn ready?
  "Whether this tab has the dictionary right now. Not a gate in front of a
   query — the worker answers either way — but the only thing that can tell an
   empty answer from a prefix with no completions (#312)."
  [db]
  @(:holding? db))


(defn holding-state
  "What one worker message says about this tab holding the database: `true`
   when it has taken its turn, `false` when it has given it back or could not
   open it, and nil when the message says nothing about ownership.

   A question about the message and nothing else — no atom, no worker, no
   mocks. What to do with the answer belongs to the caller, which is why this
   is public: the protocol table is worth stating in a test on its own."
  [^js data]
  (case (.-type data)
    "ready" true
    ("loading" "error") false
    nil))


(defn- observe-worker-message!
  "The log and the measurement for one worker message.

   Kept apart from `holding-state` so that reading a message stays a question
   about its contents rather than an action on someone else's state. Nothing
   here decides anything; the dictionary works the same with all of it
   stripped from a release build."
  [^js data]
  (case (.-type data)
    "ready" (when ^boolean goog/DEBUG
              (instrumentation/dictionary-ready!))
    "error" (log/error :dbs/sqlite3-worker-error {:message (.-message data)})
    "phase" (let [ph  (.-phase data)
                  ms  (.-durationMs data)
                  ok? (= "ok" (.-status data))]
              (when ^boolean goog/DEBUG
                (instrumentation/dictionary-phase! ph ms (.-status data)))
              (if ok?
                (log/info (keyword "dict-worker" ph) {:duration-ms ms})
                (log/error (keyword "dict-worker" ph) {:duration-ms ms :reason (.-reason data)})))
    nil))


(defn attach
  "Builds the `:db/sqlite` component around a worker. Split out of `init!` so
   the protocol can be driven from a test, in a runtime with no workers to
   spawn."
  [worker]
  (let [holding? (atom false)]
    (.addEventListener worker
                       "message"
                       (fn [^js e]
                         (let [data (.-data e)]
                           ;; `when-some`, not `when-let`: `false` is an answer
                           ;; here, and only nil means "not about ownership".
                           (when-some [holding (holding-state data)]
                             (reset! holding? holding))
                           (observe-worker-message! data))))
    (.addEventListener worker
                       "error"
                       (fn [^js e]
                         (reset! holding? false)
                         (log/error :dbs/sqlite3-worker-crashed {:error (str e)})))
    {:holding? holding?
     :proxy    (make-exec-proxy worker)
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
  (when ^boolean goog/DEBUG
    (instrumentation/dictionary-start!))
  (let [worker (js/Worker. (str "/js/sqlite3-worker.js?sqlite3.dir=/js"
                                (when ^boolean goog/DEBUG "&telemetry=1")))
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

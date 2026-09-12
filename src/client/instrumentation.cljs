(ns instrumentation
  "In-page measurement for development. Answers, with numbers, what the
   browser did: how many times the app rendered, how many dispatches ran and
   how they nested, what the standard web metrics say, how much layout
   shifted including the input-caused shifts CLS excludes, which frames
   blocked the main thread and who blocked them, how long the dictionary
   took to become usable, and how much storage it occupies.

   Installed only under goog.DEBUG — a release build eliminates the whole
   namespace. Read from the page as `window.__metrics()`, reset with
   `window.__metricsReset()`, read storage as `window.__storage()`.

   The standard metrics come from the web-vitals library, fetched at runtime
   from `/js/web-vitals.js` rather than imported — see `load-web-vitals!`.

   Honest limits, measured before this existed: the frame counter stops when
   the window is occluded even though visibilityState stays \"visible\"; a
   render here means one `render!` call — the browser still paints at most
   once per frame regardless of how many happened inside it; and the
   web-vitals figures accumulate over the page's whole life, so a reset
   clears our copy without rewinding CLS or INP."
  (:require
   [nexus.registry :as nxr]))


(defonce ^:private zero-metrics
  {:dictionary        {}
   :dispatches        0
   :frames            0
   :layout-shift      {:entries [] :input-excluded 0.0 :score 0.0}
   :long-frames       []
   :nested-dispatches 0
   :renders           0
   :web-vitals        {}})


(defonce ^:private metrics (atom zero-metrics))


(defonce ^:private dispatch-depth (volatile! 0))


(defn- observe!
  "Watches one PerformanceObserver entry type, quietly skipping the ones this
   browser does not support.

   The constructor takes a callback the browser itself invokes with a batch of
   entries whenever new ones are recorded; `.observe` only says which entry
   type to watch. Nothing holds the observer afterwards on purpose: the
   platform keeps a registered observer alive until `.disconnect`, and these
   watch for the whole page lifetime."
  [entry-type f options]
  (try
    (.observe (js/PerformanceObserver.
               (fn [entries _]
                 (doseq [entry (.getEntries ^js entries)]
                   (f entry))))
              (clj->js (merge {:type entry-type :buffered true} options)))
    (catch :default _ nil)))


(defn- scalar-fields
  "The flat values of a JS object, keyed. Attribution objects also carry
   PerformanceEntry instances, which do not survive serialisation out to a
   test runner, so anything that is not a string, number or boolean is
   dropped rather than crashing the read."
  [^js obj]
  (if (nil? obj)
    {}
    (persistent!
     (reduce (fn [acc k]
               (let [v (aget obj k)]
                 (if (or (string? v) (number? v) (boolean? v))
                   (assoc! acc (keyword k) v)
                   acc)))
             (transient {})
             (js/Object.keys obj)))))


(defn- script-attribution
  "What a long animation frame blamed, one entry per script."
  [^js entry]
  (mapv (fn [^js script]
          {:duration     (.-duration script)
           :invoker      (.-invoker script)
           :invoker-type (.-invokerType script)
           :source-url   (.-sourceURL script)})
        (or (.-scripts entry) #js [])))


(defn- count-frames!
  []
  (js/requestAnimationFrame
   (fn [_]
     (swap! metrics update :frames inc)
     (count-frames!))))


(defn count-render!
  "Counts one render. Called from the render path itself, not from a store
   watch: with unbatched saves one dispatch may notify the store several times
   while rendering once, so notifications no longer measure renders."
  []
  (swap! metrics update :renders inc))


(declare trace!)


(defn render!
  "Runs one render through the metrics and the trace: counted, and recorded
   as a `render` entry with its duration, so a tap can be placed before,
   inside or after the re-render that may have replaced its node."
  [render-fn state]
  (count-render!)
  (let [started (.now js/performance)]
    (try
      (render-fn state)
      (finally
       (trace! "render" #js {:ms (- (.now js/performance) started)})))))


(defn dictionary-start!
  "Opens the readiness interval. Called before the dictionary worker is
   constructed, on the document's timeline — the worker's own
   `performance.now` runs against a different time origin and would need
   arithmetic to compare."
  []
  (.mark js/performance "dictionary-start"))


(defn dictionary-ready!
  "Closes the readiness interval, and only the first one.

   The worker reports ready on every turn it takes, not just its first: the
   dictionary belongs to the visible tab, so a tab that is left and returned
   to opens the database again and says so again. This interval is measured
   from a mark laid once at page load, so a second reading would not be what
   the dictionary cost — it would be how long the page had been open when the
   user came back. The first reading is the one that answers the question, and
   `:phases` carries what each later turn cost."
  []
  (when-not (get-in @metrics [:dictionary :ready-ms])
    (let [^js measure (try
                        (.measure js/performance "dictionary-ready" "dictionary-start")
                        (catch :default _ nil))]
      (when measure
        (swap! metrics assoc-in [:dictionary :ready-ms] (.-duration measure))))))


(defn dictionary-phase!
  "Keeps one phase the worker reported. The worker already times every phase
   for the log; this only carries the numbers into the metrics. `cache-hit`
   among them is what separates a warm start from a cold one.

   Startup and per-turn phases land here together and the list accumulates:
   the startup ones (`wasm-init`, `pool-install`, `download`, `import`) happen
   once per tab, and `pause` and `unpause` repeat every time the dictionary
   changes tabs. Both are wanted — the per-turn pair is what the cost of a
   focus change is read from."
  [phase duration-ms status]
  (swap! metrics update-in
    [:dictionary :phases]
    (fnil conj [])
    {:duration-ms duration-ms
     :phase       phase
     :status      status}))


(defn- report-vital!
  "Keeps the latest reading of one standard metric under its lowercased name."
  [^js metric]
  (swap! metrics assoc-in
    [:web-vitals (keyword (.toLowerCase (.-name metric)))]
    {:attribution (scalar-fields (.-attribution metric))
     :rating      (.-rating metric)
     :value       (.-value metric)}))


(defn- load-web-vitals!
  "Pulls the web-vitals library in as a plain script and subscribes to the
   standard metrics.

   Loaded at runtime rather than imported, because an import rides into the
   release bundle: dead-code elimination does not remove this namespace on its
   own — a release build was checked and still carried `window.__metricsReset`
   — so an imported library would ship with it. A script the DEBUG path
   requests is fetched only when that path runs.

   Subscribing late loses nothing: every metric here reads buffered entries,
   so readings recorded before the script arrived are still reported.
   `reportAllChanges` is what makes them readable mid-life — by default
   nothing is reported until the page is hidden, which never happens inside a
   test."
  []
  (let [script (js/document.createElement "script")]
    (set! (.-src script) "/js/web-vitals.js")
    (set! (.-onload script)
          (fn [_]
            (let [^js library (.-webVitals js/window)
                  report      #js {:reportAllChanges true}]
              (when library
                (.onCLS library report-vital! report)
                (.onFCP library report-vital! report)
                (.onINP library report-vital! report)
                (.onLCP library report-vital! report)
                (.onTTFB library report-vital! report)))))
    (.appendChild js/document.head script)))


(defn- storage-estimate
  "A promise of what this origin occupies. Separate from `__metrics` on
   purpose: the underlying API is asynchronous, and folding it in would make
   every existing synchronous read a promise."
  []
  (if-let [^js storage (.-storage js/navigator)]
    (.then (.estimate storage)
           (fn [^js estimate]
             (clj->js {:quota         (.-quota estimate)
                       :usage         (.-usage estimate)
                       :usage-details (scalar-fields (.-usageDetails estimate))})))
    (js/Promise.resolve #js {})))


;;
;; Trace: what happened last, readable after the fact.
;;
;; A phone that freezes with no DevTools attached leaves nothing behind. This
;; keeps the last `trace-limit` events in a ring — errors, render exceptions,
;; page lifecycle, long tasks, every action and effect, taps on the themes
;; screen — as plain JS objects (no conversion per entry), and mirrors the
;; ring into localStorage on every error-class entry and every visibility
;; change, so a reload can still read what preceded the freeze. Read it as
;; `window.__trace()` or `JSON.parse(localStorage.getItem('sprecha:trace'))`.
;;


(def ^:private trace-limit 500)


(def ^:private trace-key "sprecha:trace")


(defonce ^:private trace (array))


(defn trace!
  "Appends one entry: `kind` a string, `data` a plain JS object or nil.
   Public for the few product effects that report a decision here under
   goog.DEBUG."
  [kind data]
  (.push trace #js {:t (.now js/performance) :kind kind :data data})
  (when (> (.-length trace) trace-limit)
    (.splice trace 0 (- (.-length trace) trace-limit)))
  nil)


(defn- mirror-trace!
  "Serialises the ring into localStorage. Only here, never per entry."
  []
  (try
    (.setItem js/localStorage trace-key (js/JSON.stringify trace))
    (catch :default _ nil)))


(defn- error-data
  [^js err]
  (if (instance? js/Error err)
    #js {:message (.-message err) :stack (.-stack err)}
    #js {:message (str err)}))


(defn- traced-effect?
  "Saves and event plumbing are noise; everything else is worth a line."
  [effect-name]
  (not (contains? #{:effect/save :effect/stop-propagation} effect-name)))


(defn- effect-timer
  "Nexus runs `:before-effect` before the handler and `:after-effect` after
   it returns, so the pair times a synchronous effect. An async effect returns
   a promise; its settlement is timed from the same start."
  []
  {:before-effect (fn [{:keys [effect] :as ctx}]
                    (let [effect-name (first effect)]
                      (when (traced-effect? effect-name)
                        (trace! "effect-start" #js {:effect (str effect-name)}))
                      (assoc ctx ::started (.now js/performance))))
   :after-effect  (fn [{:keys [effect res] ::keys [started] :as ctx}]
                    (let [effect-name (first effect)
                          finish!     (fn [outcome]
                                        (trace! (str "effect-" outcome)
                                                #js {:effect (str effect-name)
                                                     :ms     (- (.now js/performance) started)}))]
                      (when (traced-effect? effect-name)
                        (if (instance? js/Promise res)
                          (.then res
                                 (fn [_] (finish! "done"))
                                 (fn [err]
                                   (trace! "effect-failed" #js {:effect (str effect-name) :error (error-data err)})
                                   (mirror-trace!)))
                          (finish! "done"))))
                    (dissoc ctx ::started))})


(defn- ^:async trace-export
  "The trace as one JSON document: a header about the page, the live ring,
   and the last mirrored copy from localStorage under its own key — the two
   overlap and are kept apart on purpose, so a reader can see what survived
   a reload and what did not."
  []
  (let [^js storage (.-storage js/navigator)
        estimate    (when (and storage (.-estimate storage))
                      (try (await (.estimate storage)) (catch :default _ nil)))
        stored      (try (.parse js/JSON (or (.getItem js/localStorage trace-key) "null"))
                         (catch :default _ nil))]
    (.stringify js/JSON
                #js {:header #js {:build      (if goog/DEBUG "development" "release")
                                  :time       (.toISOString (js/Date.))
                                  :url        (.-href js/location)
                                  :userAgent  (.-userAgent js/navigator)
                                  :visibility (.-visibilityState js/document)
                                  :storage    estimate}
                     :live   trace
                     :stored stored})))


(defn- ^:async share-file!
  "The share sheet with the trace as a file; false when the platform has no
   file sharing or the user backed out, so the caller falls through."
  [json file-name]
  (let [^js nav js/navigator
        file    (js/File. #js [json] file-name #js {:type "application/json"})
        payload #js {:files #js [file] :title file-name}]
    (if (and (.-share nav) (.-canShare nav) (.canShare nav payload))
      (try
        (await (.share nav payload))
        true
        (catch :default _ false))
      false)))


(defn ^:async export-trace!
  "Hands the trace to whoever can carry it off the phone: the share sheet as a
   file (Android Chrome), else the clipboard (desktop Chrome), else a prompt
   whose value can be selected by hand."
  []
  (let [json      (await (trace-export))
        file-name (str "sprecha-trace-" (.toISOString (js/Date.)) ".json")]
    (when-not (await (share-file! json file-name))
      (let [^js clipboard (.-clipboard js/navigator)
            copied?       (if clipboard
                            (try
                              (await (.writeText clipboard json))
                              true
                              (catch :default _ false))
                            false)]
        (if copied?
          (js/alert "Трасса скопирована")
          (js/prompt "Трасса — скопируйте текст:" json))))))


(defn- install-trace!
  []
  (nxr/register-effect! :effect/export-trace
    (fn export-trace [_ _]
      (export-trace!)))
  (nxr/register-interceptor!
    {:before-action  (fn [{:keys [action] :as ctx}]
                       (trace! "action" #js {:action (str (first action))})
                       ctx)
     ;; A throw in an action, an effect, or the render that a save triggers
     ;; ends up in Nexus's :errors and nowhere else — the dispatcher does not
     ;; read them. Here they become the last thing the trace says.
     :after-dispatch (fn [{:keys [errors] :as ctx}]
                       (when (seq errors)
                         (doseq [{:keys [action effect phase err]} errors]
                           (trace! "dispatch-error"
                                   #js {:phase   (str phase)
                                        :source  (str (first (or action effect)))
                                        :message (ex-message err)
                                        :stack   (some-> err .-stack)}))
                         (mirror-trace!))
                       ctx)})
  (nxr/register-interceptor! (effect-timer))

  (.addEventListener js/window
                     "error"
                     (fn [^js e]
                       (trace! "error"
                               #js {:message (.-message e)
                                    :source  (.-filename e)
                                    :line    (.-lineno e)
                                    :stack   (some-> (.-error e) .-stack)})
                       (mirror-trace!)))
  (.addEventListener js/window
                     "unhandledrejection"
                     (fn [^js e]
                       (trace! "unhandledrejection" (error-data (.-reason e)))
                       (mirror-trace!)))

  ;; Replicant reports a render exception through console.error; wrapping it
  ;; is the only way to get that into the ring. The original still runs.
  (let [original (.-error js/console)]
    (set! (.-error js/console)
          (fn [& args]
            (let [err (some #(when (instance? js/Error %) %) args)]
              (trace!
               "console-error"
               #js {:message (apply str (interpose " " (map #(if (instance? js/Error %) (.-message %) (str %)) args)))
                    :stack   (some-> err .-stack)}))
            (mirror-trace!)
            (.apply original js/console (to-array args)))))

  (doseq [event ["visibilitychange" "pageshow" "pagehide" "freeze" "resume"]]
    (.addEventListener js/document
                       event
                       (fn [_]
                         (trace! event #js {:visibility (.-visibilityState js/document)})
                         (mirror-trace!))))

  (observe! "longtask"
            (fn [entry]
              (trace! "longtask"
                      #js {:start    (.-startTime ^js entry)
                           :duration (.-duration ^js entry)}))
            {})

  ;; Every step of a tap on the themes screen, so a lost one reads as either
  ;; pointerdown -> pointercancel (the browser took the gesture) or
  ;; pointerdown -> pointerup -> no click with a render in between (the
  ;; touched node was replaced). The card's id comes from its
  ;; data-collection-id, which survives a re-render where the node may not.
  ;; A pointercancel also says how far the finger went and whether the page
  ;; scrolled since the pointerdown, which is what the tap recovery decides
  ;; on (pages.collections.tap).
  (let [down (volatile! nil)]
    (.addEventListener js/document
                       "pointermove"
                       (fn [^js e]
                         (when-let [{:keys [x y]} @down]
                           (vswap! down update :moved-px max (js/Math.hypot (- (.-clientX e) x) (- (.-clientY e) y)))))
                       #js {:capture true :passive true})
    (doseq [event ["pointerdown" "pointerup" "pointercancel" "click" "contextmenu"]]
      (.addEventListener js/document
                         event
                         (fn [^js e]
                           (when-let [^js target (.-target e)]
                             (when (.closest target ".switcher")
                               (let [scroll-top (or (some-> js/document .-scrollingElement .-scrollTop) 0)
                                     data       #js {:target      (.-className target)
                                                     :card        (some-> (.closest target "[data-collection-id]")
                                                                          (.getAttribute "data-collection-id"))
                                                     :pointerType (.-pointerType e)}]
                                 (case event
                                   "pointerdown"   (vreset! down
                                                            {:x        (.-clientX e)
                                                             :y        (.-clientY e)
                                                             :moved-px 0
                                                             :scroll-top scroll-top})
                                   "pointercancel" (when-let [{:keys [moved-px] start :scroll-top} @down]
                                                     (set! (.-movedPx data) moved-px)
                                                     (set! (.-scrollDelta data) (- scroll-top start)))
                                   nil)
                                 (trace! event data)))))
                         #js {:capture true :passive true})))
  (.addEventListener js/document
                     "touchcancel"
                     (fn [^js e]
                       (trace! "touchcancel" #js {:target (some-> (.-target e) .-className)}))
                     #js {:capture true :passive true})

  (set! (.-__trace js/window) (fn [] (.slice trace))))


(defn install!
  "Wires every probe up."
  []
  (nxr/register-interceptor!
    {:before-dispatch (fn [ctx]
                        (swap! metrics update
                          (if (zero? @dispatch-depth) :dispatches :nested-dispatches)
                          inc)
                        (vswap! dispatch-depth inc)
                        ctx)
     :after-dispatch  (fn [ctx]
                        (vswap! dispatch-depth dec)
                        ctx)})

  (install-trace!)

  (count-frames!)

  ;; CLS is defined to ignore what follows the user's own interaction: every
  ;; layout-shift entry within 500 ms of input carries hadRecentInput, and the
  ;; score skips it. Typing is input, so a form that jumps under the finger
  ;; while a word is entered scores zero — web-vitals reports zero and is
  ;; right to. Hence a second counter off the same stream: :score sums every
  ;; entry regardless, and :input-excluded is the part CLS discarded, which on
  ;; a form measured while typing is all of it.
  (observe! "layout-shift"
            (fn [entry]
              (let [recent-input? (.-hadRecentInput ^js entry)
                    value (.-value ^js entry)]
                (swap! metrics update
                  :layout-shift
                  (fn [shift]
                    (cond-> (-> shift
                                (update :entries
                                        conj
                                        {:had-recent-input? recent-input?
                                         :start-time (.-startTime ^js entry)
                                         :value      value})
                                (update :score + value))
                      recent-input? (update :input-excluded + value))))))
            {})

  ;; A long animation frame is the whole frame — script, style, layout, paint
  ;; — where a long task was only the script inside it. `blockingDuration` is
  ;; the part that actually holds up input, and `scripts` names the culprit,
  ;; which is what the dropped long-task assertion lacked.
  (observe! "long-animation-frame"
            (fn [entry]
              (swap! metrics update
                :long-frames
                conj
                {:blocking-duration (.-blockingDuration ^js entry)
                 :duration   (.-duration ^js entry)
                 :scripts    (script-attribution entry)
                 :start-time (.-startTime ^js entry)}))
            {})

  (load-web-vitals!)

  ;; The page-facing API for tests and CDP evals. Functions, not values: each
  ;; call snapshots the atom at that moment — a value stored once would be
  ;; frozen at install time.
  (set! (.-__metrics js/window) (fn [] (clj->js @metrics)))
  (set! (.-__storage js/window) storage-estimate)
  (set! (.-__metricsReset js/window)
        (fn []
          (reset! metrics zero-metrics)
          (vreset! dispatch-depth 0)
          nil)))

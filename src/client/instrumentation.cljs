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


(defn dictionary-start!
  "Opens the readiness interval. Called before the dictionary worker is
   constructed, on the document's timeline — the worker's own
   `performance.now` runs against a different time origin and would need
   arithmetic to compare."
  []
  (.mark js/performance "dictionary-start"))


(defn dictionary-ready!
  "Closes the readiness interval. Records a User Timing measure, so the
   interval shows up in a performance trace, and keeps its duration."
  []
  (let [^js measure (try
                      (.measure js/performance "dictionary-ready" "dictionary-start")
                      (catch :default _ nil))]
    (when measure
      (swap! metrics assoc-in [:dictionary :ready-ms] (.-duration measure)))))


(defn dictionary-phase!
  "Keeps one startup phase the worker reported. The worker already times
   every phase for the log; this only carries the numbers into the metrics.
   `cache-hit` among them is what separates a warm start from a cold one."
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

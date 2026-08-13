(ns instrumentation
  "In-page measurement for development. Answers, with numbers, what the
   browser did: how many times the app rendered, how many dispatches ran and
   how they nested, whether layout shifted — both under the CLS rule and
   counting the shifts the user's own typing caused — which tasks ran long,
   which interactions were slow.

   Installed only under goog.DEBUG — a release build eliminates the whole
   namespace. Read from the page as `window.__metrics()`, reset with
   `window.__metricsReset()`.

   Honest limits, measured before this existed: the frame counter stops when
   the window is occluded even though visibilityState stays \"visible\", and a
   render here means one `render!` call — the browser still paints at most
   once per frame regardless of how many happened inside it."
  (:require
   [nexus.registry :as nxr]))


(defonce ^:private zero-metrics
  {:dispatches       0
   :frames           0
   :layout-shift     0.0
   :layout-shift-all 0.0
   :long-tasks       []
   :nested-dispatches 0
   :renders          0
   :slow-interactions []})


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

  ;; Two scores off one stream, so they can never disagree about which entries
  ;; they saw. :layout-shift keeps the CLS rule — hadRecentInput drops the
  ;; reflows that follow the user's own click or keystroke. :layout-shift-all
  ;; keeps everything: typing is recent input, so a form that jumps under the
  ;; cursor scores zero on the first counter and exists only on the second.
  (observe! "layout-shift"
            (fn [entry]
              (let [value        (.-value ^js entry)
                    user-caused? (.-hadRecentInput ^js entry)]
                (swap! metrics
                  (fn [m]
                    (cond-> (update m :layout-shift-all + value)
                      (not user-caused?) (update :layout-shift + value))))))
            {})

  (observe! "longtask"
            (fn [entry]
              (swap! metrics update
                :long-tasks
                conj
                {:duration (.-duration ^js entry)
                 :name     (.-name ^js entry)}))
            {})

  ;; Interactions the user would call sluggish. 100 ms is where an interface
  ;; stops feeling instant; faster events are noise at this level.
  (observe! "event"
            (fn [entry]
              (swap! metrics update
                :slow-interactions
                conj
                {:duration (.-duration ^js entry)
                 :type     (.-name ^js entry)}))
            {:durationThreshold 100})

  ;; The page-facing API for tests and CDP evals. Functions, not values: each
  ;; call snapshots the atom at that moment — a value stored once would be
  ;; frozen at install time.
  (set! (.-__metrics js/window) (fn [] (clj->js @metrics)))
  (set! (.-__metricsReset js/window)
        (fn []
          (reset! metrics zero-metrics)
          (vreset! dispatch-depth 0)
          nil)))

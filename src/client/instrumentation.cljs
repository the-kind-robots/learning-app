(ns instrumentation
  "In-page measurement for development. Answers, with numbers, what the
   browser did: how many times the app rendered, how many dispatches ran and
   how they nested, whether layout shifted, which tasks ran long, which
   interactions were slow.

   Installed only under goog.DEBUG — a release build eliminates the whole
   namespace. Read from the page as `window.__metrics()`, reset with
   `window.__metricsReset()`.

   Honest limits, measured before this existed: the frame counter stops when
   the window is occluded even though visibilityState stays \"visible\", and a
   render here means one store notification — the browser still paints at most
   once per frame regardless of how many happened inside it."
  (:require
   [nexus.registry :as nxr]))


(defonce ^:private zero-metrics
  {:dispatches   0
   :frames       0
   :layout-shift 0.0
   :long-tasks   []
   :nested-dispatches 0
   :renders      0
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


(defn install!
  "Wires every probe up. Takes the store so renders are counted the same way
   they are triggered — one notification, one render."
  [store]
  (add-watch store ::renders (fn [_ _ _ _] (swap! metrics update :renders inc)))

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

  ;; Shifts the user did not cause: hadRecentInput filters out reflows that
  ;; follow the user's own click or keystroke, the same rule CLS scoring uses.
  (observe! "layout-shift"
            (fn [entry]
              (when-not (.-hadRecentInput ^js entry)
                (swap! metrics update :layout-shift + (.-value ^js entry))))
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

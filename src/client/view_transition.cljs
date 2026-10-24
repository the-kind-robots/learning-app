(ns view-transition
  "Screen changes through the browser's View Transition API (ADR-0024). The kind of a change is the
   transition's type, which `blocks/view-transitions.css` draws by `:active-view-transition-type`.")


(def ^:private motion-query
  "Where a screen change moves: a phone, the width the layout switches at, with motion not turned down."
  "(max-width: 699px) and (prefers-reduced-motion: no-preference)")


(def ^:private native-animation-running?
  "Whether the browser animates a back gesture itself (a swipe on iPhone, predictive back on Android).
   Then the screen change runs without ours, or the screen would move twice."
  (volatile! false))


(defonce ^:private _browser-back
  ;; Registered at load, before the router's own listener, so the flag is up by the time the router renders.
  (js/window.addEventListener "popstate"
                              (fn [^js event]
                                (when (.-hasUAVisualTransition event)
                                  (vreset! native-animation-running? true)))))


(def ^:private waiting
  "The updates of a transition that has not captured the old screen yet, run in order once it has."
  (volatile! nil))


(defn change!
  "Runs `update`, which changes the page, as a `kind` of transition, or at once when `kind` is nil or
   motion is not wanted. Returns a promise of the update done; `update` may return one itself."
  [kind update]
  (let [native-animation? @native-animation-running?]
    (vreset! native-animation-running? false)
    (cond
      ;; Run now, it would land in the old screen's snapshot.
      @waiting
      (js/Promise. (fn [resolve _] (vswap! waiting conj #(resolve (update)))))

      (and kind
           (not native-animation?)
           (exists? js/ViewTransition)
           (js-in "types" (.-prototype js/ViewTransition))
           (.-matches (js/matchMedia motion-query)))
      (do (vreset! waiting [update])
          (.-updateCallbackDone
           (.startViewTransition js/document
                                 #js {:types  #js [(name kind)]
                                      :update (fn []
                                                (let [updates @waiting]
                                                  (vreset! waiting nil)
                                                  (js/Promise.all (to-array (map #(%) updates)))))})))

      :else
      (js/Promise.resolve (update)))))

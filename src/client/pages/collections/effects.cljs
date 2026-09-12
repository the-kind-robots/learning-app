(ns pages.collections.effects
  (:require
   [instrumentation :as instrumentation]
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [pages.collections.tap :as tap]
   [use-cases.collections :as collections]))


(def ^:private long-press-delay-ms 500)


(def ^:private long-press-cancel-px 10)


(defn- scroll-top
  []
  (or (some-> js/document .-scrollingElement .-scrollTop) 0))


(defn- trace!
  [kind data]
  (when ^boolean goog/DEBUG
    (instrumentation/trace! kind (clj->js data))))


(defn- track-gesture!
  "Follows one pointer from its pointerdown: how far it moved, whether the
   page scrolled, and whether the gesture already fired its tap. On
   pointercancel a tap the browser took without movement is recovered by
   dispatching `tap-actions`; a click that follows a recovered cancel is
   swallowed so the gesture fires once. `on-long-press`, when given, fires
   after `long-press-delay-ms` unless the pointer moved or lifted first."
  [{:keys [dispatch dispatch-data]} tap-actions on-long-press]
  (let [event (:replicant/dom-event dispatch-data)
        start-x (.-clientX event)
        start-y (.-clientY event)
        start-st (scroll-top)
        gesture (volatile! {:moved-px 0})
        timer (volatile! nil)
        move-fn (volatile! nil)
        up-fn (volatile! nil)
        click-fn (volatile! nil)
        stop-long-press
        (fn []
          (some-> @timer js/clearTimeout)
          (vreset! timer nil))
        cleanup (fn []
                  (stop-long-press)
                  (when-let [f @move-fn]
                    (js/window.removeEventListener "pointermove" f))
                  (when-let [f @up-fn]
                    (js/window.removeEventListener "pointerup" f)
                    (js/window.removeEventListener "pointercancel" f)))]
    (vreset! move-fn
             (fn [e]
               (let [dist (js/Math.hypot (- (.-clientX e) start-x)
                                         (- (.-clientY e) start-y))]
                 (vswap! gesture update :moved-px max dist)
                 (when (and @timer (> dist long-press-cancel-px))
                   (stop-long-press)))))
    (vreset! up-fn
             (fn [e]
               (cleanup)
               (when (= "pointercancel" (.-type e))
                 (let [decided (tap/on-cancel (assoc @gesture :scroll-delta (- (scroll-top) start-st)))]
                   (vreset! gesture decided)
                   (when (:dispatch? decided)
                     (trace! "tap-recovered" (select-keys decided [:moved-px :scroll-delta]))
                     ;; Chrome may still deliver the click of a cancelled
                     ;; touch; the first click after a recovery is this
                     ;; gesture's and must not fire twice.
                     (vreset! click-fn
                              (fn [click]
                                (js/window.removeEventListener "click" @click-fn true)
                                (when-not (:dispatch? (tap/on-click @gesture))
                                  (.stopPropagation click)
                                  (.preventDefault click))))
                     (js/window.addEventListener "click" @click-fn true)
                     (js/setTimeout #(js/window.removeEventListener "click" @click-fn true) 1000)
                     (dispatch tap-actions))))))
    (when on-long-press
      (vreset! timer
               (js/setTimeout
                (fn []
                  (vreset! timer nil)
                  (on-long-press))
                long-press-delay-ms)))
    (js/window.addEventListener "pointermove" @move-fn)
    (js/window.addEventListener "pointerup" @up-fn)
    (js/window.addEventListener "pointercancel" @up-fn)))


(nxr/register-effect! :effect/begin-long-press
  (fn begin-long-press [{:keys [dispatch] :as ctx} _ coll-id]
    (track-gesture! ctx
                    [[:action/handle-tab-click coll-id]]
                    (fn []
                      (dispatch [[:effect/save
                                  {:collections/editing-id        coll-id
                                   :collections/long-press-fired? true}]])))))


(nxr/register-effect! :effect/begin-tap
  ;; The main card has no long press; it gets the same tap recovery.
  (fn begin-tap [ctx _ tap-actions]
    (track-gesture! ctx tap-actions nil)))


(nxr/register-effect! :effect/exit-editing-on-background
  (fn exit-editing-on-background [{:keys [dispatch dispatch-data]} system _]
    (let [event (:replicant/dom-event dispatch-data)
          state @(:store system)]
      (cond
        (:collections/long-press-fired? state)
        (dispatch [[:effect/save {:collections/long-press-fired? false}]])

        (and (= (.-target event) (.-currentTarget event))
             (:collections/editing-id state))
        (dispatch [[:effect/save {:collections/editing-id nil}]])))))


(nxr/register-effect! :effect/load-collections
  (fn ^:async load-collections
    [{:keys [capabilities dispatch]} _]
    (try
      (let [data (await (collections/summary capabilities))]
        (dispatch [[:action/show-collections data]]))
      (catch js/Error err
        (log/error :effect/load-collections {:error (str err)})))))


(nxr/register-effect! :effect/prompt-create-collection
  (fn ^:async prompt-create-collection
    [{:keys [capabilities dispatch]} _]
    (try
      (when-let [raw-name (js/prompt "Название нового набора:")]
        (let [{:keys [ok]} (await (collections/create! capabilities raw-name))]
          (when ok
            (dispatch [[:effect/load-collections]]))))
      (catch js/Error err
        (log/error :effect/prompt-create-collection {:error (str err)})))))


(nxr/register-effect! :effect/delete-collection
  (fn ^:async delete-collection!
    [{:keys [capabilities dispatch]} _ {:keys [id]}]
    (try
      (await (collections/delete! capabilities id))
      (catch js/Error err
        (log/error :effect/delete-collection {:error (str err)}))
      (finally
       (dispatch [[:effect/load-collections]])))))


(nxr/register-effect! :effect/switch-active-collection
  (fn switch-active-collection
    [{:keys [capabilities dispatch]} _ collection-id]
    (collections/switch-active! capabilities collection-id)
    (dispatch [[:action/go-to-home]])))

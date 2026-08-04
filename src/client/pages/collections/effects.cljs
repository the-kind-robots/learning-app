(ns pages.collections.effects
  (:require
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [use-cases.collections :as collections]))


(def ^:private long-press-delay-ms 500)


(def ^:private long-press-cancel-px 10)


(nxr/register-effect! :effect/begin-long-press
  (fn begin-long-press [{:keys [dispatch dispatch-data]} _ coll-id]
    (let [event   (:replicant/dom-event dispatch-data)
          start-x (.-clientX event)
          start-y (.-clientY event)
          timer   (volatile! nil)
          move-fn (volatile! nil)
          up-fn   (volatile! nil)
          cleanup (fn []
                    (some-> @timer js/clearTimeout)
                    (vreset! timer nil)
                    (when-let [f @move-fn]
                      (js/window.removeEventListener "pointermove" f))
                    (when-let [f @up-fn]
                      (js/window.removeEventListener "pointerup" f)
                      (js/window.removeEventListener "pointercancel" f)))]
      (vreset! move-fn
               (fn [e]
                 (let [dist (js/Math.hypot (- (.-clientX e) start-x)
                                           (- (.-clientY e) start-y))]
                   (when (and @timer (> dist long-press-cancel-px))
                     (cleanup)))))
      (vreset! up-fn (fn [_] (cleanup)))
      (vreset! timer
               (js/setTimeout
                (fn []
                  (vreset! timer nil)
                  (dispatch [[:effect/save
                              {:collections/editing-id        coll-id
                               :collections/long-press-fired? true}]]))
                long-press-delay-ms))
      (js/window.addEventListener "pointermove" @move-fn)
      (js/window.addEventListener "pointerup" @up-fn)
      (js/window.addEventListener "pointercancel" @up-fn))))


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

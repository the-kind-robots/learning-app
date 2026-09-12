(ns pages.collections.actions
  (:require
   [nexus.registry :as nxr]))


(nxr/register-action! :action/open-collections
  (fn open-collections [_]
    ;; The screen switches before its data is read, so the page answers the
    ;; tap at once with a loading state. The items are left as they are: the
    ;; flag alone says the screen is loading, and only this action sets it,
    ;; so a reload of the same screen (after a sync pull) keeps the current
    ;; cards on screen until the new ones arrive.
    [[:effect/save
      {:page/current :page/collections
       :page/load    [:effect/load-collections]
       :collections/loading? true}]]))


(nxr/register-action! :action/show-collections
  (fn show-collections [_ {:keys [active-id items main]}]
    [[:effect/save
      {:page/current :page/collections
       :page/load [:effect/load-collections]
       :collections/active-id active-id
       :collections/editing-id nil
       :collections/items items
       :collections/loading? false
       :collections/long-press-fired? false
       :collections/main main}]]))


(nxr/register-action! :action/handle-tab-click
  (fn handle-tab-click [state coll-id]
    (cond
      (:collections/long-press-fired? state)
      [[:effect/save {:collections/long-press-fired? false}]]

      (:collections/editing-id state)
      [[:effect/save {:collections/editing-id nil}]]

      :else
      [[:effect/switch-active-collection coll-id]])))


(nxr/register-action! :action/handle-main-tab-click
  (fn handle-main-tab-click [state _]
    (cond
      (:collections/long-press-fired? state)
      [[:effect/save {:collections/long-press-fired? false}]]

      (:collections/editing-id state)
      [[:effect/save {:collections/editing-id nil}]]

      :else
      [[:effect/switch-active-collection]])))

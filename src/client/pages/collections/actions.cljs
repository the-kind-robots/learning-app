(ns pages.collections.actions
  (:require
   [nexus.registry :as nxr]))


(nxr/register-action! :action/show-collections
  (fn show-collections [_ {:keys [active-id items main]}]
    [[:effect/save
      {:app/page :page/collections
       :collections/active-id active-id
       :collections/editing-id nil
       :collections/items items
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

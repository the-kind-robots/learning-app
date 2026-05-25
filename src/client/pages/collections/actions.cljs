(ns pages.collections.actions
  (:require
   [nexus.registry :as nxr]))


(nxr/register-action! :action/show-collections
  (fn show-collections [_ {:keys [active-id active-name items]}]
    [[:effect/save
      {:app/page :page/collections
       :collections/active-id active-id
       :collections/active-name active-name
       :collections/items items}]]))

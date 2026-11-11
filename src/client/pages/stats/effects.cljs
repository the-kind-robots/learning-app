(ns pages.stats.effects
  (:require
   [nexus.registry :as nxr]
   [ports.sharing :as sharing]))


(nxr/register-effect! :effect/scroll-to-end
  ;; The days open on today, the last of them.
  (fn scroll-to-end [{:keys [dispatch-data]} _]
    (when-let [^js node (:replicant/node dispatch-data)]
      (set! (.-scrollLeft node) (.-scrollWidth node)))))


(nxr/register-effect! :effect/share-picture
  (fn share-picture [{:keys [capabilities]} _ file-name svg]
    (sharing/share-picture! (:sharing capabilities) file-name svg)))

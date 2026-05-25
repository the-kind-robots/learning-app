(ns ports.collections
  (:require
   [adapters.active-collection :as active-collection]
   [adapters.collections :as collections]))


(defn start!
  [{:keys [db]}]
  {:collections/active-id   active-collection/active-collection-id
   :collections/set-active! active-collection/set-active-collection!
   :collections/list        (fn list [] (collections/list-collections db))})

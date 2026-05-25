(ns pages.collections.effects
  (:require
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [use-cases.vocabulary :as vocabulary]
   [utils :as utils]))


(def ^:private preview-word-limit
  14)


(defn- preview-from-ids
  [vocab-index word-ids]
  (vec (keep vocab-index (take preview-word-limit word-ids))))


(defn- ^:async page-data
  [capabilities]
  (let [colls       (:collections capabilities)
        items       (await ((:collections/list colls)))
        all-words   (:words (await (vocabulary/list capabilities {:order :desc})))
        vocab-index (utils/index-by :_id all-words)
        main        {:preview-words (vec (take preview-word-limit all-words))}
        named       (mapv (fn [item]
                            (assoc item
                                   :preview-words
                                   (preview-from-ids vocab-index (:word-ids item))))
                          items)]
    {:active-id ((:collections/active-id colls))
     :items     named
     :main      main}))


(nxr/register-effect! :effect/load-collections
  (fn ^:async load-collections
    [{:keys [capabilities dispatch]} _]
    (try
      (let [data (await (page-data capabilities))]
        (dispatch [[:action/show-collections data]]))
      (catch js/Error err
        (log/error :effect/load-collections {:error (str err)})))))


(nxr/register-effect! :effect/switch-active-collection
  (fn switch-active-collection
    [{:keys [capabilities dispatch]} _ collection-id]
    ((:collections/set-active! (:collections capabilities)) collection-id)
    (dispatch [[:action/go-to-home]])))

(ns pages.collections.presenter)


(def ^:private preview-word-limit
  14)


(defn- translation-text
  [translation]
  (->> translation
       (filter #(= "ru" (:lang %)))
       first
       :value))


(defn- preview-word-props
  [{id :_id :keys [retention-level value translation]}]
  {:_id         id
   :retention-level retention-level
   :translation (translation-text translation)
   :value       value})


(defn- preview-words
  [words]
  (->> words
       (take preview-word-limit)
       (mapv preview-word-props)))


(defn page-props
  [state]
  {:active-id  (:collections/active-id state)
   :editing-id (:collections/editing-id state)
   :main       {:preview-words (preview-words (:words (:collections/main state)))}
   :items      (mapv #(assoc % :preview-words (preview-words (:words %)))
                     (:collections/items state))})

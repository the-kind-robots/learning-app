(ns pages.collections.presenter)


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


(defn- item-props
  [item]
  (update item :preview-words #(mapv preview-word-props %)))


(defn page-props
  [state]
  {:active-id  (:collections/active-id state)
   :editing-id (:collections/editing-id state)
   :items      (mapv item-props (:collections/items state))})

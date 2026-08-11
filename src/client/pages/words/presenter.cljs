(ns pages.words.presenter
  (:require
   [clojure.string :as str]))


(defn word-item-props
  [{id :_id type :type value :value translation :translation retention-level :retention-level}]
  {:id          id
   :phrase?     (= "phrase" type)
   :value       value
   :retention-level retention-level
   :translation (->> translation
                     (filter #(= "ru" (:lang %)))
                     (map :value)
                     (str/join ", "))})


(defn word-list-props
  [words]
  (mapv word-item-props words))

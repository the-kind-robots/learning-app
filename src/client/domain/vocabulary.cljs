(ns domain.vocabulary
  (:require
   [clojure.string :as str]
   [utils :as utils]))


(defn normalize-value
  [s]
  (utils/normalize-german (str s)))


(defn parse-translations
  [s]
  (->> (str/split (str s) #"[,;.]")
       (map str/trim)
       (remove str/blank?)
       (mapv (fn [v] {:lang "ru" :value v}))))


(defn merge-translations
  [existing new-translations]
  (let [seen (set (map :value existing))]
    (into (vec existing)
          (remove #(seen (:value %)) new-translations))))


(defn validate-new-word
  [value translation]
  (let [value-blank?       (str/blank? value)
        translation-blank? (str/blank? translation)]
    (if (or value-blank? translation-blank?)
      {:error {:value-blank? value-blank? :translation-blank? translation-blank?}}
      {:ok {:value value :translation translation}})))


(defn validate-word-update
  [{:keys [word-id translation]}]
  (cond
    (str/blank? word-id)     {:error {:word-id-missing? true}}
    (str/blank? translation) {:error {:translation-blank? true}}
    :else                    {:ok {:word-id word-id :translation translation}}))


(defn new-word
  [value translations created-at]
  {:type        "vocab"
   :value       value
   :translation translations
   :created-at  created-at
   :modified-at created-at})


(defn new-review
  [word-id retained translation created-at]
  {:type        "review"
   :word-id     word-id
   :retained    retained
   :created-at  created-at
   :translation [{:lang "ru" :value translation}]})


(defn update-word
  [doc translation modified-at]
  (cond-> (assoc doc
                 :translation (parse-translations translation)
                 :modified-at modified-at)
    (nil? (:created-at doc)) (assoc :created-at modified-at)))

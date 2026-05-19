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


(defn new-word
  [value translations]
  {:type        "vocab"
   :value       value
   :translation translations})


(defn new-review
  [word-id retained translation]
  {:type        "review"
   :word-id     word-id
   :retained    retained
   :translation [{:lang "ru" :value translation}]})


(defn update-word
  [doc translation]
  (assoc doc :translation (parse-translations translation)))

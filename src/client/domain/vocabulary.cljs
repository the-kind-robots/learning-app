(ns domain.vocabulary
  (:require
   [clojure.string :as str]
   [utils :as utils]))


(defn normalize-value
  [s]
  (utils/normalize-german (str s)))


(defn parse-translations
  "The entered text is one translation. It used to be split on `[,;.]`, which put
   the separator inside the data — `без того, чтобы` became two translations —
   while nothing read the pieces: grading compares against the German value and
   display glued them back with the separator they were cut on. Phrases never
   split; words no longer do either. Still a vector, so documents written before
   this are read as they are."
  [s]
  (let [value (str/trim (str s))]
    (if (str/blank? value)
      []
      [{:lang "ru" :value value}])))


(defn merge-translations
  [existing new-translations]
  (let [seen (set (map :value existing))]
    (into (vec existing)
          (remove #(seen (:value %)) new-translations))))


(defn vocab-id
  [value]
  (str "vocab:" (normalize-value value)))


(defn new-word
  [value translations]
  {:_id         (vocab-id value)
   :translation translations
   :type        "vocab"
   :value       value})


(defn new-review
  [word-id retained translation]
  {:type        "review"
   :word-id     word-id
   :retained    retained
   :translation [{:lang "ru" :value translation}]})


(defn update-word
  [doc translation]
  (assoc doc :translation (parse-translations translation)))

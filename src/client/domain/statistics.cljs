(ns domain.statistics
  "What the statistics count: the words by the percent the learner remembers them, and the lessons by day."
  (:require
   [clojure.string :as str]))


(defn word-bins
  "How many words of the `recalls`, each the probability the word is recalled (nil: never answered, 0 %), fall in
   each tenth, 0–10 % to 90–100 %, as the word list shows the percent: ten counts."
  [recalls]
  (let [counts (->> recalls
                    (map #(min 9 (quot (int (* 100 (or % 0))) 10)))
                    frequencies)]
    (mapv #(get counts % 0) (range 10))))


(defn word-groups
  "How many words of `bins` are in each of the word list's groups: `:retention/new` below 20 %, `:retention/due`
   to 50 %, `:retention/good` to 80 %, `:retention/excellent` from 80 %."
  [bins]
  (let [sum #(reduce + (subvec bins %1 %2))]
    {:retention/due       (sum 2 5)
     :retention/excellent (sum 8 10)
     :retention/good      (sum 5 8)
     :retention/new       (sum 0 2)}))


(defn- day
  "The local day of `date`, `YYYY-MM-DD`."
  [^js date]
  (let [two #(str (when (< % 10) "0") %)]
    (str (.getFullYear date) "-" (two (inc (.getMonth date))) "-" (two (.getDate date)))))


(defn lesson-days
  "The lessons of `times`, ISO times, by local day, oldest first, from the first lesson's to that of `now-ms`, or
   to the last lesson's when a device's clock ran ahead: `{:day \"YYYY-MM-DD\" :lessons n :today? bool}`, a day
   without lessons counted 0. Without lessons, today alone."
  [times now-ms]
  (let [counts (->> times
                    (map #(day (js/Date. %)))
                    frequencies)
        today  (day (js/Date. now-ms))
        days   (sort (cons today (keys counts)))
        [year month date] (map js/parseInt (str/split (first days) #"-"))]
    (->> (range)
         (map #(day (js/Date. year (dec month) (+ date %))))
         (take-while #(<= (compare % (last days)) 0))
         (mapv (fn [day-key]
                 {:day     day-key
                  :lessons (get counts day-key 0)
                  :today?  (= day-key today)})))))

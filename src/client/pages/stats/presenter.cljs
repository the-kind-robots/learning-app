(ns pages.stats.presenter
  (:require
   [application.settings.presenter :as settings]
   [clojure.string :as str]
   [domain.statistics :as statistics]
   [retention-colour :as retention-colour]))


(defn- columns
  "The histogram's columns, one per tenth of memory: each its `:words`, its `:height` in percent of the tallest, and
   the word list's `:color` of its middle percent."
  [word-bins]
  (let [tallest (max 1 (apply max word-bins))]
    (map-indexed (fn [index words]
                   {:color  (retention-colour/at (+ 5 (* 10 index)))
                    :height (* 100 (/ words tallest))
                    :words  words})
                 word-bins)))


(defn- shares
  "The strip's shares, the word list's four groups: each its `:name` (the key of its text), its `:words` and the
   word list's `:color` of its middle."
  [word-bins]
  (let [counts (statistics/word-groups word-bins)]
    (for [[group middle] [[:retention/new 10] [:retention/due 35] [:retention/good 65] [:retention/excellent 90]]]
      {:color (retention-colour/at middle)
       :name  group
       :words (get counts group)})))


(defn- date
  "The local date of `day`, `YYYY-MM-DD`."
  [day]
  (let [[year month date] (map js/parseInt (str/split day #"-"))]
    (js/Date. year (dec month) date)))


(defn- months
  "The `lesson-days` by month, each an `:id`, a `:name` and its `:days`: `:id`, `:lessons` (nil for none), `:height`
   in percent of the busiest of them, `:purple?` from five lessons, the `:day` of the month, `:weekday`, `:weekend?`,
   `:today?`."
  [language lesson-days]
  (let [^js month-name (js/Intl.DateTimeFormat. language #js {:month "long"})
        ^js weekday    (js/Intl.DateTimeFormat. language #js {:weekday "short"})
        busiest        (max 1 (apply max 0 (map :lessons lesson-days)))]
    (->> lesson-days
         (partition-by #(subs (:day %) 0 7))
         (map (fn [month]
                {:id   (subs (:day (first month)) 0 7)
                 :days (for [{:keys [day lessons today?]} month
                             :let [^js on (date day)]]
                         {:id       day
                          :day      (.getDate on)
                          :height   (* 100 (/ lessons busiest))
                          :lessons  (when (pos? lessons) lessons)
                          :purple?  (>= lessons 5)
                          :today?   today?
                          :weekday  (.format weekday on)
                          :weekend? (contains? #{0 6} (.getDay on))})
                 :name (.format month-name (date (:day (first month))))})))))


(defn page-props
  "What the statistics screen shows: the number of `:words`, the histogram's `:columns` and the strip's `:shares`;
   the number of `:lessons` and the days by `:months`."
  [state]
  (let [word-bins   (:stats/word-bins state)
        lesson-days (:stats/lesson-days state)]
    {:columns (columns word-bins)
     :lessons (reduce + (map :lessons lesson-days))
     :months  (months (settings/language state) lesson-days)
     :shares  (shares word-bins)
     :words   (reduce + word-bins)}))


(defn picture-props
  "What the shared picture shows: what the screen does, with only the last fourteen days, and today's `:date`
   written out."
  [state]
  (let [word-bins   (:stats/word-bins state)
        lesson-days (:stats/lesson-days state)
        language    (settings/language state)
        ^js written (js/Intl.DateTimeFormat. language #js {:day "numeric" :month "long" :year "numeric"})]
    {:columns (columns word-bins)
     :date    (->> lesson-days (filter :today?) first :day date (.format written))
     :lessons (reduce + (map :lessons lesson-days))
     :months  (months language (take-last 14 lesson-days))
     :shares  (shares word-bins)
     :words   (reduce + word-bins)}))

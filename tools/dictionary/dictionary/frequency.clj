(ns dictionary.frequency
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [utils :refer [normalize-german]]))


(defn- split-row
  [line]
  (str/split line #"\t|,"))


(defn- parse-double-safe
  [value]
  (try
    (parse-double (str/trim (str value)))
    (catch Exception _
      nil)))


(defn- parse-long-safe
  [value]
  (try
    (parse-long (str/trim (str value)))
    (catch Exception _
      nil)))


(defn- header?
  [row]
  (some #(= "word" (str/lower-case (str/trim %))) row))


(defn- row->record
  [headers row position]
  (let [data   (zipmap headers row)
        word   (or (get data "word") (first row))
        count  (parse-double-safe (or (get data "count") (second row)))
        rank   (parse-long-safe (get data "rank"))
        source (or (get data "source") "frequency-file")]
    (when (and (seq (str/trim (str word)))
               (or count rank))
      [(normalize-german word)
       (cond-> {:source source
                :rank   (or rank position)}
         count (assoc :count count))])))


(defn read-frequency-file
  "Read optional TSV/CSV frequency data.
   Supported headers: word, count, rank, source.
   `count` is a normalized per-million word rate (linear, sums across
   surface forms).
   Minimal format without header: word<TAB>count."
  [path]
  (if (str/blank? path)
    {}
    (with-open [reader (io/reader path)]
      (let [rows        (->> (line-seq reader)
                             (map str/trim)
                             (remove #(or (str/blank? %) (str/starts-with? % "#")))
                             (map split-row)
                             (vec))
            first-row   (first rows)
            has-header? (header? first-row)
            headers     (if has-header?
                          (mapv #(str/lower-case (str/trim %)) first-row)
                          ["word" "count"])
            data-rows   (if has-header? (rest rows) rows)
            sorted      (if (some #{"rank"} headers)
                          data-rows
                          (sort-by (fn [row]
                                     (- (or (parse-double-safe (or (second row) "0")) 0.0)))
                                   data-rows))]
        (into {}
              (keep-indexed (fn [index row]
                              (row->record headers row (inc index))))
              sorted)))))

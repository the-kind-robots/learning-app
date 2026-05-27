(ns dictionary.transform
  (:require [clojure.string :as str]
            [dictionary.goethe :as goethe]
            [dictionary.kaikki :as kaikki]
            [utils :refer [normalize-german]]))


(def ^:private cefr-base-rank
  {"a1" 30000
   "a2" 20000
   "b1" 10000})


(def ^:private frequency-base-rank 1000000)


(def ^:private allowed-pos
  #{"adj"
    "adv"
    "conj"
    "intj"
    "noun"
    "num"
    "particle"
    "phrase"
    "postp"
    "prep"
    "pron"
    "verb"})


(defn- compact-senses
  [senses]
  (->> senses
       (map (fn [sense]
              (let [tags    (distinct (filter string? (:tags sense)))
                    glosses (distinct (filter string? (:glosses sense)))]
                (cond-> {}
                  (seq tags)    (assoc :tags tags)
                  (seq glosses) (assoc :glosses glosses)))))
       (remove empty?)
       (distinct)
       (vec)))


(defn- lemma-value
  [kaikki-entry pos]
  (or (when (= "noun" pos)
        (kaikki/canonical-noun-form kaikki-entry))
      (:word kaikki-entry)))


(defn- lemma-id-value
  [value]
  (str/lower-case value))


(defn bare-word
  "Strip article prefix from a noun value. \"der Hund\" → \"Hund\", \"gehen\" → \"gehen\"."
  [value]
  (if-let [[_ word] (re-matches #"(?:der|die|das)\s+(.*)" value)]
    word
    value))


(defn- frequency-candidates
  [kaikki-entry value]
  (distinct [value (bare-word value) (:word kaikki-entry)]))


(defn- frequency-for-entry
  [frequency-index kaikki-entry value]
  (when (seq frequency-index)
    (let [matches (->> (frequency-candidates kaikki-entry value)
                       (keep (fn [candidate]
                               (when-let [frequency (get frequency-index (normalize-german candidate))]
                                 (assoc frequency :matched candidate)))))]
      (when (seq matches)
        (let [best        (apply min-key :rank matches)
              total-count (reduce + 0 (keep :count matches))]
          (cond-> best
            (pos? total-count) (assoc :count total-count)))))))


(defn- compute-rank
  "Compute rank for an entry. Higher = more important."
  [cefr-level sense-count translation-count frequency]
  (if-let [freq-rank (:rank frequency)]
    (max 1 (- frequency-base-rank freq-rank))
    (if-let [base (cefr-base-rank cefr-level)]
      (+ base (* sense-count 10) translation-count)
      (min 5000 (+ (* sense-count 100) (* translation-count 10))))))


(defn lemma
  [kaikki-entry goethe-index frequency-index]
  (let [pos (str/lower-case (:pos kaikki-entry "unknown"))]
    (when (allowed-pos pos)
      (let [word         (:word kaikki-entry)
            value        (lemma-value kaikki-entry pos)
            discriminant (kaikki/entry-discriminant kaikki-entry pos)
            text-id      (cond-> (str "lemma:" (lemma-id-value value) ":" pos)
                           discriminant (str ":" discriminant))
            translations (kaikki/russian-translations kaikki-entry)
            senses       (:senses kaikki-entry)
            sense-count  (count senses)
            translation-count (count translations)
            cefr         (goethe/cefr-level goethe-index word)
            frequency    (frequency-for-entry frequency-index kaikki-entry value)
            rank         (compute-rank cefr sense-count translation-count frequency)]
        {:text-id      text-id
         :value        value
         :pos          pos
         :rank         rank
         :translations translations
         :forms        (kaikki/inflected-forms kaikki-entry)
         :enrichment   {:cefr-level  cefr
                        :senses      (compact-senses senses)
                        :sense-count sense-count}}))))

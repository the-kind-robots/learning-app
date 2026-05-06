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


(defn- compute-rank
  "Compute rank for an entry. Higher = more important."
  ([cefr-level sense-count translation-count]
   (compute-rank cefr-level sense-count translation-count nil))
  ([cefr-level sense-count translation-count frequency]
   (if-let [freq-rank (:rank frequency)]
     (max 1 (- frequency-base-rank freq-rank))
     (if-let [base (cefr-base-rank cefr-level)]
       (+ base (* sense-count 10) translation-count)
       (min 5000 (+ (* sense-count 100) (* translation-count 10)))))))


(defn- compact-senses
  "Keep only sense data useful for translation enrichment.
   Raw tags, examples, etymology, sounds, etc. stay out of emitted docs."
  [senses]
  (->> senses
       (map (fn [sense]
              (let [tags    (vec (distinct (filter string? (:tags sense))))
                    glosses (vec (distinct (filter string? (:glosses sense))))]
                (cond-> {}
                  (seq tags)    (assoc :tags tags)
                  (seq glosses) (assoc :glosses glosses)))))
       (remove empty?)
       (distinct)
       (vec)))


(defn- entry-value
  "For nouns, prepend article (\"der Hund\"). Others use bare word."
  [word pos article]
  (if (and (= "noun" pos) article)
    (str article " " word)
    word))


(defn- entry-id-value
  "Keep lemma identity distinct from normalized lookup keys.
   Example: das Roß and das Ross share a surface key, but not a document ID."
  [value]
  (str/lower-case value))


(declare bare-word)


(defn- frequency-candidates
  [kaikki-entry value]
  (distinct
   (concat
    [value
     (bare-word value)
     (:word kaikki-entry)]
    (keep :form (:forms kaikki-entry)))))


(defn- frequency-for-entry
  [frequency-index kaikki-entry value]
  (when (seq frequency-index)
    (let [matches (->> (frequency-candidates kaikki-entry value)
                       (keep (fn [candidate]
                               (when-let [frequency (get frequency-index (normalize-german candidate))]
                                 (assoc frequency :matched candidate))))
                       (vec))]
      (when (seq matches)
        (let [best        (apply min-key :rank matches)
              total-count (reduce + 0 (keep :count matches))]
          (cond-> best
            (pos? total-count) (assoc :count total-count)))))))


(defn dictionary-entry
  "Build a dictionary-entry map from a merged Kaikki entry. Entries without
   Russian translations are included with an empty :translation vector so the
   enrichment script can fill them in later."
  ([kaikki-entry goethe-index timestamp]
   (dictionary-entry kaikki-entry goethe-index timestamp nil))
  ([kaikki-entry goethe-index timestamp frequency-index]
   (let [pos (str/lower-case (or (:pos kaikki-entry) "unknown"))]
     (when (allowed-pos pos)
       (let [word   (:word kaikki-entry)
             article (when (= "noun" pos) (kaikki/extract-article kaikki-entry))
             value        (entry-value word pos article)
             norm         (normalize-german value)
             id           (str "lemma:" (entry-id-value value) ":" pos)
             translations (kaikki/russian-translations kaikki-entry)
             senses       (:senses kaikki-entry)
             compact      (compact-senses senses)
             sense-count  (count senses)
             trans-count  (count translations)
             cefr         (goethe/cefr-level goethe-index word)
             frequency    (frequency-for-entry frequency-index kaikki-entry value)
             rank         (compute-rank cefr sense-count trans-count frequency)
             forms        (kaikki/inflected-forms kaikki-entry)
             meta         (cond-> {:normalized-value norm
                                   :cefr-level       cefr
                                   :gender           article
                                   :senses           compact
                                   :sense-count      sense-count
                                   :source           "kaikki-enwiktionary"}
                            frequency (assoc :frequency frequency))]
         {:_id         id
          :type        "dictionary-entry"
          :value       value
          :pos         pos
          :rank        rank
          :translation translations
          :forms       forms
          :meta        meta
          :created-at  timestamp
          :modified-at timestamp})))))


(defn- bare-word
  "Strip article prefix from a noun value. \"der Hund\" → \"Hund\", \"gehen\" → \"gehen\"."
  [value]
  (if-let [[_ word] (re-matches #"(?:der|die|das)\s+(.*)" value)]
    word
    value))


(defn accumulate-surface-forms
  "For each surface form (bare word, value with article, all inflected forms):
   normalize and add {:lemma-id :lemma :rank} to index under that normalized key.
   Returns updated index."
  [index dict-entry]
  (let [entry     {:lemma-id (:_id dict-entry)
                   :lemma    (:value dict-entry)
                   :rank     (:rank dict-entry)}
        value     (:value dict-entry)
        bare      (bare-word value)
        all-forms (distinct (concat [value bare] (:forms dict-entry)))]
    (reduce (fn [idx raw-form]
              (let [norm (normalize-german raw-form)]
                (update idx norm (fnil conj []) entry)))
            index
            all-forms)))


(defn surface-form-documents
  "Convert surface-form index to sorted seq of surface-form docs.
   Each doc has entries sorted by rank descending."
  [index]
  (->> index
       (sort-by key)
       (map (fn [[norm-form entries]]
              {:_id     (str "sf:" norm-form)
               :type    "surface-form"
               :value   norm-form
               :entries (->> entries
                             (distinct)
                             (sort-by :rank >)
                             (vec))}))))

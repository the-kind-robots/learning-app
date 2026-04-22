(ns dictionary.transform
  (:require [clojure.string :as str]
            [dictionary.goethe :as goethe]
            [dictionary.kaikki :as kaikki]
            [utils :refer [normalize-german]]))


(def ^:private cefr-base-rank
  {"a1" 30000
   "a2" 20000
   "b1" 10000})


(def ^:private allowed-pos
  #{"adj"
    "adv"
    "conj"
    "intj"
    "name"
    "noun"
    "num"
    "particle"
    "phrase"
    "postp"
    "prep"
    "pron"
    "unknown"
    "verb"})


(defn- compute-rank
  "Compute rank for an entry. Higher = more important."
  [cefr-level sense-count translation-count]
  (if-let [base (cefr-base-rank cefr-level)]
    (+ base (* sense-count 10) translation-count)
    (min 5000 (+ (* sense-count 100) (* translation-count 10)))))


(defn- entry-value
  "For nouns, prepend article (\"der Hund\"). Others use bare word."
  [word pos gender]
  (if (and (= "noun" pos) gender)
    (str gender " " word)
    word))


(defn dictionary-entry
  "Build a dictionary-entry map from a merged Kaikki entry, or nil if no Russian translations."
  [kaikki-entry goethe-index timestamp]
  (let [word   (:word kaikki-entry)
        pos    (str/lower-case (or (:pos kaikki-entry) "unknown"))
        gender (when (= "noun" pos) (kaikki/extract-gender kaikki-entry))]
    (when (allowed-pos pos)
      (let [value        (entry-value word pos gender)
            norm         (normalize-german value)
            id           (str "lemma:" norm ":" pos)
            translations (kaikki/russian-translations kaikki-entry)
            senses       (:senses kaikki-entry)
            sense-count  (count senses)
            trans-count  (count translations)
            cefr         (goethe/cefr-level goethe-index word)
            rank         (compute-rank cefr sense-count trans-count)
            forms        (kaikki/inflected-forms kaikki-entry)]
        (when (and (seq translations)
                   (not (and (= "name" pos) (nil? cefr))))
          {:_id         id
           :type        "dictionary-entry"
           :value       value
           :pos         pos
           :rank        rank
           :translation translations
           :forms       forms
           :meta        {:normalized-value norm
                         :cefr-level       cefr
                         :gender           gender
                         :sense-count      sense-count
                         :source           "kaikki-enwiktionary"}
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

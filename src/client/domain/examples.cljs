(ns domain.examples
  "Which examples a read sees, which of them a word shows, and which pairs of
   entry and collection have none. A lesson, the word dialog and the example
   backfill read the same rules."
  (:require
   [clojure.string :as str]))


(defn visible-in
  "The `examples` a read in `family`, a collection and then its children (`domain.collections/family`), sees:
   of each word's, those made for the collection, otherwise those made for its children. nil, outside every
   collection, sees all."
  [family examples]
  (if-let [[{own-id :id} & children] (seq family)]
    (let [child-ids (set (map :id children))]
      (into []
            (mapcat (fn [word-examples]
                      (or (not-empty (filterv #(= own-id (:collection-id %)) word-examples))
                          (filter #(child-ids (:collection-id %)) word-examples))))
            (vals (group-by :word-id examples))))
    examples))


(defn in-language
  "Those of `examples` translated into `language`. One stored before examples carried a language is Russian."
  [language examples]
  (filterv #(= language (:language % "ru")) examples))


(defn missing-pairs
  "The pairs of `entry` that have no example, `{:collection-id :collection-name :word}`; an entry in no
   collection misses one only without any example, as `{:word entry}`."
  [entry collections examples]
  (if (seq collections)
    (into []
          (comp (filter (fn [{:keys [id]}]
                          (not-any? #(= id (:collection-id %)) examples)))
                (map (fn [{:keys [id name]}]
                       {:collection-id id :collection-name name :word entry})))
          collections)
    (when (empty? examples)
      [{:word entry}])))


(defn lesson-example
  "The one of `examples`, those of one word that a read sees, that a lesson asks and the word dialog shows:
   the smallest id, the same every time. nil without examples."
  [examples]
  (first (sort-by :id examples)))


(defn complete?
  "Whether `example` has both its sentence and its translation, more than blanks."
  [{:keys [translation value]}]
  (not (or (str/blank? value) (str/blank? translation))))

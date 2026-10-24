(ns pages.words.edit
  "What the word dialog knows of its edit, the `:words/editing` map: which collections get a card, which
   examples are changed, and whether the edit is fit to save."
  (:require
   [clojure.string :as str]
   [domain.collections :as collections]
   [domain.examples :as domain-examples]))


(defn collection-ids
  "The ids of the collections the dialog has a card for: the one of the scope first, then the others that list
   `word-id`, and nil, no collection, last when the word is in none or has an example of none."
  [word-id active listing examples]
  (let [listed (filter #(some #{word-id} (:word-ids %)) listing)
        scoped (some (set listed) (collections/family listing (:id active)))]
    (cond-> (into (if scoped [(:id scoped)] [])
                  (comp (map :id) (remove #{(:id scoped)}))
                  listed)
      (or (empty? listed) (some (comp nil? :collection-id) examples)) (conj nil))))


(defn opened-examples
  "The example of each collection of `ids`, `{collection-id example}`: the one a lesson there asks, blank where
   there is none."
  [ids examples]
  (let [by-collection (update-vals (group-by :collection-id examples) domain-examples/lesson-example)]
    (into {} (map (fn [id] [id (get by-collection id {:translation "" :value ""})])) ids)))


(defn changed-examples
  "The examples of `edit` that differ from the ones it opened with, `{:collection-id :example}` each."
  [{:keys [collection-ids examples opened-examples]}]
  (for [id collection-ids
        :let [now (get examples id)]
        :when (not= now (get opened-examples id))]
    {:collection-id id :example now}))


(defn valid?
  "Whether `edit` is fit to save: its word is neither blank nor another word's, and every changed example has
   its sentence and its translation."
  [{:keys [word word-taken?] :as edit}]
  (boolean (and (not (str/blank? word))
                (not word-taken?)
                (every? (comp domain-examples/complete? :example) (changed-examples edit)))))

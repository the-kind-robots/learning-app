(ns pages.words.edit
  "What the word dialog knows of its edit, the `:words/editing` map: which collections get a card, which
   examples are changed, and whether the edit is fit to save."
  (:require
   [clojure.string :as str]
   [domain.collections :as domain-collections]
   [domain.examples :as domain-examples]
   [domain.vocabulary :as vocabulary]))


(defn collection-ids
  "The ids of the collections the dialog has a card for: the open collection, or the one of its family that
   lists `word-id`, first, then the other `collections` that list it, and nil, no collection, last when the
   word is in none or has an example of none among `word-examples`."
  [word-id open-collection collections word-examples]
  (let [listed (filter #(some #{word-id} (:word-ids %)) collections)
        scoped (some (set listed) (domain-collections/family collections (:id open-collection)))]
    (cond-> (->> listed
                 (map :id)
                 (remove #{(:id scoped)})
                 (concat (when scoped [(:id scoped)]))
                 vec)
      (or (empty? listed) (some (comp nil? :collection-id) word-examples)) (conj nil))))


(defn opened-examples
  "The example of each of `collection-ids`, `{collection-id example}`: the one a lesson there asks among
   `word-examples`, blank where there is none."
  [collection-ids word-examples]
  (let [by-collection (update-vals (group-by :collection-id word-examples) domain-examples/lesson-example)]
    (zipmap collection-ids (map #(get by-collection % {:translation "" :value ""}) collection-ids))))


(defn renamed?
  "Whether the word typed in `edit` is another word than the one it is the edit of: it is not blank and its id
   differs from `:id`. Saved so, it is a new word in the place of the old."
  [{:keys [id word]}]
  (boolean (and (not (str/blank? word))
                (not= id (vocabulary/vocab-id word)))))


(defn changed-examples
  "The examples of `edit` that differ from the ones it opened with, `{:collection-id :example}` each."
  [{:keys [collection-ids examples opened-examples]}]
  (for [id    collection-ids
        :let  [now (get examples id)]
        :when (not= now (get opened-examples id))]
    {:collection-id id :example now}))


(defn valid?
  "Whether `edit` is fit to save: its word is neither blank nor another word's, and every changed example has
   its sentence and its translation."
  [{:keys [word word-taken?] :as edit}]
  (boolean (and (not (str/blank? word))
                (not word-taken?)
                (every? (comp domain-examples/complete? :example) (changed-examples edit)))))

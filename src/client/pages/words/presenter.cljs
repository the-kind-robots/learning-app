(ns pages.words.presenter
  (:require
   [clojure.string :as str]
   [utils :as utils]))


(defn- retention-band
  "How well the word is remembered, as the list says it: from `level`, a
   percentage."
  [level]
  (cond
    (>= level 80) :retention/excellent
    (>= level 50) :retention/good
    (>= level 20) :retention/due
    :else         :retention/new))


(defn word-item-props
  [{:keys [id kind value translation retention-level]}]
  {:id          id
   :phrase?     (= "phrase" kind)
   :retention-band    (retention-band retention-level)
   :retention-color   (utils/prozent->color retention-level)
   :retention-percent (int retention-level)
   :value       value
   :translation (->> translation
                     (filter #(= "ru" (:lang %)))
                     (map :value)
                     (str/join ", "))})


(defn word-list-props
  [words]
  (mapv word-item-props words))


(defn empty-state
  "Which placeholder is shown instead of the word list, `:empty/no-matches`
   or `:empty/first-run`, or nil when there are rows to render. `total` counts the words in the active scope before the
   search filter, so an empty vocabulary and a filter that matched nothing tell
   apart here rather than in the view."
  [words total]
  (when (and (some? total) (empty? words))
    (if (pos? total)
      :empty/no-matches
      :empty/first-run)))


(defn page-props
  "What the word list renders: the rows, the placeholder that replaces them,
   and whether the page chrome — header, search box, lesson button — applies.
   An empty vocabulary drops the chrome; an empty filter keeps it so the query
   stays editable. Until the learner's data is in memory `total` is nil and
   the list shows nothing — neither placeholder."
  [{:words/keys [editing in-collection? more? rows search total]}]
  {:editing     editing
   :in-collection? (boolean in-collection?)
   :empty-state (empty-state rows total)
   :items       (word-list-props rows)
   :known?      (some? total)
   :more?       more?
   :search      search
   :vocabulary? (boolean (some-> total pos?))})

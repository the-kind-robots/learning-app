(ns pages.words.presenter
  (:require
   [clojure.string :as str]))


(def ^:private first-run-state
  {:cta  "Добавить слово"
   :hint "Добавьте первое слово на главной странице"
   :text "Слов пока нет"})


(def ^:private no-matches-state
  {:cta  nil
   :hint "Попробуйте другой запрос"
   :text "Ничего не найдено"})


(defn word-item-props
  [{:keys [id kind value translation retention-level]}]
  {:id          id
   :phrase?     (= "phrase" kind)
   :value       value
   :retention-level retention-level
   :translation (->> translation
                     (filter #(= "ru" (:lang %)))
                     (map :value)
                     (str/join ", "))})


(defn word-list-props
  [words]
  (mapv word-item-props words))


(defn empty-state
  "Props for the placeholder shown instead of the word list, or nil when there
   are rows to render. `total` counts the words in the active scope before the
   search filter, so an empty vocabulary and a filter that matched nothing tell
   apart here rather than in the view."
  [words total]
  (when (and (some? total) (empty? words))
    (if (pos? total)
      no-matches-state
      first-run-state)))


(defn page-props
  "What the word list renders: the rows, the placeholder that replaces them,
   and whether the page chrome — header, search box, lesson button — applies.
   An empty vocabulary drops the chrome; an empty filter keeps it so the query
   stays editable. Until the learner's data is in memory `total` is nil and
   the list shows nothing — neither placeholder."
  [{:words/keys [editing more? rows search total]}]
  {:editing     editing
   :empty-state (empty-state rows total)
   :items       (word-list-props rows)
   :known?      (some? total)
   :more?       more?
   :search      search
   :vocabulary? (boolean (some-> total pos?))})

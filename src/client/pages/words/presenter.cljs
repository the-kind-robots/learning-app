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
  [{id :_id kind :kind value :value translation :translation retention-level :retention-level}]
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
  (when (empty? words)
    (if (pos? total)
      no-matches-state
      first-run-state)))


(defn page-state
  "State for the word list: the rows, the placeholder that replaces them, and
   whether the page chrome — header, search box, lesson button — applies. An
   empty vocabulary drops the chrome; an empty filter keeps it so the query
   stays editable."
  [{:keys [search total words]}]
  {:words/empty-state (empty-state words total)
   :words/items       (word-list-props words)
   :words/search      (or search "")
   :words/vocabulary? (pos? total)})

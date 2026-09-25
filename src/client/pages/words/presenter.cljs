(ns pages.words.presenter
  (:require
   [clojure.string :as str]))


(def page-size
  "Rows the list grows by. The first render asks for one page; reaching the
   bottom asks for one more."
  50)


(defn next-limit
  "The row count to ask for after the reader reached the bottom."
  [limit]
  (+ (or limit 0) page-size))


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
  (when (empty? words)
    (if (pos? total)
      no-matches-state
      first-run-state)))


(defn loading?
  "Whether the screen is still waiting for its first rows: a read is running
   and none has answered since the route entered (`:words/items` is nil until
   then, and a vector — empty or not — after)."
  [state]
  (boolean (and (:words/current-read state)
                (nil? (:words/items state)))))


(defn page-state
  "State for the word list: the rows, the placeholder that replaces them, and
   whether the page chrome — header, search box, lesson button — applies. An
   empty vocabulary drops the chrome; an empty filter keeps it so the query
   stays editable.

   `limit` is the row count these rows were asked for, kept so a reload after
   an edit lands on the same rows. `more?` compares the rows against
   `matches`, the count the search left, so the view only has to know whether
   to render the sentinel — `total` counts before the filter and cannot
   answer this."
  [{:keys [limit matches search total words]}]
  {:words/empty-state (empty-state words total)
   ;; At least one word in the active scope, before the search filter.
   :words/has-words?  (pos? total)
   :words/items       (word-list-props words)
   :words/limit       (or limit page-size)
   :words/more?       (< (count words) (or matches 0))
   :words/search      (or search "")})

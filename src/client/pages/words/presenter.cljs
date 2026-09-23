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


(defn next-read-token
  "The number for the next read of the list. Reads are numbered because five
   callers write `:words/*` — the first render, the search, the next page, and
   the reloads after an edit and after a synchronisation pull — and the
   storage they go through answers in its own order."
  [state]
  (inc (or (:words/read-token state) 0)))


(defn current-read?
  "Whether these rows answer the read the list is still waiting for. A read
   that a later one overtook is dropped, so a page asked for before a search
   cannot land after it and put the unfiltered rows back under a search box
   that still holds the query."
  [state {:keys [token]}]
  (= token (:words/read-token state)))


(defn more-to-read?
  "Whether reaching the end should ask for another page. A search that has not
   answered yet says no: its rows replace the ones that page would extend, and
   the query the page would carry is already the one being replaced."
  [state]
  (boolean (and (:words/more? state)
                (nil? (:words/pending-search state)))))


(defn new-query?
  "Whether arriving rows answer a different query than the rows on screen.
   They do when the reader's typing has been read: these rows replace what was
   being read rather than extending it, and that is when the list goes back to
   its first row — not on the keystroke 400 ms earlier, with the old rows still
   under a reader free to scroll them."
  [state {:keys [search]}]
  (not= (or search "") (or (:words/search state) "")))


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
   :words/items       (word-list-props words)
   :words/limit       (or limit page-size)
   :words/more?       (< (count words) (or matches 0))
   :words/search      (or search "")
   :words/vocabulary? (pos? total)})

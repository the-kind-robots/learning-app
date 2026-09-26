(ns pages.words.actions
  (:require
   [nexus.registry :as nxr]
   [use-cases.collections :as collections]
   [use-cases.vocabulary :as vocabulary]))


(def ^:private page-size
  "Rows the list grows by. Entry and a new query show one page; reaching the
   bottom shows one more."
  50)


(defn content
  "The rows the word list shows, cut from the learner's data in `state` by the
   query and the row count the list holds: `:words/rows`, `:words/total` (the
   words in scope before the query, which is how an empty vocabulary and a
   query with no match tell apart) and `:words/more?` (whether the query
   matched more than the rows shown).

   Before memory is ready it shows no rows and, with `:words/total` nil, makes
   no claim about the vocabulary either way."
  [state {:keys [active-id now-ms]}]
  (if-not (:learner/ready? state)
    {:words/more? false
     :words/rows  []
     :words/total nil}
    (let [memory (:learner/memory state)
          {:keys [matches total words]}
          (vocabulary/rows memory
                           {:limit    (:words/limit state)
                            :search   (:words/search state)
                            :word-ids (collections/active-scope memory active-id)}
                           now-ms)]
      {:words/more? (< (count words) matches)
       :words/rows  words
       :words/total total})))


(defn- shown
  "`changes` to the list's query or row count, with the rows they cut."
  [state context changes]
  (let [state (merge state changes)]
    (merge changes (content state context))))


(nxr/register-action! :action/open-words
  ;; The screen and its first page in one write, in the task of the tap. It
  ;; opens no dialog: leaving with a word open would otherwise bring it back
  ;; on the return.
  (fn open-words [state context]
    [[:effect/save
      (shown state
             context
             {:page/current  :page/words
              :words/editing nil
              :words/limit   page-size
              :words/search  ""})]]))


(nxr/register-action! :action/open-word-edit
  (fn open-word-edit [_ word]
    [[:effect/save {:words/editing word}]]))


(nxr/register-action! :action/close-word-edit
  (fn close-word-edit [_]
    [[:effect/save {:words/editing nil}]]))


(nxr/register-action! :action/search-words
  (fn search-words [_ search]
    [[:effect/enter :action/show-search search]]))


(nxr/register-action! :action/show-search
  ;; A new query starts at the first page, and the list goes back to its top
  ;; with the matching rows, on the keystroke: the rows loaded for the old
  ;; query say nothing about how far down this one the reader has read.
  (fn show-search [state context search]
    [[:effect/save (shown state context {:words/limit page-size :words/search search})]
     [:effect/scroll-words-to-top]]))


(nxr/register-action! :action/show-more-words
  (fn show-more-words [state]
    (when (:words/more? state)
      [[:effect/enter :action/show-page (+ (:words/limit state) page-size)]])))


(nxr/register-action! :action/show-page
  (fn show-page [state context limit]
    [[:effect/save (shown state context {:words/limit limit})]]))


;; An edit or a removal changes memory once PouchDB accepts it, and the rows
;; follow memory at the row count already on screen, so neither throws the
;; reader back to the first page.
(nxr/register-action! :action/save-word
  (fn save-word [_ {:keys [id translation]}]
    ;; Saving closes the dialog; rows arriving for any other reason do not
    ;; (#439).
    [[:effect/save {:words/editing nil}]
     [:effect/update-word {:id id :translation translation}]]))


(nxr/register-action! :action/remove-word
  (fn remove-word [_ word]
    [[:effect/delete-word word]]))

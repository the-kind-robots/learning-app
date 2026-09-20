(ns pages.words.actions
  (:require
   [nexus.registry :as nxr]
   [pages.words.presenter :as presenter]))


(nxr/register-action! :action/show-words
  (fn show-words [_ words]
    [[:effect/save
      (merge {:page/current  :page/words
              :page/load     [:effect/load-words]
              :words/editing nil}
             (presenter/page-state words))]]))


(nxr/register-action! :action/open-word-edit
  (fn open-word-edit [_ word]
    [[:effect/save {:words/editing word}]]))


(nxr/register-action! :action/close-word-edit
  (fn close-word-edit [_]
    [[:effect/save {:words/editing nil}]]))


(nxr/register-action! :action/search-words
  ;; A new query starts at the first page: the rows loaded for the old one say
  ;; nothing about how far down this one the reader has read. The list goes
  ;; back to the top with them — results are read from the first one, and a
  ;; reader left at the bottom would be sitting on the sentinel, which would
  ;; ask for the second page before the first was read.
  (fn search-words [_ search]
    [[:effect/scroll-words-to-top]
     [:effect/set-words-search {:search search :limit presenter/page-size}]]))


(nxr/register-action! :action/show-more-words
  (fn show-more-words [state]
    (when (:words/more? state)
      [[:effect/load-more-words
        {:search (:words/search state)
         :limit  (presenter/next-limit (:words/limit state))}]])))


;; A mutation reloads the list at the row count already on screen, so saving or
;; removing a word does not throw the reader back to the first page.
(nxr/register-action! :action/save-word
  (fn save-word [state {:keys [id translation]}]
    [[:effect/update-word
      {:id          id
       :translation translation
       :limit       (:words/limit state)
       :search      (:words/search state)}]]))


(nxr/register-action! :action/remove-word
  (fn remove-word [state {:keys [id value]}]
    [[:effect/delete-word
      {:id     id
       :value  value
       :limit  (:words/limit state)
       :search (:words/search state)}]]))

(ns pages.words.actions
  (:require
   [nexus.registry :as nxr]
   [pages.words.presenter :as presenter]))


(nxr/register-action! :action/show-words
  (fn show-words [_ {:keys [words total search]}]
    [[:effect/save
      {:app/page      :page/words
       :words/items   (presenter/word-list-props words)
       :words/total   total
       :words/search  search
       :words/editing nil}]]))


(nxr/register-action! :action/open-word-edit
  (fn open-word-edit [_ word]
    [[:effect/save {:words/editing word}]]))


(nxr/register-action! :action/close-word-edit
  (fn close-word-edit [_]
    [[:effect/save {:words/editing nil}]]))


(nxr/register-action! :action/search-words
  (fn search-words [_ search]
    [[:effect/set-words-search search]]))


(nxr/register-action! :action/save-word
  (fn save-word [state {:keys [id translation]}]
    [[:effect/update-word {:id id :translation translation :search (:words/search state)}]]))


(nxr/register-action! :action/remove-word
  (fn remove-word [state {:keys [id value]}]
    [[:effect/delete-word {:id id :value value :search (:words/search state)}]]))

(ns pages.words.actions
  (:require
   [nexus.registry :as nxr]
   [pages.words.presenter :as presenter]))


(defn words-shown
  "State for a page of words that has just been read. `:page/load` carries the
   query these rows came from, so the reload a sync pull triggers
   (`:action/reload-page`) asks for the page the reader has rather than the
   first one — otherwise a reader 500 rows down loses 450 of them to a pull
   that happened to bring a document. It names the action rather than the
   effect: the effect takes the number of the read it is answering, and a
   vector stored here would hand a pull the number of the read that produced
   these rows.

   `:words/editing` is left alone. Rows arrive on their own now — the sentinel
   observer asks for them — and clearing it here shut an open dialog under the
   reader, one being typed into included (#439)."
  [{:keys [limit search] :as words}]
  (merge {:page/current :page/words
          :page/load    [:action/load-words {:limit limit :search search}]}
         (presenter/page-state words)))


(nxr/register-action! :action/load-words
  ;; Every read of the list is numbered, and the number comes from the state,
  ;; so every caller reaches the effect through an action that stamps it. This
  ;; one is the screen's own: route entry, and the reload a pull re-dispatches.
  (fn load-words [state opts]
    (let [token (presenter/next-read-token state)]
      [[:effect/save {:words/read-token token}]
       [:effect/load-words opts token]])))


(nxr/register-action! :action/show-words
  ;; Rows read under a different query replace the ones the reader was
  ;; reading, and that is the moment the list goes back to its first row.
  (fn show-words [state words]
    (when (presenter/current-read? state words)
      (cond-> [[:effect/save (words-shown words)]]
        (presenter/new-query? state words)
        (conj [:effect/scroll-words-to-top])))))


(nxr/register-action! :action/open-word-edit
  (fn open-word-edit [_ word]
    [[:effect/save {:words/editing word}]]))


(nxr/register-action! :action/close-word-edit
  (fn close-word-edit [_]
    [[:effect/save {:words/editing nil}]]))


(nxr/register-action! :action/search-words
  ;; A new query starts at the first page: the rows loaded for the old one say
  ;; nothing about how far down this one the reader has read. The list goes
  ;; back to the top with them, on `:action/show-words` — the rows are 400 ms
  ;; away and scrolling here moved a reader who was still reading the old ones,
  ;; then left them free to scroll back down onto the sentinel before the
  ;; shorter list arrived (#439).
  ;;
  ;; The read is numbered on the keystroke, not when the debounce runs out:
  ;; from here on this query is what the list is going to hold. The number is
  ;; kept under `:words/pending-search` as well, which is how reaching the end
  ;; in the meantime knows to stand down.
  (fn search-words [state search]
    (let [token (presenter/next-read-token state)]
      [[:effect/save {:words/pending-search token :words/read-token token}]
       [:effect/set-words-search {:limit presenter/page-size :search search} token]])))


(nxr/register-action! :action/words-search-settled
  ;; The search has answered, or failed. It stops holding the next page back,
  ;; unless a newer keystroke has already taken its place.
  (fn words-search-settled [state token]
    (when (= token (:words/pending-search state))
      [[:effect/save {:words/pending-search nil}]])))


(nxr/register-action! :action/show-more-words
  (fn show-more-words [state]
    (when (presenter/more-to-read? state)
      (let [token (presenter/next-read-token state)]
        [[:effect/save {:words/read-token token}]
         [:effect/load-more-words
          {:limit  (presenter/next-limit (:words/limit state))
           :search (:words/search state)}
          token]]))))


;; A mutation reloads the list at the row count already on screen, so saving or
;; removing a word does not throw the reader back to the first page.
(nxr/register-action! :action/save-word
  (fn save-word [state {:keys [id translation]}]
    ;; Saving closes the dialog. It used to close on the rows the save brought
    ;; back, which also shut it on rows nobody asked for (#439).
    (let [token (presenter/next-read-token state)]
      [[:effect/save {:words/editing nil :words/read-token token}]
       [:effect/update-word
        {:id          id
         :translation translation
         :limit       (:words/limit state)
         :search      (:words/search state)}
        token]])))


(nxr/register-action! :action/remove-word
  (fn remove-word [state {:keys [id value]}]
    (let [token (presenter/next-read-token state)]
      [[:effect/save {:words/read-token token}]
       [:effect/delete-word
        {:id     id
         :value  value
         :limit  (:words/limit state)
         :search (:words/search state)}
        token]])))

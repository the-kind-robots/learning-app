(ns pages.words.actions
  (:require
   [nexus.registry :as nxr]
   [pages.words.presenter :as presenter]))


(defn- start-read
  "Starting a read cancels the one before it. A read cannot be stopped, so
   cancelling means its result is ignored when it arrives.

   Returns the new read and the state that makes it `:words/current-read`."
  []
  (let [read (random-uuid)]
    [read {:words/current-read read}]))


(defn- current-read?
  "Whether `read` is the one running, not one cancelled since."
  [state read]
  (= read (:words/current-read state)))


(defn- more-to-read?
  "Whether reaching the end asks for another page: there are more rows, and no
   page read while a read is running."
  [state]
  (boolean (and (:words/more? state)
                (nil? (:words/current-read state)))))


(defn- new-query?
  "Whether arriving rows answer a different query than the rows on screen.
   They do when the reader's typing has been read: these rows replace what was
   being read rather than extending it, and that is when the list goes back to
   its first row — not on the keystroke 400 ms earlier, with the old rows still
   under a reader free to scroll them."
  [state {:keys [search]}]
  (not= (or search "") (or (:words/search state) "")))


(defn words-shown
  "State for a page of words that has just been read: its rows, and no read
   running any more.

   `:words/editing` is left alone. Rows arrive on their own now — the sentinel
   observer asks for them — and clearing it here shut an open dialog under the
   reader, one being typed into included (#439)."
  [words]
  (assoc (presenter/page-state words) :words/current-read nil))


(def ^:private opened
  "The screen as the route enters it: no rows, no query, no dialog — nothing
   of the last visit — until the first read lands. `:words/items` is nil, not
   empty: no read has answered yet. The dialog matters on its own: rows no
   longer close it (#439), so a word left open would come back."
  {:words/current-read nil
   :words/editing      nil
   :words/empty-state  nil
   :words/has-words?   false
   :words/items        nil
   :words/limit        presenter/page-size
   :words/more?        false
   :words/search       ""})


(defn reload
  "The read a sync pull re-runs: the rows the reader has, under the query they
   were read for — otherwise a reader 500 rows down loses 450 of them to a
   pull that happened to bring a document. The action rather than the effect:
   the pull's read starts when it happens."
  [state]
  [:action/load-words {:limit (:words/limit state) :search (:words/search state)}])


(nxr/register-action! :action/open-words
  (fn open-words [_]
    [[:effect/save opened]]))


(nxr/register-action! :action/reload-words
  (fn reload-words [state]
    [(reload state)]))


(nxr/register-action! :action/load-words
  ;; Every read of the list starts in an action, so every caller reaches the
  ;; effect with one. This is the screen's own read: route entry, and the
  ;; reload a pull re-dispatches.
  (fn load-words [_ opts]
    (let [[read started] (start-read)]
      [[:effect/save started]
       [:effect/load-words opts read]])))


(nxr/register-action! :action/show-words
  ;; Rows read under a different query replace the ones the reader was
  ;; reading, and that is the moment the list goes back to its first row.
  (fn show-words [state {:keys [read] :as words}]
    (when (current-read? state read)
      (cond-> [[:effect/save (words-shown words)]]
        (new-query? state words)
        (conj [:effect/scroll-words-to-top])))))


(nxr/register-action! :action/words-read-ended
  ;; A read that brought no rows: it failed, or the reader declined the
  ;; removal it was for. If it is still the current read, none is running now.
  (fn words-read-ended [state read]
    (when (current-read? state read)
      [[:effect/save {:words/current-read nil}]])))


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
  ;; The read starts on the keystroke, not when the debounce runs out: from
  ;; here on this query is what the list is going to hold. `:words/search`
  ;; changes only when its rows arrive.
  (fn search-words [_ search]
    (let [[read started] (start-read)]
      [[:effect/save started]
       [:effect/set-words-search {:limit presenter/page-size :search search} read]])))


(nxr/register-action! :action/show-more-words
  (fn show-more-words [state]
    (when (more-to-read? state)
      (let [[read started] (start-read)]
        [[:effect/save started]
         [:effect/load-words
          {:limit  (presenter/next-limit (:words/limit state))
           :search (:words/search state)}
          read]]))))


;; A mutation reloads the list at the row count already on screen, so saving or
;; removing a word does not throw the reader back to the first page.
(nxr/register-action! :action/save-word
  (fn save-word [state {:keys [id translation]}]
    ;; Saving closes the dialog. It used to close on the rows the save brought
    ;; back, which also shut it on rows nobody asked for (#439).
    (let [[read started] (start-read)]
      [[:effect/save (assoc started :words/editing nil)]
       [:effect/update-word
        {:id          id
         :translation translation
         :limit       (:words/limit state)
         :search      (:words/search state)}
        read]])))


(nxr/register-action! :action/remove-word
  (fn remove-word [state {:keys [id value]}]
    (let [[read started] (start-read)]
      [[:effect/save started]
       [:effect/delete-word
        {:id     id
         :value  value
         :limit  (:words/limit state)
         :search (:words/search state)}
        read]])))

(ns pages.collections.actions
  (:require
   [nexus.registry :as nxr]
   [use-cases.collections :as collections]))


(defn content
  "What the themes screen shows of the learner's data."
  [_ {:keys [active-collection learner]}]
  (let [{:keys [items total-words]} (collections/summary learner)]
    {:collections/active-id   (:id active-collection)
     :collections/items       items
     :collections/total-words total-words}))


(nxr/register-action! :action/open-collections
  ;; The screen and its tiles in one write, in the task of the tap; the traces of the last visit go.
  (fn open-collections [state context]
    [[:effect/save
      (merge {:page/current        :page/collections
              :collections/deleted []
              :collections/editing-id nil}
             (content state context))]]))


(nxr/register-action! :action/show-collections
  ;; The tiles computed again from memory: after the reader's own change.
  (fn show-collections [state context]
    [[:effect/save (content state context)]]))


(nxr/register-action! :action/show-deleted
  ;; The tiles from memory once it took the deletion, with the stored pointer as it is now (deleting the
  ;; active collection clears it), and the deleted collection's trace in its place; focus goes to the
  ;; trace's «Вернуть».
  (fn show-deleted [state context {:keys [deleted deleted-message status-region]}]
    [[:effect/save
      (assoc (content state context)
             :collections/deleted    (conj (:collections/deleted state) deleted)
             :collections/editing-id nil)]
     [:effect/focus-collection (:id deleted)]
     [:effect/announce status-region deleted-message]]))


(nxr/register-action! :action/restore-collection
  ;; The collection as it was deleted on this visit, written back.
  (fn restore-collection [state {:keys [id restored-message status-region]}]
    [[:effect/restore-collection
      {:collection       (some #(when (= id (:id %)) %) (:collections/deleted state))
       :restored-message restored-message
       :status-region    status-region}]]))


(nxr/register-action! :action/show-restored
  ;; The tiles from memory once it took the collection back, its trace gone; focus goes to its target.
  (fn show-restored [state context {:keys [id restored-message status-region]}]
    [[:effect/save
      (assoc (content state context)
             :collections/deleted
             (filterv #(not= id (:id %)) (:collections/deleted state)))]
     [:effect/focus-collection id]
     [:effect/announce status-region restored-message]]))


(nxr/register-action! :action/exit-editing-on-background
  ;; A click on the background itself, not on a tile in it, ends the editing.
  (fn exit-editing-on-background [state self-click?]
    (when (and self-click? (:collections/editing-id state))
      [[:effect/save {:collections/editing-id nil}]])))


(nxr/register-action! :action/handle-tab-click
  (fn handle-tab-click [state coll-id]
    (if (:collections/editing-id state)
      [[:effect/save {:collections/editing-id nil}]]
      [[:effect/switch-active-collection coll-id]])))


(nxr/register-action! :action/handle-main-tab-click
  (fn handle-main-tab-click [state _]
    (if (:collections/editing-id state)
      [[:effect/save {:collections/editing-id nil}]]
      [[:effect/switch-active-collection]])))

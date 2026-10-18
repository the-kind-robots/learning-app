(ns pages.collections.actions
  (:require
   [nexus.registry :as nxr]
   [use-cases.collections :as collections]))


(defn content
  "What the themes screen shows of the learner's data in `state`."
  [state {:keys [active-collection]}]
  (let [{:keys [items total-words]} (collections/summary (:learner/memory state))]
    {:collections/active-id   (:id active-collection)
     :collections/items       items
     :collections/total-words total-words}))


(nxr/register-action! :action/open-collections
  ;; The screen and its tiles in one write, in the task of the tap.
  (fn open-collections [state context]
    [[:effect/save
      (merge {:page/current :page/collections
              :collections/editing-id nil}
             (content state context))]]))


(nxr/register-action! :action/show-collections
  ;; The tiles computed again from memory: after the reader's own change.
  (fn show-collections [state context]
    [[:effect/save (content state context)]]))


(nxr/register-action! :action/show-deleted
  ;; The tiles without the deleted collection, from memory once it took the deletion, with the stored
  ;; pointer as it is now (deleting the active collection clears it); focus and the status line go to
  ;; the neighbour picked before the delete.
  (fn show-deleted [state context {:keys [deleted-message focus-id status-region]}]
    [[:effect/save (assoc (content state context) :collections/editing-id nil)]
     [:effect/focus-collection focus-id]
     [:effect/announce status-region deleted-message]]))


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

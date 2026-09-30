(ns pages.collections.actions
  (:require
   [nexus.registry :as nxr]
   [use-cases.collections :as collections]))


(defn content
  "What the themes screen shows of the learner's data in `state`."
  [state {:keys [active-id]}]
  (let [{:keys [items total-words]} (collections/summary (:learner/memory state))]
    {:collections/active-id   active-id
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


(defn deleted-message
  "What the status line says once a collection is deleted, by the name its
   target showed."
  [name]
  (str "Набор «" name "» удалён"))


(nxr/register-action! :action/show-deleted
  ;; The tiles without the deleted collection, computed from memory once it
  ;; took the deletion and with the stored pointer as it is now — deleting the
  ;; active collection clears it; focus on the neighbour picked before, and
  ;; the status line naming it.
  (fn show-deleted [state context {:keys [name focus-id]}]
    [[:effect/save (assoc (content state context) :collections/editing-id nil)]
     [:effect/focus-collection focus-id]
     [:effect/announce "app-status" (deleted-message name)]]))


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

(ns pages.collections.actions
  (:require
   [nexus.registry :as nxr]
   [use-cases.collections :as collections]))


(defn- unchanged-or
  "The current value when the new one equals it, so a change to memory that
   left the collections as they were saves nothing new and the screen does
   not render again."
  [current value]
  (if (= current value) current value))


(defn content
  "What the themes screen shows of the learner's data in `state`. Until
   memory is ready it is loading; the tiles already on screen stay."
  [state {:keys [active-id]}]
  (if-not (:learner/ready? state)
    {:collections/loading? true}
    (let [{:keys [items total-words]} (collections/summary (:learner/memory state) active-id)]
      {:collections/active-id   active-id
       :collections/items       (unchanged-or (:collections/items state) items)
       :collections/loading?    false
       :collections/total-words total-words})))


(nxr/register-action! :action/open-collections
  ;; The screen and its tiles in one write, in the task of the tap.
  (fn open-collections [state context]
    [[:effect/save
      (merge {:page/current :page/collections
              :collections/editing-id nil}
             (content state context))]]))


(defn deleted-message
  "What the status line says once a collection is deleted, by the name its
   target showed."
  [name]
  (str "Набор «" name "» удалён"))


(nxr/register-action! :action/show-deleted
  ;; The tiles follow memory, which took the deletion when PouchDB did; this
  ;; focuses the neighbour picked before it and names it on the status line.
  ;; Recomputed with the stored pointer as it is now: deleting the active
  ;; collection cleared it after memory had taken the deletion.
  (fn show-deleted [state context {:keys [name focus-id]}]
    [[:effect/save (assoc (content state context) :collections/editing-id nil)]
     [:effect/focus-collection focus-id]
     [:effect/announce (deleted-message name)]]))


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

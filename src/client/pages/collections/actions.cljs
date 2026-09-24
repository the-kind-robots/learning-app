(ns pages.collections.actions
  (:require
   [nexus.registry :as nxr]))


(def ^:private shown
  "What every save of the loaded page carries, as one value: the render
   watch skips a save whose every value is identical to the current one,
   and a literal built per call — a fresh vector, a fresh keyword object in
   a development build — would defeat that on every reload."
  {:page/current :page/collections
   :page/load [:effect/load-collections]
   :collections/editing-id nil
   :collections/loading? false})


(nxr/register-action! :action/open-collections
  (fn open-collections [_]
    ;; The screen switches before its data is read, so the page answers the
    ;; tap at once with a loading state. The items are left as they are: the
    ;; flag alone says the screen is loading, and only this action sets it,
    ;; so a reload of the same screen (after a sync pull) keeps the current
    ;; cards on screen until the new ones arrive.
    [[:effect/save
      {:page/current (:page/current shown)
       :page/load    (:page/load shown)
       :collections/loading? true
       :collections/deleted-name nil}]]))


(defn- unchanged-or
  "The current value when the new one equals it, so a reload that brought
   the same data saves nothing new and the screen does not render again."
  [current value]
  (if (= current value) current value))


(defn collections-shown
  "The state to save once the collections are read. A reload that brought
   the same data leaves every value identical to `state`'s, so the merge
   returns the same map and nothing renders."
  [state {:keys [active-id items total-words]}]
  (assoc shown
         :collections/active-id   active-id
         :collections/items       (unchanged-or (:collections/items state) items)
         :collections/total-words total-words))


(nxr/register-action! :action/show-collections
  (fn show-collections [state summary]
    [[:effect/save (collections-shown state summary)]]))


(nxr/register-action! :action/show-deleted
  ;; The screen without the deleted collection, the status line naming it,
  ;; and focus on the neighbour picked before the delete.
  (fn show-deleted [state summary {:keys [name focus-id]}]
    [[:effect/save (assoc (collections-shown state summary)
                          :collections/deleted-name name)]
     [:effect/focus-collection focus-id]]))


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

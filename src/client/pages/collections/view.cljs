(ns pages.collections.view
  (:require
   [pages.collections.presenter :as presenter]))


(defn- close-icon
  []
  [:svg.tile__close-icon
   {:viewBox "0 0 12 12" :aria-hidden "true" :fill "none"}
   [:path
    {:d "M2 2 L10 10 M10 2 L2 10"
     :stroke "currentColor"
     :stroke-width 2.75
     :stroke-linecap "round"}]])


(defn- close-button
  "The delete control, revealed by the editing state."
  [{:keys [id editing? delete-label]}]
  [:button.tile__close
   {:type        "button"
    :aria-label  delete-label
    :aria-hidden (when-not editing? "true")
    :tabindex    (when-not editing? "-1")
    :on          {:pointerdown [[:effect/stop-propagation]]
                  :click       [[:effect/stop-propagation]
                                [:effect/delete-collection {:id id}]]}}
   (close-icon)])


(defn- target-attrs
  "What every tappable thing carries: its id for the gesture tracking, its
   tap, and a long press where there is something to delete. It goes on a
   `<button>` every time, so nothing claims a role it cannot carry and a
   keyboard reaches every target."
  [{:keys [id tap deletable?]}]
  {:data-collection-id id
   :type "button"
   :on   {:click       tap
          :pointerdown (if deletable?
                         [[:effect/begin-long-press id tap]]
                         [[:effect/begin-tap id tap]])}})


(defn- name-and-count
  [{:keys [name count]}]
  (list
   [:span.tile__name name]
   [:span.tile__count count]))


(defn- plain-tile
  "The box carries the colour and the states; the button inside it fills the
   box and takes the tap. The close button is that button's sibling, because
   a button holds no button."
  [{:keys [key active? editing? deletable?] :as tile}]
  [:div.tile
   {:replicant/key key
    :class [(when active? "tile--active")
            (when editing? "tile--editing")]}
   (when deletable? (close-button tile))
   [:button.tile__target (target-attrs tile)
    (name-and-count tile)]])


(defn- folder-row
  "A list item holding the row's own button. The `<ul>` keeps its items and
   the tap target is an element that already is one."
  [{:keys [id active? editing?] :as row}]
  [:li.tile__row
   {:replicant/key id
    :class [(when active? "tile__row--active")
            (when editing? "tile__row--editing")]}
   (close-button row)
   [:button.tile__row-target (target-attrs row)
    [:span.tile__row-name (:name row)]
    [:span.tile__count (:count row)]]])


(defn- folder-head
  "The parent collection when it has a document; a plain label with the
   count otherwise. Tappable means a button, so a label stays a heading and
   a target never is one."
  [{:keys [tappable? name count] :as head}]
  (if tappable?
    [:div.tile__head
     {:class [(when (:active? head) "tile__head--active")
              (when (:editing? head) "tile__head--editing")]}
     (when (:deletable? head) (close-button head))
     [:button.tile__head-target (target-attrs head)
      (name-and-count head)]]
    [:h2.tile__label
     [:span.tile__label-text name]
     [:span.tile__count count]]))


(defn- folder-tile
  [{:keys [key head rows]}]
  [:div.tile.tile--folder
   {:replicant/key key}
   (folder-head head)
   [:ul.tile__rows
    (for [row rows] (folder-row row))]])


(defn- add-button
  []
  [:button.switcher__add
   {:type       "button"
    :aria-label "Новый набор"
    :on         {:click [[:effect/stop-propagation]
                         [:effect/save {:collections/editing-id nil}]
                         [:effect/prompt-create-collection]]}}
   [:svg {:viewBox "0 0 40 40" :width 28 :height 28 :aria-hidden "true"}
    [:path
     {:d "M20 8 V32 M8 20 H32"
      :stroke "currentColor"
      :stroke-width 4
      :stroke-linecap "round"}]]])


(defn page
  [state]
  (let [{:keys [loading? tiles]} (presenter/page-props state)]
    [:div.switcher
     {:on {:click [[:effect/exit-editing-on-background]]}}
     [:h1.switcher__title "Наборы"]
     (if loading?
       [:div.switcher__loading {:role "status" :aria-live "polite"}
        [:p.switcher__loading-text "Загружаем…"]]
       [:div.masonry
        {:on {:click [[:effect/exit-editing-on-background]]}}
        (for [tile tiles]
          (if (:folder? tile)
            (folder-tile tile)
            (plain-tile tile)))])
     (add-button)]))

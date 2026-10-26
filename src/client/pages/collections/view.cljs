(ns pages.collections.view
  (:require
   [application.shell :as shell]
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
  "The delete control. It follows its target in the DOM, so the keyboard reaches it right after that target,
   and is always in the Tab order; the CSS shows it while editing or when focus is on the target or itself."
  [{:keys [id name]}]
  [:button.tile__close
   {:type       "button"
    :aria-label [:i18n :collections/delete {:name name}]
    :on         {:pointerdown [[:effect/stop-propagation]]
                 :click       [[:effect/stop-propagation]
                               [:effect/delete-collection
                                {:confirm-message [:i18n :collections/delete-confirm {:name name}]
                                 :deleted-message [:i18n :collections/deleted {:name name}]
                                 :id id
                                 :status-region shell/status-region-id}]]}}
   (close-icon)])


(defn- trace
  "What stays of a collection deleted on this visit: one button that brings it back, its name struck through
   and an undo mark where its count stood. The mark is as wide as the count, so the name wraps as it did and
   nothing around it moves."
  [{:keys [id name lang]} class]
  [:button.tile__restore
   {:class      class
    :type       "button"
    :aria-label [:i18n :collections/restore {:name name}]
    :data-restore-id id
    :on         {:click [[:effect/stop-propagation]
                         [:action/restore-collection
                          {:id id
                           :restored-message [:i18n :collections/restored {:name name}]
                           :status-region shell/status-region-id}]]}}
   [:s.tile__gone {:lang lang} name]
   [:svg.tile__restore-icon
    {:viewBox "0 0 20 20" :aria-hidden "true" :fill "none"}
    [:path
     {:d "M7 4 L3 8 L7 12 M3 8 H12 A5 5 0 0 1 12 18 H9"
      :stroke "currentColor"
      :stroke-width 2.25
      :stroke-linecap "round"
      :stroke-linejoin "round"}]]])


(defn- target-attrs
  "What every tappable thing carries: its id for gesture tracking, its tap, a long press where there is
   something to delete, `aria-current` on the active one. Always a `<button>`, so a keyboard reaches every
   target and nothing claims a role it cannot carry."
  [{:keys [id tap deletable? active?]}]
  {:aria-current (when active? "true")
   :data-collection-id id
   :on {:click       tap
        :pointerdown (if deletable?
                       [[:effect/begin-long-press id tap]]
                       [[:effect/begin-tap id tap]])}
   :type "button"})


(defn- name-and-count
  [{:keys [name lang count main?]}]
  (list
   [:span.tile__name {:lang lang} (if main? [:i18n :collections/all] name)]
   [:span.tile__count count]))


(defn- accent-class
  "The class of the colour a tile has."
  [accent]
  (case accent
    :accent/owl      "tile--owl"
    :accent/macaw    "tile--macaw"
    :accent/fox      "tile--fox"
    :accent/starfish "tile--starfish"
    :accent/beetle   "tile--beetle"
    :accent/bee      "tile--bee"
    :accent/cardinal "tile--cardinal"
    :accent/peacock  "tile--peacock"))


(defn- plain-tile
  "The box carries the colour and the states; the button inside it fills the
   box and takes the tap. The close button is that button's sibling, because
   a button holds no button."
  [{:keys [accent key active? editing? deletable? trace?] :as tile}]
  [:div.tile
   {:replicant/key key
    :class [(accent-class accent)
            (when active? "tile--active")
            (when editing? "tile--editing")
            (when trace? "tile--trace")]}
   (if trace?
     (trace tile "tile__trace")
     [:button.tile__target (target-attrs tile)
      (name-and-count tile)])
   (when deletable? (close-button tile))])


(defn- folder-row
  "A list item holding the row's own button. The `<ul>` keeps its items and
   the tap target is an element that already is one."
  [{:keys [id active? editing? trace?] :as row}]
  [:li.tile__row
   {:replicant/key id
    :class [(when active? "tile__row--active")
            (when editing? "tile__row--editing")]}
   (if trace?
     (trace row "tile__row-trace")
     (list
      [:button.tile__row-target (target-attrs row)
       [:span.tile__row-name {:lang (:lang row)} (:name row)]
       [:span.tile__count (:count row)]]
      (close-button row)))])


(defn- folder-head
  "The parent collection when it has a document, its trace when it was
   deleted on this visit; a plain label with the count otherwise. Tappable
   means a button, so a label stays a heading and a target never is one."
  [{:keys [tappable? trace? name lang count] :as head}]
  (cond
    trace?
    [:div.tile__head (trace head "tile__head-trace")]

    tappable?
    [:div.tile__head
     {:class [(when (:active? head) "tile__head--active")
              (when (:editing? head) "tile__head--editing")]}
     [:button.tile__head-target (target-attrs head)
      (name-and-count head)]
     (when (:deletable? head) (close-button head))]

    :else
    [:h2.tile__label
     [:span.tile__label-text {:lang lang} name]
     [:span.tile__count count]]))


(defn- folder-tile
  [{:keys [accent key head rows]}]
  [:div.tile.tile--folder
   {:replicant/key key
    :class (accent-class accent)}
   (folder-head head)
   [:ul.tile__rows
    (for [row rows] (folder-row row))]])


(defn- add-button
  []
  [:button.switcher__add
   {:type       "button"
    :aria-label [:i18n :collections/new]
    :on         {:click [[:effect/stop-propagation]
                         [:effect/save {:collections/editing-id nil}]
                         [:effect/prompt-create-collection [:i18n :collections/new-name]]]}}
   [:svg {:viewBox "0 0 40 40" :width 28 :height 28 :aria-hidden "true"}
    [:path
     {:d "M20 8 V32 M8 20 H32"
      :stroke "currentColor"
      :stroke-width 4
      :stroke-linecap "round"}]]])


(defn page
  [state]
  (let [{:keys [tiles]} (presenter/page-props state)]
    ;; Keyed: a screen's root stands where the last screen's `div` stood, and without a key Replicant
    ;; reuses that node and mounts nothing.
    [:div.switcher
     {:on {:click [[:action/exit-editing-on-background [:event/self-click?]]]}
      :replicant/key :page/collections
      :replicant/on-mount [[:effect/listen-for-typing]]
      :replicant/on-unmount [[:effect/stop-listening-for-typing]]}
     [:h1.switcher__title.visually-hidden [:i18n :collections/title]]
     [:div.masonry
      {:on {:click [[:action/exit-editing-on-background [:event/self-click?]]]}}
      (for [tile tiles]
        (if (:folder? tile)
          (folder-tile tile)
          (plain-tile tile)))]
     (add-button)]))

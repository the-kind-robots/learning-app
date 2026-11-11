(ns pages.collections.view
  (:require
   [application.pages :as pages]
   [application.presenter :as application]
   [application.shell :as shell]
   [pages.collections.presenter :as presenter]
   [replicant.alias :as alias]))


(alias/defalias close-icon
  []
  [:svg.tile__close-icon
   {:viewBox     "0 0 12 12"
    :aria-hidden "true"
    :fill        "none"}
   [:path
    {:d "M2 2 L10 10 M10 2 L2 10"
     :stroke "currentColor"
     :stroke-width 2.75
     :stroke-linecap "round"}]])


(alias/defalias close-button
  "The delete control. It follows its target in the DOM, so the keyboard reaches it right after that target,
   and is always in the Tab order; the CSS shows it while editing or when focus is on the target or itself."
  [{:close-button/keys [id name]}]
  [:button.tile__close
   {:type       "button"
    :aria-label [:i18n :collections/delete {:name name}]
    :on         {:pointerdown [[:effect/stop-propagation]]
                 :click       [[:effect/stop-propagation]
                               [:effect/delete-collection
                                {:deleted-message [:i18n :collections/deleted {:name name}]
                                 :id id
                                 :status-region shell/status-region-id}]]}}
   [close-icon]])


(defn- target-attrs
  "What every tappable thing carries: its id for gesture tracking, its tap, a long press where there is
   something to delete, `aria-current` on the active one. Always a `<button>`, so a keyboard reaches every
   target and nothing claims a role it cannot carry."
  [{:target/keys [id tap deletable? active?]}]
  {:aria-current (when active? "true")
   :data-collection-id id
   :on {:click       tap
        :pointerdown (if deletable?
                       [[:effect/begin-long-press id tap]]
                       [[:effect/begin-tap id tap]])}
   :type "button"})


(defn- name-and-count
  [{:target/keys [name lang count main?]}]
  (list
   [:span.tile__name
    {:lang lang}
    (if main?
      [:i18n :collections/all]
      name)]
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


(alias/defalias folder-row
  "A list item holding the row's own button. The `<ul>` keeps its items and
   the tap target is an element that already is one."
  [{:target/keys [id active? editing? name lang count] :as row}]
  [:li.tile__row
   {:class [(when active? "tile__row--active")
            (when editing? "tile__row--editing")]}
   [:button.tile__row-target
    (target-attrs row)
    [:span.tile__row-name
     {:lang lang}
     name]
    [:span.tile__count count]]
   [close-button
    {:close-button/id   id
     :close-button/name name}]])


(alias/defalias folder-head
  "The parent collection when it has a document; a plain label with the
   count otherwise. Tappable means a button, so a label stays a heading and
   a target never is one."
  [{:folder-head/keys [tappable?]
    :target/keys [active? count deletable? editing? id lang name]
    :as head}]
  (if tappable?
    [:div.tile__head
     {:class [(when active? "tile__head--active")
              (when editing? "tile__head--editing")]}
     [:button.tile__head-target
      (target-attrs head)
      (name-and-count head)]
     (when deletable?
       [close-button
        {:close-button/id   id
         :close-button/name name}])]
    [:h2.tile__label
     [:span.tile__label-text
      {:lang lang}
      name]
     [:span.tile__count count]]))


(alias/defalias tile
  "A theme's tile. A plain one: the box carries the colour and the states, the button inside it fills the box and
   takes the tap, and the close button is that button's sibling, because a button holds no button. A folder: its
   head over its children's rows. One alias for both, so a tile that becomes a folder stays the same element."
  [{:tile/keys   [accent armed? folder? head rows sort-key]
    :target/keys [active? deletable? editing? id name]
    :as          tile}]
  (if folder?
    [:div.tile.tile--folder
     {:sort-key sort-key
      :armed    (when armed? "")
      :class    (accent-class accent)}
     [folder-head head]
     [:ul.tile__rows
      (for [row rows]
        [folder-row (assoc row :replicant/key (:target/id row))])]]
    [:div.tile
     {:sort-key sort-key
      :armed    (when armed? "")
      :class    [(accent-class accent)
                 (when active? "tile--active")
                 (when editing? "tile--editing")]}
     [:button.tile__target
      (target-attrs tile)
      (name-and-count tile)]
     (when deletable?
       [close-button
        {:close-button/id   id
         :close-button/name name}])]))


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
     [:h1.switcher__title.visually-hidden
      [:i18n :collections/title]]
     [:arrange-panel.masonry
      {:on {:click   [[:action/exit-editing-on-background [:event/self-click?]]]
            :arrange [[:action/arrange-collections [:event/order]]]}}
      (for [props tiles]
        [tile (assoc props :replicant/key (:tile/key props))])]
     ;; The main button stands where «НАЧАТЬ УРОК» does on home (#773).
     [:footer.switcher__footer.page-footer
      [:div.page-footer__action
       [:button.big-button.green-button
        {:type "button"
         :on   {:click [[:effect/stop-propagation]
                        [:effect/save {:collections/editing-id nil}]
                        [:effect/prompt-create-collection [:i18n :collections/new-name]]]}}
        [:i18n :collections/add]]]]]))


(defn hotkeys
  "The actions of each keystroke on the themes: `Escape` leaves them, `Alt+KeyS` opens the statistics the stars
   show."
  [state]
  (merge {"Escape" [[:action/close-page]]}
         (when (application/stars-button? state)
           {"Alt+KeyS" [[:action/open-page :page/stats]]})))


(defmethod pages/page :page/collections
  [_]
  {:hotkeys hotkeys
   :open    :action/open-collections
   :path    "/collections"
   :refresh :action/show-collections
   :view    page})

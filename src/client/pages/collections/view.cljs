(ns pages.collections.view
  (:require
   [application.shell :as shell]
   [pages.collections.presenter :as presenter]))


(defn- close-icon
  []
  [:svg.close-icon
   {:viewBox     "0 0 12 12"
    :aria-hidden "true"
    :fill        "none"}
   [:path
    {:d "M2 2 L10 10 M10 2 L2 10"
     :stroke "currentColor"
     :stroke-width 2.75
     :stroke-linecap "round"}]])


(defn- close-button
  "The delete control. It follows its target in the DOM, so the keyboard reaches it right after that target,
   and is always in the Tab order; the CSS shows it while editing or when focus is on the target or itself."
  [{:keys [id name]}]
  [:button.close
   {:type       "button"
    :aria-label [:i18n :collections/delete {:name name}]
    :on         {:pointerdown [[:effect/stop-propagation]]
                 :click       [[:effect/stop-propagation]
                               [:effect/delete-collection
                                {:deleted-message [:i18n :collections/deleted {:name name}]
                                 :id id
                                 :status-region shell/status-region-id}]]}}
   (close-icon)])


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
   [:span.name
    {:lang lang}
    (if main? [:i18n :collections/all] name)]
   [:span.count count]))


(defn- plain-tile
  "The box carries the colour and the states; the button inside it fills the
   box and takes the tap. The close button is that button's sibling, because
   a button holds no button."
  [{:keys [accent key sort-key armed? active? editing? deletable?] :as tile}]
  [:div.tile
   {:replicant/key key
    :sort-key      sort-key
    :armed         (when armed? "")
    :data-accent   (name accent)
    :data-active   (when active? "")
    :data-editing  (when editing? "")}
   [:button.target
    (target-attrs tile)
    (name-and-count tile)]
   (when deletable? (close-button tile))])


(defn- folder-row
  "A list item holding the row's own button. The `<ul>` keeps its items and
   the tap target is an element that already is one."
  [{:keys [id active? editing?] :as row}]
  [:li.row
   {:replicant/key id
    :data-active   (when active? "")
    :data-editing  (when editing? "")}
   [:button.row-target
    (target-attrs row)
    [:span.row-name
     {:lang (:lang row)}
     (:name row)]
    [:span.count
     (:count row)]]
   (close-button row)])


(defn- folder-head
  "The parent collection when it has a document; a plain label with the
   count otherwise. Tappable means a button, so a label stays a heading and
   a target never is one."
  [{:keys [tappable? name lang count] :as head}]
  (if tappable?
    [:div.head
     {:data-active  (when (:active? head) "")
      :data-editing (when (:editing? head) "")}
     [:button.head-target
      (target-attrs head)
      (name-and-count head)]
     (when (:deletable? head) (close-button head))]
    [:h2.label
     [:span.label-text
      {:lang lang}
      name]
     [:span.count count]]))


(defn- folder-tile
  [{:keys [accent key sort-key armed? head rows]}]
  [:div.tile
   {:replicant/key key
    :sort-key      sort-key
    :armed         (when armed? "")
    :data-accent   (name accent)
    :data-folder   ""}
   (folder-head head)
   [:ul.rows
    (for [row rows] (folder-row row))]])


(defn- add-button
  []
  [:button.add
   {:type       "button"
    :aria-label [:i18n :collections/new]
    :on         {:click [[:effect/stop-propagation]
                         [:effect/save {:collections/editing-id nil}]
                         [:effect/prompt-create-collection [:i18n :collections/new-name]]]}}
   [:svg
    {:viewBox     "0 0 40 40"
     :width       28
     :height      28
     :aria-hidden "true"}
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
     [:h1.title.visually-hidden
      [:i18n :collections/title]]
     [:arrange-panel.masonry
      {:on {:click   [[:action/exit-editing-on-background [:event/self-click?]]]
            :arrange [[:action/arrange-collections [:event/order]]]}}
      (for [tile tiles]
        (if (:folder? tile)
          (folder-tile tile)
          (plain-tile tile)))]
     (add-button)]))

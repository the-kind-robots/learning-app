(ns pages.collections.view
  (:require
   [pages.collections.presenter :as presenter]
   [utils :as utils]))


(defn- progress-dot
  [retention-level]
  [:span.card-preview__dot
   {:style {:background-color (utils/prozent->color retention-level)}}])


(defn- preview-row
  [{id :_id :keys [retention-level value translation]}]
  [:li.card-preview__row {:replicant/key id}
   (progress-dot retention-level)
   [:span.card-preview__de {:lang "de"} value]
   [:span.card-preview__ru {:lang "ru"} translation]])


(defn- empty-state
  []
  [:div.card-preview__empty
   [:p.card-preview__empty-text "Слов пока нет"]
   [:p.card-preview__empty-hint "Добавьте первое слово"]])


(defn- card-preview
  [{:keys [name preview-words]}]
  (let [display (not-empty name)]
    [:div.card-preview
     [:h2.card-preview__name
      {:class (when-not display "card-preview__name--placeholder")}
      (or display "Без названия")]
     (if (seq preview-words)
       [:ul.card-preview__list
        (for [word preview-words]
          (preview-row word))]
       (empty-state))]))


(defn- main-card
  [{:keys [preview-words]} active-id]
  [:div.tab-card
   {:replicant/key "main"
    :class (when (nil? active-id) "tab-card--active")
    :on    {:click [[:effect/stop-propagation]
                    [:action/handle-main-tab-click]]}}
   (card-preview {:name "Всё подряд" :preview-words preview-words})])


(defn- close-icon
  []
  [:svg.tab-card__close-icon
   {:viewBox "0 0 12 12" :aria-hidden "true" :fill "none"}
   [:path
    {:d "M2 2 L10 10 M10 2 L2 10"
     :stroke "currentColor"
     :stroke-width 2.75
     :stroke-linecap "round"}]])


(defn- tab-card
  [{coll-id :_id coll-name :name :as item} active-id editing-id]
  (let [editing? (= coll-id editing-id)]
    [:div.tab-card
     {:replicant/key coll-id
      :class [(when (= coll-id active-id) "tab-card--active")
              (when editing? "tab-card--editing")]
      :on    {:click       [[:action/handle-tab-click coll-id]]
              :pointerdown [[:effect/begin-long-press coll-id]]}}
     [:button.tab-card__close
      {:type       "button"
       :aria-label (str "Удалить набор «" (or (not-empty coll-name) "Без названия") "»")
       :tabindex   (when-not editing? "-1")
       :on         {:pointerdown [[:effect/stop-propagation]]
                    :click       [[:effect/stop-propagation]
                                  [:effect/delete-collection {:id coll-id}]]}}
      (close-icon)]
     (card-preview item)]))


(defn- new-card
  []
  [:button.tab-card.tab-card--new
   {:type       "button"
    :aria-label "Новый набор"
    :on         {:click [[:effect/stop-propagation]
                         [:effect/save
                          {:collections/editing-id        nil
                           :collections/long-press-fired? false}]
                         [:effect/prompt-create-collection]]}}
   [:svg.tab-card__dash-frame
    {:preserveAspectRatio "none" :viewBox "0 0 90 160" :aria-hidden "true"}
    [:rect
     {:x            1
      :y            1
      :width        88
      :height       158
      :rx           10
      :ry           10
      :fill         "none"
      :stroke       "rgba(60,60,60,0.3)"
      :stroke-width 1.2
      :stroke-dasharray "7 4"
      :vector-effect "non-scaling-stroke"}]]
   [:span.tab-card__plus {:aria-hidden "true"}
    [:svg {:viewBox "0 0 40 40" :width 40 :height 40}
     [:path
      {:d "M20 8 V32 M8 20 H32"
       :stroke "currentColor"
       :stroke-width 3.5
       :stroke-linecap "round"}]]]])


(defn page
  [state]
  (let [{:keys [active-id editing-id main items]} (presenter/page-props state)]
    [:div.switcher
     {:on {:click [[:effect/exit-editing-on-background]]}}
     [:h1.switcher__title "Наборы"]
     [:div.switcher__grid
      {:on {:click [[:effect/exit-editing-on-background]]}}
      (main-card main active-id)
      (for [item items] (tab-card item active-id editing-id))
      (new-card)]]))

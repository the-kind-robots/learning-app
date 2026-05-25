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
  (let [placeholder (when-not name "Без названия")
        display     (or (not-empty name) placeholder)]
    [:div.card-preview
     [:h2.card-preview__name
      {:class (when (not (not-empty name)) "card-preview__name--placeholder")}
      display]
     (if (seq preview-words)
       [:ul.card-preview__list
        (for [word preview-words]
          (preview-row word))]
       (empty-state))]))


(defn- main-card
  [{:keys [preview-words]} active-id]
  [:div.tab-card
   {:replicant/key "main"
    :class (when (nil? active-id) "tab-card--active")}
   (card-preview {:name "Всё подряд" :preview-words preview-words})])


(defn- tab-card
  [{coll-id :_id :as item} active-id]
  [:div.tab-card
   {:replicant/key coll-id
    :class (when (= coll-id active-id) "tab-card--active")}
   (card-preview item)])


(defn page
  [state]
  (let [{:keys [active-id main items]} (presenter/page-props state)]
    [:div.switcher
     [:h1.switcher__title "Наборы"]
     [:div.switcher__grid
      (main-card main active-id)
      (for [item items] (tab-card item active-id))]]))

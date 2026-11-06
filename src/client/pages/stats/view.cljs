(ns pages.stats.view
  (:require
   [application.pages :as pages]
   [pages.stats.presenter :as presenter]))


(defn- words-section
  [{:keys [columns shares words]}]
  [:section.statistics__section
   [:h2.statistics__heading
    [:i18n :stats/words {:count words}]]
   [:div.statistics__bins
    (for [{:keys [color height words]} columns]
      [:div.statistics__bin
       {:style {:height (str height "%")}}
       [:span.statistics__bin-fill {:style {:background-color color}}]
       [:span.statistics__bin-count words]])]
   [:div.statistics__strip
    (for [{:keys [color name words]} shares]
      [:span.statistics__strip-part
       {:replicant/key name
        :style {:background-color color
                :flex-grow        words}}])]
   [:ul.statistics__groups
    (for [{:keys [color name words]} shares]
      [:li.statistics__group
       {:replicant/key name}
       [:span.statistics__group-dot {:style {:background-color color}}]
       [:span.statistics__group-words words]
       [:span.statistics__group-name
        [:i18n name]]])]])


(defn- day-column
  [{:keys [day height id lessons purple? today? weekday weekend?]}]
  [:div.statistics__day
   {:replicant/key id}
   [:div.statistics__day-bar
    [:span.statistics__day-lessons lessons]
    [:span.statistics__day-fill
     {:class (when purple? "statistics__day-fill--purple")
      :style {:height (str height "%")}}]]
   [:span.statistics__day-date
    {:class (when today? "statistics__day-date--today")}
    day]
   [:span.statistics__day-weekday
    {:class (when weekend? "statistics__day-weekday--weekend")}
    weekday]])


(defn- lessons-section
  [{:keys [lessons months]}]
  [:section.statistics__section
   [:h2.statistics__heading
    [:i18n :stats/lessons {:count lessons}]]
   ;; Opened on today, at the right end.
   [:div.statistics__days
    {:replicant/on-mount [[:effect/scroll-to-end]]}
    (for [{:keys [days id name]} months]
      [:div.statistics__month
       {:replicant/key id}
       [:span.statistics__month-name name]
       [:div.statistics__month-days
        (map day-column days)]])]])


(defn page
  [state]
  (let [props (presenter/page-props state)]
    [:div.statistics
     ;; The shell's corner ✕ leaves the screen; the heading stays for assistive technology only.
     [:h1.visually-hidden
      [:i18n :stats/title]]
     [:div.statistics__content
      (words-section props)
      (lessons-section props)]
     [:footer.statistics__footer.page-footer
      [:div.page-footer__action
       [:button.big-button.green-button
        {:on {:click [[:effect/enter :action/share-stats]]}}
        [:i18n :stats/share]]]]]))


(defmethod pages/page :page/stats
  [_]
  {:hotkeys (constantly {"Escape" [[:action/close-page]]})
   :open    :action/show-stats
   :path    "/stats"
   :refresh :action/show-stats
   :view    page})

(ns install-guide.view)


(defn- share-svg
  []
  ;; zprint: as written
  [:svg
   {:width          "22"
    :height         "22"
    :viewBox        "0 0 24 24"
    :fill           "none"
    :stroke         "currentColor"
    :stroke-width   "1.8"
    :stroke-linecap "round"
    :stroke-linejoin "round"}
   [:path {:d "M4 12v6a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-6"}]
   [:polyline {:points "16 6 12 2 8 6"}]
   [:line {:x1 "12" :y1 "2" :x2 "12" :y2 "15"}]])


(defn- add-square-svg
  []
  ;; zprint: as written
  [:svg
   {:width          "22"
    :height         "22"
    :viewBox        "0 0 24 24"
    :fill           "none"
    :stroke         "currentColor"
    :stroke-width   "1.8"
    :stroke-linecap "round"
    :stroke-linejoin "round"}
   [:rect {:x "3" :y "3" :width "18" :height "18" :rx "3" :ry "3"}]
   [:line {:x1 "12" :y1 "8" :x2 "12" :y2 "16"}]
   [:line {:x1 "8" :y1 "12" :x2 "16" :y2 "12"}]])


(defn- checkmark-svg
  []
  [:svg
   {:width          "22"
    :height         "22"
    :viewBox        "0 0 24 24"
    :fill           "none"
    :stroke         "currentColor"
    :stroke-width   "1.8"
    :stroke-linecap "round"
    :stroke-linejoin "round"}
   [:polyline {:points "20 6 9 17 4 12"}]])


(defn render
  [state]
  [:div#app-install-guide.app-install-guide
   {:hidden (not (:pwa/show-guide? state))
    :on     {:click [[:action/dismiss-install-guide-backdrop [:event/self-click?]]]}}
   [:div.card
    [:div.header
     [:h3
      [:i18n :install-guide/title]]
     [:button.close
      {:aria-label [:i18n :install-guide/close]
       :on {:click [[:action/dismiss-install-guide]]}}
      "×"]]
    [:div.steps
     [:div.step
      [:span.step-number "1"]
      [:span.step-icon
       (share-svg)]
      [:span.step-text
       [:i18n :install-guide/share]]]
     [:div.step
      [:span.step-number "2"]
      [:span.step-icon
       (add-square-svg)]
      [:span.step-text
       [:i18n :install-guide/add-to-home]]]
     [:div.step
      [:span.step-number "3"]
      [:span.step-icon
       (checkmark-svg)]
      [:span.step-text
       [:i18n :install-guide/add]]]]]])

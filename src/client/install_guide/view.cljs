(ns install-guide.view)


(defn- share-svg
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
   [:path {:d "M4 12v6a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-6"}]
   [:polyline {:points "16 6 12 2 8 6"}]
   [:line {:x1 "12" :y1 "2" :x2 "12" :y2 "15"}]])


(defn- add-square-svg
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
  (list
   [:button.app-shell__install-button
    {:type       "button"
     :aria-label "Install app"
     ;; disabling Install button, until designing proper UI/UX for installation.
     :hidden     true #_(not (:pwa/install-available? state))
     :on         {:click [[:action/pwa-install-requested]]}}
    "установить"]
   [:div#app-install-guide.app-install-guide
    {:hidden (not (:pwa/show-guide? state))
     :on     {:click [[:action/dismiss-install-guide-backdrop [:event/self-click?]]]}}
    [:div.app-install-guide__card
     [:div.app-install-guide__header
      [:h3 "Install Sprecha"]
      [:button.app-install-guide__close
       {:aria-label "Close"
        :on {:click [[:action/dismiss-install-guide]]}}
       "×"]]
     [:div.app-install-guide__steps
      [:div.app-install-guide__step
       [:span.app-install-guide__step-number "1"]
       [:span.app-install-guide__step-icon (share-svg)]
       [:span.app-install-guide__step-text "Tap " [:strong "Share"] " in the toolbar"]]
      [:div.app-install-guide__step
       [:span.app-install-guide__step-number "2"]
       [:span.app-install-guide__step-icon (add-square-svg)]
       [:span.app-install-guide__step-text "Tap " [:strong "Add to Home Screen"]]]
      [:div.app-install-guide__step
       [:span.app-install-guide__step-number "3"]
       [:span.app-install-guide__step-icon (checkmark-svg)]
       [:span.app-install-guide__step-text "Tap " [:strong "Add"] " to confirm"]]]]]))

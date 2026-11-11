(ns install-guide.view
  (:require
   [replicant.alias :as alias]))


(alias/defalias share-svg
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


(alias/defalias add-square-svg
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


(alias/defalias checkmark-svg
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


(alias/defalias step
  "A step of the guide: its number, then its icon and what to do, the children."
  [{:step/keys [number]} [icon text]]
  [:div.app-install-guide__step
   [:span.app-install-guide__step-number number]
   [:span.app-install-guide__step-icon icon]
   [:span.app-install-guide__step-text text]])


(defn render
  [state]
  [:div#app-install-guide.app-install-guide
   {:hidden (not (:pwa/show-guide? state))
    :on     {:click [[:action/dismiss-install-guide-backdrop [:event/self-click?]]]}}
   [:div.app-install-guide__card
    [:div.app-install-guide__header
     [:h3
      [:i18n :install-guide/title]]
     [:button.app-install-guide__close
      {:aria-label [:i18n :install-guide/close]
       :on {:click [[:action/dismiss-install-guide]]}}
      "×"]]
    [:div.app-install-guide__steps
     [step {:step/number "1"}
      [share-svg]
      [:i18n :install-guide/share]]
     [step {:step/number "2"}
      [add-square-svg]
      [:i18n :install-guide/add-to-home]]
     [step {:step/number "3"}
      [checkmark-svg]
      [:i18n :install-guide/add]]]]])

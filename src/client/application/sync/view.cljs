(ns application.sync.view)


(defn- sync-icon
  "A laptop beside a phone: connecting devices, not reloading the page."
  []
  ;; zprint: as written
  [:svg.icon
   {:aria-hidden    "true"
    :fill           "none"
    :stroke         "currentColor"
    :stroke-linecap "round"
    :stroke-linejoin "round"
    :stroke-width   "1.8"
    :viewBox        "0 0 24 24"}
   [:path {:d "M13 14H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1h11a1 1 0 0 1 1 1v1"}]
   [:path {:d "M1.5 18H13"}]
   [:rect {:x "16" :y "9" :width "6" :height "11" :rx "1.5"}]
   [:path {:d "M19 17.2h0"}]])


(defn- sync-menu-dialog
  [{:keys [show-account-actions?]}]
  [:dialog.sync-menu-dialog.modal
   {:replicant/on-mount [[:action/open-dialog]]
    :on {:click [[:action/dismiss-on-backdrop [:event/self-click?]]]
         :close [[:action/close-sync-menu]]}}
   [:div.content
    (when show-account-actions?
      (list
       [:button.item
        {:type "button"
         :on   {:click [[:effect/open-sync-qr]]}}
        [:i18n :sync/connect-device]]
       [:button.item
        {:type "button"
         :on   {:click [[:effect/share-sync-link
                         {:copied-message [:i18n :sync/recovery-link-copied]
                          :title [:i18n :sync/recovery-link-title]}]]}}
        [:i18n :sync/recovery-link]]))
    [:button.item
     {:type        "button"
      :data-cancel true
      :on          {:click [[:action/close-sync-menu]]}}
     [:i18n :sync/cancel]]]])


(defn- sync-qr-dialog
  [{:keys [qr-url]}]
  [:dialog.pairing-dialog.modal
   {:replicant/on-mount [[:action/open-dialog]]
    :on {:click [[:action/dismiss-on-backdrop [:event/self-click?]]]
         :close [[:action/close-sync-qr]]}}
   [:div.content
    [:h2.title
     [:i18n :sync/connect-device]]
    [:p.hint
     [:i18n :sync/scan-qr]]
    [:img.qr
     {:src qr-url
      :alt [:i18n :sync/qr]}]
    [:button.close
     {:type "button"
      :on   {:click [[:action/close-sync-qr]]}}
     [:i18n :dialog/close]]]])


(defn sync-button
  "The bar's button that opens the sync menu, when the device can use it."
  [{:keys [show-sync?]}]
  (when show-sync?
    [:button.icon-button
     {:type       "button"
      :title      [:i18n :sync/title]
      :aria-label [:i18n :sync/title]
      :on         {:click [[:action/open-sync-menu]]}}
     (sync-icon)]))


(defn dialogs
  "The sync menu and the QR dialog, whichever is open."
  [{:keys [menu-open? sync-qr] :as props}]
  (list
   (when menu-open?
     (sync-menu-dialog props))
   (when sync-qr
     (sync-qr-dialog sync-qr))))

(ns application.sync.view)


(defn- sync-icon
  "A laptop beside a phone: connecting devices, not reloading the page."
  []
  [:svg.app-shell__icon
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
   [:div.sync-menu-dialog__content
    (when show-account-actions?
      (list
       [:button.sync-menu-dialog__item
        {:type "button"
         :on   {:click [[:effect/open-sync-qr]]}}
        "Подключить устройство"]
       [:button.sync-menu-dialog__item
        {:type "button"
         :on   {:click [[:effect/share-sync-link
                         {:copied-message "Ссылка скопирована в буфер обмена"
                          :title          "Sprecha: восстановление доступа"}]]}}
        "Ссылка восстановления"]))
    [:button.sync-menu-dialog__item.sync-menu-dialog__item--cancel
     {:type "button"
      :on   {:click [[:action/close-sync-menu]]}}
     "Отмена"]]])


(defn- sync-qr-dialog
  [{:keys [qr-url]}]
  [:dialog.pairing-dialog.modal
   {:replicant/on-mount [[:action/open-dialog]]
    :on {:click [[:action/dismiss-on-backdrop [:event/self-click?]]]
         :close [[:action/close-sync-qr]]}}
   [:div.pairing-dialog__content
    [:h2.pairing-dialog__title "Подключить устройство"]
    [:p.pairing-dialog__hint "Отсканируйте QR-код на новом устройстве"]
    [:img.pairing-dialog__qr {:src qr-url :alt "QR-код для подключения устройства"}]
    [:button.pairing-dialog__close
     {:type "button"
      :on   {:click [[:action/close-sync-qr]]}}
     "Закрыть"]]])


(defn sync-button
  "The bar's button that opens the sync menu, when the device can use it."
  [{:keys [show-sync?]}]
  (when show-sync?
    [:button.app-shell__icon-button
     {:type       "button"
      :title      "Синхронизация"
      :aria-label "Синхронизация"
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

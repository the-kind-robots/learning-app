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
    (if show-account-actions?
      (list
       [:button.sync-menu-dialog__item
        {:type "button"
         :on   {:click [[:effect/open-sync-qr]]}}
        [:i18n :sync/connect-device]]
       [:button.sync-menu-dialog__item
        {:type "button"
         :on   {:click [[:effect/share-sync-link
                         {:copied-message [:i18n :sync/recovery-link-copied]
                          :title [:i18n :sync/recovery-link-title]}]]}}
        [:i18n :sync/recovery-link]])
      [:button.sync-menu-dialog__item
       {:type "button"
        :on   {:click [[:action/open-invite-request]]}}
       [:i18n :sync/keep-words]])
    [:button.sync-menu-dialog__item.sync-menu-dialog__item--cancel
     {:type "button"
      :on   {:click [[:action/close-sync-menu]]}}
     [:i18n :sync/cancel]]]])


(defn- sync-qr-dialog
  [{:keys [qr-url]}]
  [:dialog.pairing-dialog.modal
   {:replicant/on-mount [[:action/open-dialog]]
    :on {:click [[:action/dismiss-on-backdrop [:event/self-click?]]]
         :close [[:action/close-sync-qr]]}}
   [:div.pairing-dialog__content
    [:h2.pairing-dialog__title [:i18n :sync/connect-device]]
    [:p.pairing-dialog__hint [:i18n :sync/scan-qr]]
    [:img.pairing-dialog__qr {:src qr-url :alt [:i18n :sync/qr]}]
    [:button.pairing-dialog__close
     {:type "button"
      :on   {:click [[:action/close-sync-qr]]}}
     [:i18n :dialog/close]]]])


(defn- invite-request-dialog
  "A note on paper: a warm ask and an empty field. An empty field does not send; the browser holds it."
  []
  [:dialog.invite-dialog.modal
   {:aria-labelledby "invite-dialog-title"
    :replicant/on-mount [[:action/open-dialog]]
    :on {:click [[:action/dismiss-on-backdrop [:event/self-click?]]]
         :close [[:action/close-invite-request]]}}
   [:form.invite-dialog__content
    {:on {:submit [[:effect/prevent-default]
                   [:action/request-invite [:event.form.field/value "request"]]]}}
    [:h2#invite-dialog-title.invite-dialog__title [:i18n :sync/keep-words-title]]
    [:label.invite-dialog__ask {:for "invite-request"} [:i18n :sync/keep-words-ask]]
    [:textarea#invite-request.invite-dialog__note
     {:name     "request"
      :required true
      :rows     6}]
    [:button.invite-dialog__send.big-button.green-button {:type "submit"} [:i18n :sync/send]]
    [:button.invite-dialog__cancel
     {:type "button"
      :on   {:click [[:action/close-invite-request]]}}
     [:i18n :sync/cancel]]]])


(defn sync-button
  "The bar's button that opens the sync menu: grey without an account, lit green while online, faded offline."
  [{:keys [show-sync? status]}]
  (when show-sync?
    [:button.app-shell__sync
     {:type       "button"
      :class      (case status
                    :sync/online  "app-shell__sync--online"
                    :sync/offline "app-shell__sync--offline"
                    nil)
      :title      [:i18n :sync/title]
      :aria-label [:i18n :sync/title]
      :on         {:click [[:action/open-sync-menu]]}}
     (sync-icon)]))


(defn dialogs
  "The sync menu, the QR dialog and the invite form, whichever is open."
  [{:keys [invite-request-open? menu-open? sync-qr] :as props}]
  (list
   (when menu-open?
     (sync-menu-dialog props))
   (when invite-request-open?
     (invite-request-dialog))
   (when sync-qr
     (sync-qr-dialog sync-qr))))

(ns application.sync.view)


(defn- sync-icon
  "A laptop beside a phone: connecting devices, not reloading the page."
  []
  ;; zprint: as written
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


(defn- sync-menu
  "A popover under the control, as the settings are under the word mark: the browser opens and closes it,
   and each item closes it too."
  [{:keys [show-account-actions?]}]
  [:div.sync-menu
   {:id         "sync-menu"
    :aria-label [:i18n :sync/title]
    :popover    "auto"
    :role       "dialog"}
   (if show-account-actions?
     (list
      [:button.sync-menu__item
       {:popovertarget "sync-menu"
        :popovertargetaction "hide"
        :type "button"
        :on {:click [[:effect/open-sync-qr]]}}
       [:i18n :sync/connect-device]]
      [:button.sync-menu__item
       {:popovertarget "sync-menu"
        :popovertargetaction "hide"
        :type "button"
        :on {:click [[:effect/share-sync-link
                      {:copied-message [:i18n :sync/recovery-link-copied]
                       :title [:i18n :sync/recovery-link-title]}]]}}
       [:i18n :sync/recovery-link]])
     (list
      [:button.sync-menu__item
       {:popovertarget "sync-menu"
        :popovertargetaction "hide"
        :type "button"
        :on {:click [[:action/open-sync-dialog]]}}
       [:i18n :sync/enable]]
      ;; The installed app on iOS opens no link: its storage is apart from the browser's (#683).
      [:button.sync-menu__item
       {:popovertarget "sync-menu"
        :popovertargetaction "hide"
        :type "button"
        :on {:click [[:effect/paste-recovery-link
                      {:prompt-message  [:i18n :sync/paste-prompt]
                       :refused-message [:i18n :sync/not-a-recovery-link]}]]}}
       [:i18n :sync/paste-recovery-link]]))])


(defn- sync-qr-dialog
  [{:keys [qr-url]}]
  [:dialog.pairing-dialog.modal
   {:replicant/on-mount [[:action/open-dialog]]
    :on {:click [[:action/dismiss-on-backdrop [:event/self-click?]]]
         :close [[:action/close-sync-qr]]}}
   [:div.pairing-dialog__content
    [:h2.pairing-dialog__title
     [:i18n :sync/connect-device]]
    [:p.pairing-dialog__hint
     [:i18n :sync/scan-qr]]
    [:img.pairing-dialog__qr
     {:src qr-url
      :alt [:i18n :sync/qr]}]
    [:button.pairing-dialog__close
     {:type "button"
      :on   {:click [[:action/close-sync-qr]]}}
     [:i18n :dialog/close]]]])


(defn- sync-dialog
  "A note on paper: a warm ask and an empty field, sent once something is written in it."
  [{:keys [note send-disabled?]}]
  [:dialog.sync-dialog.modal
   {:aria-labelledby "sync-dialog-title"
    :replicant/on-mount [[:action/open-dialog]]
    :on {:click [[:action/dismiss-on-backdrop [:event/self-click?]]]
         :close [[:action/close-sync-dialog]]}}
   ;; The form takes the focus the dialog opens with: the field would raise the keyboard over the buttons.
   [:form.sync-dialog__content
    {:autofocus true
     :tabindex  "-1"
     :on        {:submit [[:effect/prevent-default]
                          [:action/request-sync]]}}
    [:h2#sync-dialog-title.sync-dialog__title
     [:i18n :sync/enable-title]]
    [:label.sync-dialog__ask
     {:for "sync-dialog-note"}
     [:i18n :sync/enable-ask]]
    ;; 500: the longest note the server takes (#689).
    [:textarea#sync-dialog-note.sync-dialog__note
     {:maxlength 500
      :name      "note"
      :on        {:input [[:action/write-sync-request-note [:event.target/value]]]}
      :rows      6
      :value     note}]
    [:button.sync-dialog__send.big-button.green-button
     {:disabled send-disabled?
      :type     "submit"}
     [:i18n :sync/send]]
    [:button.sync-dialog__cancel
     {:type "button"
      :on   {:click [[:action/close-sync-dialog]]}}
     [:i18n :sync/cancel]]]])


(defn sync-button
  "The bar's button that opens the sync menu: grey without an account, lit green while online, orange offline."
  [{:keys [show-sync? status]}]
  (when show-sync?
    [:button.app-shell__sync
     {:type          "button"
      :class         (case status
                       :sync/online  "app-shell__sync--online"
                       :sync/offline "app-shell__sync--offline"
                       nil)
      :title         [:i18n :sync/title]
      ;; The colour says it to the eye; the name says it to a screen reader.
      :aria-label    [:i18n
                      (case status
                        :sync/online  :sync/title-online
                        :sync/offline :sync/title-offline
                        :sync/title-off)]
      ;; The browser opens and closes the menu, and marks this button expanded while it is open.
      :popovertarget "sync-menu"}
     (sync-icon)]))


(defn dialogs
  "The sync menu beside its control, and the QR dialog or the form that asks to turn the sync on, whichever is
   open."
  [{:keys [open-dialog show-sync? sync-qr] :as props}]
  (list
   (when show-sync?
     (sync-menu props))
   (case open-dialog
     :dialog/qr      (sync-qr-dialog sync-qr)
     :dialog/request (sync-dialog props)
     nil)))

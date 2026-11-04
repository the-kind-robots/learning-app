(ns application.sync.actions
  "Joining another device to this account's sync: the dialog that shows the
   link as a QR code, and closing it when the device that opened the link has
   joined. Without an account, the form that asks us to turn the sync on. In
   Telegram's browser, the dialog of a credential link left for another. One
   of them is open at a time: `:app/sync-open` says which. The sync menu is
   the browser's popover and keeps no state."
  (:require
   [clojure.string :as str]
   [nexus.registry :as nxr]))


(defn- close
  "Closes the sync's dialog `open` if it is the one open; another opened since stays."
  [state open]
  (when (= open (:app/sync-open state))
    [[:effect/save {:app/sync-open nil}]]))


(nxr/register-action! :action/open-sync-dialog
  (fn open-sync-dialog [_]
    [[:effect/save
      {:app/sync-open :sync-open/request
       :app/sync-request-note ""}]]))


(nxr/register-action! :action/close-sync-dialog
  (fn close-sync-dialog [state]
    (close state :sync-open/request)))


(nxr/register-action! :action/write-sync-request-note
  (fn write-sync-request-note [_ note]
    [[:effect/save {:app/sync-request-note note}]]))


;; A note of nothing but spaces is not sent: the server answers it 400.
(nxr/register-action! :action/request-sync
  (fn request-sync [state]
    (let [note (:app/sync-request-note state)]
      (when-not (str/blank? note)
        [[:effect/request-sync (str/trim note)]]))))


(nxr/register-action! :action/show-sync-qr
  (fn show-sync-qr [_ qr]
    [[:effect/save
      {:app/sync-open :sync-open/qr
       :app/sync-qr   qr}]]))


(nxr/register-action! :action/close-sync-qr
  (fn close-sync-qr [state]
    (close state :sync-open/qr)))


(nxr/register-action! :action/close-held-link
  (fn close-held-link [state]
    (close state :sync-open/held-link)))


(nxr/register-action! :action/device-joined
  ;; The dialog may have been closed, or opened again for another nonce, since: then the news is of no use.
  (fn device-joined [state nonce]
    (when (= nonce (get-in state [:app/sync-qr :nonce]))
      (close state :sync-open/qr))))

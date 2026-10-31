(ns application.sync.actions
  "Joining another device to this account's sync: the sync menu, the dialog
   that shows the link as a QR code, and closing it when the device that
   opened the link has joined. Without an account, the form that asks for an
   invite."
  (:require
   [nexus.registry :as nxr]))


(nxr/register-action! :action/open-sync-menu
  (fn open-sync-menu [_]
    [[:effect/save {:app/sync-menu-open? true}]]))


(nxr/register-action! :action/close-sync-menu
  (fn close-sync-menu [_]
    [[:effect/save {:app/sync-menu-open? false}]]))


(nxr/register-action! :action/open-invite-request
  (fn open-invite-request [_]
    [[:effect/save
      {:app/invite-request-open? true
       :app/sync-menu-open?      false}]]))


(nxr/register-action! :action/close-invite-request
  (fn close-invite-request [_]
    [[:effect/save {:app/invite-request-open? false}]]))


(nxr/register-action! :action/request-invite
  (fn request-invite [_ text]
    [[:effect/request-invite text]
     [:effect/save {:app/invite-request-open? false}]]))


(nxr/register-action! :action/show-sync-qr
  (fn show-sync-qr [_ qr]
    [[:effect/save
      {:app/sync-menu-open? false
       :app/sync-qr qr}]]))


(nxr/register-action! :action/close-sync-qr
  (fn close-sync-qr [_]
    [[:effect/save {:app/sync-qr nil}]]))


(nxr/register-action! :action/device-joined
  ;; The dialog may have been closed, or opened again for another nonce, since: then the news is of no use.
  (fn device-joined [state nonce]
    (when (= nonce (get-in state [:app/sync-qr :nonce]))
      [[:effect/save {:app/sync-qr nil}]])))

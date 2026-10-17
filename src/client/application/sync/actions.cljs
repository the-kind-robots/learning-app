(ns application.sync.actions
  "Joining another device to this account's sync: the sync menu, the dialog
   that shows the link as a QR code, and closing it when the device that
   opened the link has joined."
  (:require
   [nexus.registry :as nxr]))


(nxr/register-action! :action/open-sync-menu
  (fn open-sync-menu [_]
    [[:effect/save {:app/sync-menu-open? true}]]))


(nxr/register-action! :action/close-sync-menu
  (fn close-sync-menu [_]
    [[:effect/save {:app/sync-menu-open? false}]]))


(nxr/register-action! :action/show-sync-qr
  (fn show-sync-qr [_ qr]
    [[:effect/save
      {:app/sync-menu-open? false
       :app/sync-qr         qr}]]))


(nxr/register-action! :action/close-sync-qr
  ;; The dialog stops waiting for the device that opened its link, if it
  ;; still waits.
  (fn close-sync-qr [state]
    [[:effect/stop-waiting-for-device (get-in state [:app/sync-qr :nonce])]
     [:effect/save {:app/sync-qr nil}]]))

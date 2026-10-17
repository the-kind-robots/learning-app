(ns application.sync.effects
  (:require
   ["qrcode" :as QRCode]
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]))


(nxr/register-effect! :effect/stop-waiting-for-device
  ;; The dialog closed before the device joined; `nonce` is nil when no
  ;; dialog was waiting.
  (fn stop-waiting-for-device [{:keys [capabilities]} _ nonce]
    (when nonce
      ((get-in capabilities [:learner :learner/stop-waiting-for-device]) nonce))))


(nxr/register-effect! :effect/share-sync-link
  ;; `title` and `copied-message` are the texts the view wants shown.
  (fn ^:async share-sync-link
    [{:keys [capabilities]} _ {:keys [copied-message title]}]
    (try
      (when-let [url (await ((get-in capabilities [:learner :learner/sync-link])))]
        (if (exists? js/navigator.share)
          (await (js/navigator.share #js {:title title :url url}))
          (do
            (await (.. js/navigator -clipboard (writeText url)))
            (js/alert copied-message))))
      (catch js/Error err
        (log/error :effect/share-sync-link {:error (str err)})))))


(nxr/register-effect! :effect/open-sync-qr
  (fn ^:async open-sync-qr
    [{:keys [capabilities dispatch]} _]
    (try
      ;; The nonce tags one round: the QR carries it out, the device that
      ;; opens the link echoes it back, and only that echo closes this
      ;; dialog.
      (let [nonce (js/crypto.randomUUID)
            {:learner/keys [on-device-joined sync-link]} (:learner capabilities)]
        (when-let [link (await (sync-link nonce))]
          (let [qr-url (await (.toDataURL QRCode link))]
            (on-device-joined nonce #(dispatch [[:action/close-sync-qr]]))
            (dispatch [[:action/show-sync-qr
                        {:link   link
                         :nonce  nonce
                         :qr-url qr-url}]]))))
      (catch js/Error err
        (log/error :effect/open-sync-qr {:error (str err)})))))

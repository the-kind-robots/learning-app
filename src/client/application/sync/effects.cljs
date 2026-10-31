(ns application.sync.effects
  (:require
   ["qrcode" :as QRCode]
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [ports.account :as account]))


(nxr/register-effect! :effect/share-sync-link
  ;; `title` and `copied-message` are the texts the view wants shown.
  (fn ^:async share-sync-link
    [{:keys [capabilities]} _ {:keys [copied-message title]}]
    (try
      (when-let [url (await (account/sync-link (:account capabilities)))]
        (if (exists? js/navigator.share)
          (await (js/navigator.share #js {:title title :url url}))
          (do
            (await (.. js/navigator -clipboard (writeText url)))
            (js/alert copied-message))))
      (catch js/Error err
        (log/error :effect/share-sync-link {:error (str err)})))))


(nxr/register-effect! :effect/request-invite
  ;; Only the console hears it until the invite service exists (#650).
  (fn request-invite [_ _ text]
    (js/console.info "Invite request:" text)))


(nxr/register-effect! :effect/open-sync-qr
  (fn ^:async open-sync-qr
    [{:keys [capabilities dispatch]} _]
    (try
      ;; The nonce tags one round: the QR carries it out, the opener echoes it back, and only that echo closes the dialog.
      (let [nonce   (js/crypto.randomUUID)
            account (:account capabilities)]
        (when-let [link (await (account/sync-link account nonce))]
          (let [qr-url (await (.toDataURL QRCode link))]
            (account/on-device-joined account nonce #(dispatch [[:action/device-joined nonce]]))
            (dispatch [[:action/show-sync-qr
                        {:link   link
                         :nonce  nonce
                         :qr-url qr-url}]]))))
      (catch js/Error err
        (log/error :effect/open-sync-qr {:error (str err)})))))

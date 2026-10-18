(ns ports.install
  "Putting the app on the device: the install prompt and the service worker that keeps it there and takes new
   builds. `adapters.install.*` does the work."
  (:require
   [adapters.install.prompt :as prompt]
   [adapters.install.service-worker :as service-worker]
   [nexus.registry :as nxr]))


(defprotocol Install
  (ios?
   [i]
   "Whether the device is an iPhone, iPod or iPad.")
  (listen-for-install!
   [i on-available on-installed]
   "Calls `on-available` when installing becomes possible and `on-installed` when it is done.")
  (prompt-install!
   [i]
   "Shows the browser's install prompt, if it offered one.")
  (registration
   [i]
   "A promise of the service worker's registration, nil where there is none."))


(defn start!
  "The install. The service worker registers here; the registration arrives later, as a promise."
  []
  (let [registered (service-worker/start!)]
    (reify Install
      (ios? [_] (prompt/ios?))
      (listen-for-install! [_ on-available on-installed] (prompt/listen! on-available on-installed))
      (prompt-install! [_] (prompt/prompt!))
      (registration [_] registered))))


;; The build mark's tap; only a development build has a mark.
(when ^boolean goog/DEBUG
  (nxr/register-effect! :effect/get-newest-build
    (fn get-newest-build [{:keys [capabilities]} _]
      (service-worker/get-newest-build! (registration (:install capabilities))))))

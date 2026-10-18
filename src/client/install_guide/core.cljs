(ns install-guide.core
  (:require
   [nexus.registry :as nxr]))


(nxr/register-action! :action/pwa-install-requested
  (fn pwa-install-requested [_]
    [[:effect/request-install]]))


(nxr/register-effect! :effect/request-install
  ;; On iOS the guide is the way to install; elsewhere the browser's prompt.
  (fn request-install [{:keys [capabilities dispatch]} _]
    (if ((get-in capabilities [:browser :browser/ios?]))
      (dispatch [[:effect/save {:pwa/show-guide? true}]])
      ((get-in capabilities [:browser :browser/prompt-install!])))))


(nxr/register-action! :action/dismiss-install-guide
  (fn dismiss-install-guide [_]
    [[:effect/save {:pwa/show-guide? false}]]))


(nxr/register-action! :action/dismiss-install-guide-backdrop
  (fn dismiss-install-guide-backdrop [_ self?]
    (when self?
      [[:effect/save {:pwa/show-guide? false}]])))

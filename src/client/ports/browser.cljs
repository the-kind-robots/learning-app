(ns ports.browser
  "The browser the app runs in, as the application reaches it: whether the
   page is shown and the device online, moving between screens, installing
   the app, and the service worker. `adapters.browser.*` does the work."
  (:require
   [adapters.browser.install-prompt :as install-prompt]
   [adapters.browser.navigation :as navigation]
   [adapters.browser.page :as page]
   [adapters.browser.service-worker :as service-worker]))


(defn start!
  "The browser capability. The service worker registers here (`:browser/registration`, a promise); the
   router starts later, with `:browser/follow-routes!`, over `routes` and the `home` page the history rests on."
  [{:keys [home routes] :as routing}]
  (js/window.addEventListener "pageshow" (fn [_] (page/sync-virtual-keyboard!)))
  {:browser/active?              page/active?
   :browser/follow-routes!       (navigation/follow-routes routing)
   :browser/ios?                 install-prompt/ios?
   :browser/listen-for-install!  install-prompt/listen!
   :browser/navigate             (partial navigation/navigate! home)
   :browser/on-inactive          page/on-inactive
   :browser/on-offline           page/on-offline
   :browser/on-online            page/on-online
   :browser/on-visibility-change page/on-visibility-change
   :browser/online?              page/online?
   :browser/prompt-install!      install-prompt/prompt!
   :browser/registration         (service-worker/start!)
   :browser/sync-virtual-keyboard! page/sync-virtual-keyboard!
   :browser/until-active         page/until-active
   :browser/yield                page/yield
   :browser/visible?             page/visible?})

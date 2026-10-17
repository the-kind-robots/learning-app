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
  "The browser capability. The service worker is registered here, at once;
   the registration, a promise, is `:browser/registration`. The router starts
   here too, over `routes`, a table of paths and page names; the page it
   matches comes through `:browser/on-route`, a subscription. A page shown
   again from the back-forward cache syncs the virtual keyboard
   (`:browser/sync-virtual-keyboard!`)."
  [{:keys [routes]}]
  (js/window.addEventListener "pageshow" (fn [_] (page/sync-virtual-keyboard!)))
  {:browser/active?              page/active?
   :browser/ios?                 install-prompt/ios?
   :browser/listen-for-install!  install-prompt/listen!
   :browser/navigate             navigation/navigate!
   :browser/on-inactive          page/on-inactive
   :browser/on-offline           page/on-offline
   :browser/on-online            page/on-online
   :browser/on-route             (navigation/start-router! routes)
   :browser/on-visibility-change page/on-visibility-change
   :browser/online?              page/online?
   :browser/prompt-install!      install-prompt/prompt!
   :browser/registration         (service-worker/start!)
   :browser/sync-virtual-keyboard! page/sync-virtual-keyboard!
   :browser/until-active         page/until-active
   :browser/yield                page/yield
   :browser/visible?             page/visible?})

(ns application.sync.presenter
  (:require
   [clojure.string :as str]))


(defn props
  "What joining a device to the sync shows. The entry point stands in the bar on every screen once the learner's
   data is loaded and shows the sync's `:status`; the menu offers account actions to a device that has an account
   (ADR-0006), and to one that has none the dialog that asks us to turn the sync on and the paste of a recovery
   link."
  [state]
  {:held-link      (:app/held-link state)
   :note           (:app/sync-request-note state)
   :open-dialog    (case (:app/sync-open state)
                     :sync-open/held-link :dialog/held-link
                     :sync-open/qr        :dialog/qr
                     :sync-open/request   :dialog/request
                     nil)
   :send-disabled? (str/blank? (:app/sync-request-note state))
   :show-account-actions? (contains? #{:sync/offline :sync/online} (:sync/status state))
   :show-sync?     (= :status/loaded (:learner/status state))
   :status         (:sync/status state)
   :sync-qr        (:app/sync-qr state)})

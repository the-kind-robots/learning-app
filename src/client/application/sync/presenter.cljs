(ns application.sync.presenter
  (:require
   [clojure.string :as str]))


(defn props
  "What joining a device to the sync shows. The entry point is a home-page errand that shows the sync's
   `:status` once the learner's data is loaded; the menu offers account actions to a device that has an account
   (ADR-0006), and the dialog that asks us to turn the sync on to one that has none."
  [state]
  {:note           (:app/sync-request-note state)
   :open-dialog    (case (:app/sync-open state)
                     :sync-open/menu    :dialog/menu
                     :sync-open/qr      :dialog/qr
                     :sync-open/request :dialog/request
                     nil)
   :send-disabled? (str/blank? (:app/sync-request-note state))
   :show-account-actions? (contains? #{:sync/offline :sync/online} (:sync/status state))
   :show-sync?     (and (= :page/home (:page/current state))
                        (= :status/loaded (:learner/status state)))
   :status         (:sync/status state)
   :sync-qr        (:app/sync-qr state)})

(ns application.sync.presenter
  (:require
   [clojure.string :as str]))


(defn props
  "What joining a device to the sync shows. The entry point stands in the bar on every screen once the learner's
   data is loaded and shows the sync's `:status`; the menu offers account actions to a device that has an account
   (ADR-0006), and the dialog that asks us to turn the sync on to one that has none."
  [state]
  {:open-dialog (case (:app/sync-open state)
                  :sync-open/qr      :dialog/qr
                  :sync-open/request :dialog/request
                  nil)
   :show-sync? (= :status/loaded (:learner/status state))
   :sync-button/status (:sync/status state)
   :sync-dialog/note (:app/sync-request-note state)
   :sync-dialog/send-disabled? (str/blank? (:app/sync-request-note state))
   :sync-menu/show-account-actions? (contains? #{:sync/offline :sync/online} (:sync/status state))
   :sync-qr-dialog/qr-url (get-in state [:app/sync-qr :qr-url])})

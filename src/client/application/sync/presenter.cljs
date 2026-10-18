(ns application.sync.presenter)


(defn props
  "What joining a device to the sync shows. The entry point is a home-page errand, offered only to a device
   that has an account (ADR-0006); the menu offers account actions to it alone."
  [state]
  {:menu-open?            (boolean (:app/sync-menu-open? state))
   :sync-qr               (:app/sync-qr state)
   :show-account-actions? (boolean (:app/has-account? state))
   :show-sync?            (and (= :page/home (:page/current state))
                               (boolean (:app/has-account? state)))})

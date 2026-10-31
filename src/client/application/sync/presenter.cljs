(ns application.sync.presenter)


(defn props
  "What joining a device to the sync shows. The entry point is a home-page errand that shows the sync's
   `:status`; the menu offers account actions to a device that has an account (ADR-0006), and the invite form
   to one that has none."
  [state]
  {:invite-request-open? (boolean (:app/invite-request-open? state))
   :menu-open? (boolean (:app/sync-menu-open? state))
   :sync-qr    (:app/sync-qr state)
   :show-account-actions? (boolean (:app/has-account? state))
   :show-sync? (= :page/home (:page/current state))
   :status     (:sync/status state)})

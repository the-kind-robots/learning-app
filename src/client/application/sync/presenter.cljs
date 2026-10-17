(ns application.sync.presenter)


(defn props
  "What joining a device to the sync shows. `:show-sync?` is where the invite gate
   becomes visible (ADR-0006): sync has no entry point until an account
   exists, and an account exists only once an invite has been redeemed.
   Connecting a device is a home-page errand, so the entry point is offered
   there and nowhere else. The menu offers account actions only to a device
   that has an account; the way out is always there."
  [state]
  {:menu-open?            (boolean (:app/sync-menu-open? state))
   :sync-qr               (:app/sync-qr state)
   :show-account-actions? (boolean (:app/has-account? state))
   :show-sync?            (and (= :page/home (:page/current state))
                               (boolean (:app/has-account? state)))})

(ns application.presenter)


(defn shell-props
  "What the shell shows, in its own terms rather than the state's.

   `:show-sync?` is where the invite gate becomes visible (ADR-0006): sync has
   no entry point until an account exists, and an account exists only once an
   invite has been redeemed."
  [state]
  {:menu-open?    (boolean (:app/sync-menu-open? state))
   :page          (:page/current state)
   :pairing       (:app/pairing state)
   :show-install? (boolean (:pwa/install-available? state))
   :show-sync?    (some? (:app/account-id state))})


(defn sync-menu-props
  "The menu offers account actions only to a device that has an account; the
   way out is always there."
  [state]
  {:show-account-actions? (some? (:app/account-id state))})

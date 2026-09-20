(ns application.presenter
  (:require
   [build-identity]))


(defn shell-props
  "What the shell shows, in its own terms rather than the state's.

   `:show-sync?` is where the invite gate becomes visible (ADR-0006): sync has
   no entry point until an account exists, and an account exists only once an
   invite has been redeemed.

   `:build-mark` comes from the bundle rather than the state — nothing the app
   does changes which build is running — and is empty in a release build."
  [state]
  {:build-mark    build-identity/stamp
   :menu-open?    (boolean (:app/sync-menu-open? state))
   :page          (:page/current state)
   :pairing       (:app/pairing state)
   :show-install? (boolean (:pwa/install-available? state))
   :show-sync?    (some? (:app/account-id state))
   :show-update?  (boolean (:pwa/new-build-waiting? state))})


(defn sync-menu-props
  "The menu offers account actions only to a device that has an account; the
   way out is always there."
  [state]
  {:show-account-actions? (some? (:app/account-id state))})

(ns application.presenter
  (:require
   [build-identity]))


(defn- page
  [state]
  (when (:learner/loaded? state)
    (:page/current state)))


(defn shell-props
  "What the shell shows, in its own terms rather than the state's.

   `:show-sync?` is where the invite gate becomes visible (ADR-0006): sync has
   no entry point until an account exists, and an account exists only once an
   invite has been redeemed. Connecting a device is a home-page errand, so the
   entry point is offered there and nowhere else.

   `:corner` is the control in the top-right corner: the themes icon on home,
   the close mark on every other page (ADR-0015). Nil while no page is on
   display yet.

   `:page` is nil until memory has the learner's data:
   every screen answers from memory, so none is shown before it can.

   `:read-error` is what the shell shows in place of a screen when
   the learner's data could not be read at start: that it cannot be read,
   and that a reload is the way out. It is nil otherwise. Before memory is
   loaded the shell renders only after such a failure; until then the
   server's splash stays.

   `:build-mark` comes from the bundle rather than the state — nothing the app
   does changes which build is running — and is empty in a release build."
  [state]
  {:build-mark    build-identity/stamp
   :corner        (case (page state)
                    :page/home :collections
                    (:page/collections :page/lesson :page/words) :close
                    nil)
   :menu-open?    (boolean (:app/sync-menu-open? state))
   :page          (page state)
   :pairing       (:app/pairing state)
   :read-error    (when (:learner/unreadable? state)
                    "Не получается прочитать данные на устройстве. Перезагрузите страницу.")
   :show-install? (boolean (:pwa/install-available? state))
   :show-sync?    (and (= :page/home (:page/current state))
                       (some? (:app/account-id state)))
   :show-update?  (boolean (:pwa/new-build-waiting? state))})


(defn sync-menu-props
  "The menu offers account actions only to a device that has an account; the
   way out is always there."
  [state]
  {:show-account-actions? (some? (:app/account-id state))})

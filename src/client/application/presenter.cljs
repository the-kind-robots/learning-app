(ns application.presenter
  (:require
   [build-identity]))


(defn- page
  [state]
  (when (:learner/loaded? state)
    (:page/current state)))


(defn shell-props
  "What the shell shows, in its own terms rather than the state's.

   `:corner` is the control in the top-right corner: the themes icon on home,
   the close mark on every other page (ADR-0015). Nil while no page is on
   display yet.

   `:page` is nil until memory has the learner's data:
   every screen answers from memory, so none is shown before it can.

   `:read-error?` is whether the shell shows, in place of a screen, that the
   learner's data could not be read at start. Before memory is loaded the
   shell renders only after such a failure; until then the server's splash
   stays.

   `:build-mark` comes from the bundle rather than the state — nothing the app
   does changes which build is running — and is empty in a release build."
  [state]
  {:build-mark    build-identity/stamp
   :corner        (case (page state)
                    :page/home :collections
                    (:page/collections :page/lesson :page/words) :close
                    nil)
   :page          (page state)
   :read-error?   (boolean (:learner/unreadable? state))
   :show-install? (boolean (:pwa/install-available? state))})

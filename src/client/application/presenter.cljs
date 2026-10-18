(ns application.presenter
  (:require
   [build-identity]))


(defn- page
  [state]
  (when (= :status/loaded (:learner/status state))
    (:page/current state)))


(defn shell-props
  "What the shell shows: `:corner` (themes icon on home, close mark elsewhere, ADR-0015), `:page` (nil until
   memory is loaded), `:read-error?` (the learner's data could not be read), `:build-mark` (the bundle's,
   empty in a release build)."
  [state]
  {:build-mark    build-identity/stamp
   :corner        (case (page state)
                    :page/home :collections
                    (:page/collections :page/lesson :page/words) :close
                    nil)
   :page          (page state)
   :read-error?   (= :status/unreadable (:learner/status state))
   :show-install? (boolean (:pwa/install-available? state))})

(ns application.presenter
  (:require
   [application.pages :as pages]
   [build-identity]))


(defn- page
  [state]
  (when (= :status/loaded (:learner/status state))
    (:page/current state)))


(defn stars
  "Today's lessons as stars, nil for none: `:lessons` passed, `:gold` stars, and the purple stars five gold make,
   as `:purple` stars, or from five on as one star after `:purple-number`."
  [lessons]
  (when (pos? lessons)
    (let [purple (quot lessons 5)]
      (merge {:gold    (rem lessons 5)
              :lessons lessons}
             (if (>= purple 5)
               {:purple 1 :purple-number purple}
               {:purple purple})))))


(defn shell-props
  "What the shell shows: `:corner` (themes icon on home, close mark elsewhere, ADR-0015), `:page` (nil until
   memory is loaded), `:read-error?` (the learner's data could not be read), `:build-mark` (the bundle's,
   empty in a release build), `:stars` (today's lessons, `stars`)."
  [state]
  {:build-mark    build-identity/stamp
   :corner        (case (page state)
                    :page/home :collections
                    (:page/collections :page/lesson :page/words) :close
                    nil)
   :page          (page state)
   :read-error?   (= :status/unreadable (:learner/status state))
   :show-install? (boolean (:pwa/install-available? state))
   :stars         (when (page state)
                    (stars (:learner/lessons-today state 0)))})


(defn screen-change
  "The transition from the screen of `before` to that of `after`, by the pages' `:transitions`: the screen's
   `:within` when it stays, else the `:in` of the one coming on, the `:out` of the one going off, or back to home
   or deeper. Nil when either is not drawn yet."
  [before after]
  (let [from (page before)
        to   (page after)]
    (cond
      (or (nil? from) (nil? to)) nil
      (= from to) (when-let [within (get-in (pages/page to) [:transitions :within])]
                    (within before after))
      :else (or (get-in (pages/page to) [:transitions :in])
                (get-in (pages/page from) [:transitions :out])
                (if (= pages/home to) :transition/back :transition/deeper)))))

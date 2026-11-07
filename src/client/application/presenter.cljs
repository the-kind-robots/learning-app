(ns application.presenter
  (:require
   [application.pages :as pages]
   [build-identity]))


(defn- page
  [state]
  (when (= :status/loaded (:learner/status state))
    (:page/current state)))


(defn stars
  "Today's lessons as stars: `:lessons` passed, nil for none; `:purple-number` from five purple stars on; and
   `:stars`, newest first, gold then purple, each with its `:color` and an `:id` that stays while the star stays.
   Five gold stars make one purple, the purple of their five; from five purple on, the number and the newest
   purple stand for them. Only the count is drawn: a star that comes and the stars that go move by the view's
   CSS, which lays the row from the right, so a star that comes stands after those that go."
  [lessons]
  (let [purple (quot lessons 5)
        number (when (>= purple 5) purple)]
    {:lessons       (when (pos? lessons) lessons)
     :purple-number number
     :stars         (reverse
                     (concat
                      (for [five (if number
                                   [(dec purple)]
                                   (range purple))]
                        {:color :star/purple
                         :id    [:purple five]})
                      (for [index (range (rem lessons 5))]
                        {:color :star/gold
                         :id    [:gold purple index]})))}))


(defn stars-button?
  "Whether today's stars are a button that opens the statistics: with a lesson today, outside a lesson and the
   statistics."
  [state]
  (and (pos? (:learner/lessons-today state 0))
       (contains? #{:page/collections :page/home :page/words} (page state))))


(defn shell-props
  "What the shell shows: `:corner` (themes icon on home, close mark elsewhere, ADR-0015), `:page` (nil until
   memory is loaded), `:read-error?` (the learner's data could not be read), `:build-mark` (the bundle's,
   empty in a release build), `:stars` (today's lessons, `stars`, from the moment memory is loaded, so that a
   star passed later joins a row already there), and whether they are a button (`:stars-button?`)."
  [state]
  {:build-mark    build-identity/stamp
   :corner        (case (page state)
                    :page/home :collections
                    (:page/collections :page/lesson :page/stats :page/words) :close
                    nil)
   :page          (page state)
   :read-error?   (= :status/unreadable (:learner/status state))
   :show-install? (boolean (:pwa/install-available? state))
   :stars         (when (page state)
                    (stars (:learner/lessons-today state 0)))
   :stars-button? (stars-button? state)})


(defn screen-change
  "The transition from the screen of `before` to that of `after`, by the pages' `:transitions`: the screen's
   `:within` when it stays, else the `:in` of the one coming on, the `:out` of the one going off, or deeper when
   the window left stays open beneath, back when it was taken off. Nil when either is not drawn yet."
  [before after]
  (let [from (page before)
        to   (page after)]
    (cond
      (or (nil? from) (nil? to)) nil
      (= from to) (when-let [within (get-in (pages/page to) [:transitions :within])]
                    (within before after))
      :else (or (get-in (pages/page to) [:transitions :in])
                (get-in (pages/page from) [:transitions :out])
                (if (some #{from} (:page/windows after))
                  :transition/deeper
                  :transition/back)))))

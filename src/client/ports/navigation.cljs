(ns ports.navigation
  "The only writer of the browser history. It keeps the history as a home
   entry with at most one screen above it (ADR-0015), so Back from a screen is
   home and Back from home leaves the app."
  (:require
   [reitit.frontend.easy :as rfe]
   [reitit.frontend.history :as rfh]))


(def ^:private home-path "/home")


(def ^:private over-home
  "The `history.state` of a screen entry that stands on a home entry. A
   string, not an object: it survives a reload and back/forward as is, and
   advanced compilation has no property name in it to rename."
  "over-home")


(defn move
  "The history write that takes the app to home (`to-home?`) or to a screen.
   `at-home?` is whether the current entry is home, `over-home?` whether it
   stands on a home entry. Nil when there is nothing to do.

   A screen opened from home is pushed and marked; one opened from a screen
   takes its entry and its mark. Home is reached by stepping back onto the
   home entry beneath. Only an entry with no mark — written before the history
   was kept this way — is replaced by home instead."
  [{:keys [at-home? over-home? to-home?]}]
  (cond
    (and to-home? at-home?) nil
    (and to-home? over-home?) {:write :back}
    to-home? {:over-home? false :write :replace}
    at-home? {:over-home? true :write :push}
    :else {:over-home? over-home? :write :replace}))


(defn home-beneath-needed?
  "Whether the entry the app was opened on needs a home entry written beneath
   it: a route other than home that does not stand on home already. A reload
   or a back/forward onto a screen finds its mark and needs none; an unknown
   path is the router's to replace with home."
  [{:keys [home? over-home? route?]}]
  (and route? (not home?) (not over-home?)))


(defn- entry-over-home?
  []
  (= over-home (.. js/window -history -state)))


(defn- at-home?
  []
  (= home-path (.. js/window -location -pathname)))


(defn navigate!
  "Takes the app to `page` by the write `move` picks. A push or a replace is
   written here, with its mark, and then handed to reitit the way reitit's own
   push does — neither fires popstate. A step back fires it, and reitit takes
   it from there."
  [page]
  (let [path  (rfe/href page)
        {:keys [write] :as step}
        (move {:at-home?   (at-home?)
               :over-home? (entry-over-home?)
               :to-home?   (= home-path path)})
        state (when (:over-home? step) over-home)]
    (case write
      nil      nil
      :back    (.back js/window.history)
      :push    (.pushState js/window.history state "" path)
      :replace (.replaceState js/window.history state "" path))
    (when (#{:push :replace} write)
      (rfh/-on-navigate @rfe/history path))))


(defn put-home-beneath!
  "Writes a home entry beneath the entry the app was opened on when it needs
   one. Runs before the router starts, so the router still matches the
   landing path and the address bar never shows home."
  [route?]
  (let [location (.-location js/window)
        path     (str (.-pathname location) (.-search location) (.-hash location))]
    (when (home-beneath-needed? {:home?      (at-home?)
                                 :over-home? (entry-over-home?)
                                 :route?     (route? (.-pathname location))})
      (.replaceState js/window.history nil "" home-path)
      (.pushState js/window.history over-home "" path))))


(defn start!
  [_deps]
  {:navigation/navigate navigate!})

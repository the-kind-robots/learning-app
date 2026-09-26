(ns ports.navigation
  "The only writer of the browser history. It keeps the history as a home
   entry with at most one screen above it (ADR-0015), so Back from a screen is
   home and Back from home leaves the app."
  (:require
   [reitit.frontend :as rf]
   [reitit.frontend.easy :as rfe]))


(def ^:private home :page/home)


(defn move
  "How the app gets from where it is to `to`: nothing from home to home, a
   push from home to a screen, a replace from a screen to a screen, and a step
   back from a screen onto the home entry beneath it."
  [{:keys [at-home? to]}]
  (cond
    (and at-home? (= home to)) nil
    at-home?    :push
    (= home to) :back
    :else       :replace))


(defn- at-home?
  []
  (= (rfe/href home) (.. js/window -location -pathname)))


(defn navigate!
  "Moves to `page`. A push or a replace reaches the router in this task; a
   step back reaches it only with `popstate`, a task later, so `show-home` —
   which puts home on display — is called first, and home is on screen in the
   task of the tap all the same."
  [page show-home]
  (case (move {:at-home? (at-home?) :to page})
    nil      nil
    :push    (rfe/push-state page)
    :replace (rfe/replace-state page)
    :back    (do (show-home)
                 (.back js/window.history))))


(defn put-home-beneath!
  "On a fresh landing on a screen, writes a home entry beneath it, so Back
   from it is home. A reload or a back/forward lands on an entry that already
   has one. Runs before the router starts, so the router matches the landing
   path and the address bar never shows home — which is also why the paths
   come from the router itself: reitit.frontend.easy has none to give yet."
  [router]
  (let [location  (.-location js/window)
        path      (str (.-pathname location) (.-search location) (.-hash location))
        landing   (some-> (.getEntriesByType js/performance "navigation") (aget 0) .-type)
        home-path (:path (rf/match-by-name router home))]
    (when (and (= "navigate" landing)
               (rf/match-by-path router (.-pathname location))
               (not= home-path (.-pathname location)))
      (.replaceState js/window.history nil "" home-path)
      (.pushState js/window.history nil "" path))))


(defn start!
  [_deps]
  {:navigation/navigate navigate!})

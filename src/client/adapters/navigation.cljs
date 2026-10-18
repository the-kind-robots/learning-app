(ns adapters.navigation
  "The only writer of the browser history. It keeps the history as a home
   entry with at most one screen above it (ADR-0015), so Back from a screen is
   home and Back from home leaves the app."
  (:require
   [reitit.frontend :as rf]
   [reitit.frontend.controllers :as rfc]
   [reitit.frontend.easy :as rfe]))


(defn navigator
  "The navigator over `routes`, a table of paths and page names, and the `home` page the history rests on. It
   keeps what changes (the router and its controllers, the moves waiting for a step back) in one atom."
  [{:keys [home routes]}]
  {:home   home
   :routes routes
   :state  (atom {:controllers nil :router nil :stepping-back nil})})


(defn move
  "How the app gets from where it is to `to`: nothing from home to home, a
   push from home to a screen, a replace from a screen to a screen, and a step
   back from a screen onto the home entry beneath it."
  [{:keys [at-home? home to]}]
  (cond
    (and at-home? (= home to)) nil
    at-home?    :push
    (= home to) :back
    :else       :replace))


(defn follow!
  "Starts the router and calls `on-enter` with the page of every route entered. Starting waits for that call,
   so an incoming credential in the address is read before the router redirects an unknown path and drops the
   fragment."
  [{:keys [home routes state]} on-enter]
  (let [router (rf/router (mapv (fn [[path data]]
                                  [path (assoc data :controllers [{:start (fn [_] (on-enter (:name data)))}])])
                                routes))]
    ;; A query that is not valid percent-encoding makes the router throw (`URIError`); it is dropped.
    (try
      (js/decodeURIComponent js/location.search)
      (catch :default _
        (js/history.replaceState nil "" (str js/location.pathname js/location.hash))))
    ;; Opened straight on a screen: home goes under it, so Back leads home (ADR-0015). Before the router
    ;; starts, so the address bar never shows home.
    (let [location  (.-location js/window)
          path      (str (.-pathname location) (.-search location) (.-hash location))
          landing   (some-> (.getEntriesByType js/performance "navigation") (aget 0) .-type)
          home-path (:path (rf/match-by-name router home))]
      (when (and (= "navigate" landing)
                 (rf/match-by-path router (.-pathname location))
                 (not= home-path (.-pathname location)))
        (.replaceState js/window.history nil "" home-path)
        (.pushState js/window.history nil "" path)))
    (swap! state assoc :router router)
    (rfe/start!
     router
     (fn [match _history]
       (if match
         (swap! state update :controllers rfc/apply-controllers match)
         ;; A path with no route is a redirect: replace the entry, which also drops the fragment and so an
         ;; incoming credential from the address bar.
         (rfe/replace-state home)))
     ;; Reitit's anchor handler stays out: every shell link asks the navigation port (ADR-0015).
     {:ignore-anchor-click? (constantly false)
      :use-fragment false})))


(defn navigate!
  "Moves to `page`. A step back reaches the router only with `popstate`, a task later, so home's controllers run
   first and home is on screen in the task of the tap; the `popstate` finds them running and enters nothing
   again. A move asked for before that `popstate` waits for it."
  [{:keys [home state] :as navigator} page]
  (if (:stepping-back @state)
    (swap! state update :stepping-back conj page)
    (case (move {:at-home? (= (rfe/href home) (.. js/window -location -pathname)) :home home :to page})
      nil      nil
      :push    (rfe/push-state page)
      :replace (rfe/replace-state page)
      :back    (do (swap! state update :controllers rfc/apply-controllers (rf/match-by-name (:router @state) home))
                   ;; The address bar still shows the screen being left, so a move judged now would push over it.
                   (swap! state assoc :stepping-back [])
                   (js/window.addEventListener "popstate"
                                               (fn [_]
                                                 (let [queued (:stepping-back @state)]
                                                   (swap! state assoc :stepping-back nil)
                                                   (run! #(navigate! navigator %) queued)))
                                               #js {:once true})
                   (.back js/window.history)))))

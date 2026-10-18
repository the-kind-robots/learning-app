(ns adapters.browser.navigation
  "The only writer of the browser history. It keeps the history as a home
   entry with at most one screen above it (ADR-0015), so Back from a screen is
   home and Back from home leaves the app."
  (:require
   [reitit.frontend :as rf]
   [reitit.frontend.controllers :as rfc]
   [reitit.frontend.easy :as rfe]))


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


(defn- at-home?
  [home]
  (= (rfe/href home) (.. js/window -location -pathname)))


;; Moves asked for while a step back is still on its way to `popstate`: the address bar still shows
;; the screen being left, so a move judged now would push over it.
(defonce ^:private stepping-back
  (atom nil))


(declare navigate!)


(defn- stepped-back!
  [home]
  (let [[queued _] (reset-vals! stepping-back nil)]
    (doseq [[page show-home] queued]
      (navigate! home page show-home))))


(defn navigate!
  "Moves to `page`. A step back reaches the router only with `popstate`, a task later, so `show-home`
   is called first and home is on screen in the task of the tap. A move asked for before that
   `popstate` waits for it."
  [home page show-home]
  (if @stepping-back
    (swap! stepping-back conj [page show-home])
    (case (move {:at-home? (at-home? home) :home home :to page})
      nil      nil
      :push    (rfe/push-state page)
      :replace (rfe/replace-state page)
      :back    (do (show-home)
                   (reset! stepping-back [])
                   (js/window.addEventListener "popstate" #(stepped-back! home) #js {:once true})
                   (.back js/window.history)))))


(defn follow-routes
  "Returns `follow-routes!`, which starts the router over `routes`, a table of paths and page names, and
   calls its argument with the page of every route entered. Starting waits for that call, so an incoming
   credential in the address is read before the router redirects an unknown path and drops the fragment."
  [{:keys [home routes]}]
  (let [controllers (atom nil)]
    (fn follow-routes!
      [f]
      (let [router (rf/router (mapv (fn [[path data]]
                                      [path (assoc data :controllers [{:start (fn [_] (f (:name data)))}])])
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
        (rfe/start!
         router
         (fn [match _history]
           (if match
             (reset! controllers (rfc/apply-controllers @controllers match))
             ;; A path with no route is a redirect: replace the entry, which also drops the fragment and so an
             ;; incoming credential from the address bar.
             (rfe/replace-state home)))
         ;; Reitit's anchor handler stays out: every shell link asks the navigation port (ADR-0015).
         {:ignore-anchor-click? (constantly false)
          :use-fragment false})))))

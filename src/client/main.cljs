(ns main
  (:require
   [application]
   [db-migrations :as db-migrations]
   [dbs :as dbs]
   [install-guide.core]
   [install-guide.view :as install-guide]
   [lambdaisland.glogi :as log]
   [logging]
   [nexus.action-log :as action-log]
   [nexus.registry :as nxr]
   [pages.home.actions]
   [pages.home.effects]
   [pages.home.view :as pages.home.view]
   [pages.lesson.actions]
   [pages.lesson.effects]
   [pages.lesson.view :as pages.lesson.view]
   [pages.words.actions]
   [pages.words.effects]
   [pages.words.view :as pages.words.view]
   [reitit.frontend :as rf]
   [reitit.frontend.controllers :as rfc]
   [reitit.frontend.easy :as rfe]
   [replicant.dom :as r]
   [tasks :as tasks]))


(defonce store
  (atom {:app/page :page/loading}))


(defn- dispatch!
  ([actions]
   (nxr/dispatch store {} actions))
  ([dispatch-data actions]
   (nxr/dispatch store dispatch-data actions)))


(defonce ^:private router-controllers (atom nil))


(def ^:private router
  (rf/router
   [["/home"
     {:name        :page/home
      :controllers [{:start #(dispatch! [[:effect/load-home]])}]}]
    ["/words"
     {:name        :page/words
      :controllers [{:start #(dispatch! [[:effect/load-words]])}]}]
    ["/lesson"
     {:name        :page/lesson
      :controllers [{:start #(dispatch! [[:effect/load-lesson]])}]}]]))


(defn- sync-virtual-keyboard!
  []
  (when (js-in "virtualKeyboard" js/navigator)
    (when-let [vk (.-virtualKeyboard js/navigator)]
      (set! (.-overlaysContent vk)
            (boolean (js/document.querySelector "[data-vk-overlay]"))))))


(defn- render
  [state]
  (list
   [:a.app-shell__logo {:href "/home"} "Sprecha"]
   (install-guide/render state)
   (case (:app/page state)
     :page/home   (pages.home.view/page state)
     :page/words  (pages.words.view/page state)
     :page/lesson (pages.lesson.view/page state)
     [:div.app-loading "Загружаем..."])))


(defn- render!
  [state]
  (r/render js/document.body (render state))
  (sync-virtual-keyboard!))


(defn ^:async init
  []
  (when goog/DEBUG
    (action-log/inspect))
  (nxr/register-system->state! deref)
  (r/set-dispatch! dispatch!)
  (add-watch store ::render #(render! %4))
  (dispatch! [[:effect/pwa-init]])
  (js/window.addEventListener "pageshow" sync-virtual-keyboard!)

  (await (db-migrations/ensure-migrated!))
  (await (tasks/start!))
  (dbs/init!)
  (when (js-in "serviceWorker" js/navigator)
    (js/navigator.serviceWorker.register "/js/app/sw.js" #js {:scope "/"})))


(defn ^:export start
  []
  (.. (init)
      (catch (fn [err]
               (log/error :main/boot-failed {:error (str err)}))))

  (rfe/start!
   router
   (fn [match _history]
     (if match
       (reset! router-controllers (rfc/apply-controllers @router-controllers match))
       (rfe/navigate :page/home)))
   {:use-fragment false}))

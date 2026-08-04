(ns main
  (:require
   [application]
   [db.pouch :as pouch]
   [db.sqlite :as sqlite]
   [install-guide.core]
   [lambdaisland.glogi :as log]
   [logging]
   [nexus.action-log :as action-log]
   [nexus.registry :as nxr]
   [pages.collections.actions]
   [pages.collections.effects]
   [pages.collections.view]
   [pages.home.actions]
   [pages.home.effects]
   [pages.lesson.actions]
   [pages.lesson.effects]
   [pages.words.actions]
   [pages.words.effects]
   [ports.clock :as clock]
   [ports.collections :as collections]
   [ports.dictionary :as dictionary]
   [ports.examples :as examples]
   [ports.navigation :as navigation]
   [ports.progress-store :as progress-store]
   [ports.task-queue :as task-queue]
   [reitit.frontend :as rf]
   [reitit.frontend.controllers :as rfc]
   [reitit.frontend.easy :as rfe]
   [replicant.dom :as r]
   [runtime.system :as system]
   [sync]))


(defn ^:async init
  []
  (when goog/DEBUG
    (action-log/inspect))

  (system/start!
   {:app/store           {:start (fn [_] (atom {:page/current :page/loading}))}

    :worker/service-worker {:start
                            #(when (js-in "serviceWorker" js/navigator)
                               (js/navigator.serviceWorker.register "/js/app/sw.js" #js {:scope "/"}))}

    :document/listeners  {:start
                          (fn [_]
                            (js/window.addEventListener "pageshow" application/sync-virtual-keyboard!)
                            application/sync-virtual-keyboard!)}

    :db/sqlite           {:start sqlite/init!}
    :identity/recovery   {:start sync/check-incoming-auth!}

    :db/pouch            {:after [:identity/recovery]
                          :start pouch/init!}

    :sync/identity       {:requires {:db :db/pouch}
                          :start    sync/start!}

    :port/clock          {:start clock/start!}

    :worker/task-runner  {:requires {:db    :db/pouch
                                     :clock :port/clock}
                          :start    task-queue/start!
                          :stop     task-queue/stop!}

    :port/dictionary     {:requires {:db :db/sqlite}
                          :start    dictionary/start!}

    :port/progress-store {:requires {:db    :db/pouch
                                     :clock :port/clock}
                          :start    progress-store/start!}

    :port/examples       {:requires {:clock :port/clock
                                     :db    :db/pouch}
                          :start    examples/start!}

    :port/navigation     {:start navigation/start!}

    :port/collections    {:requires {:clock :port/clock
                                     :db    :db/pouch}
                          :start    collections/start!}

    :app/capabilities    {:requires {:capabilities/sync :sync/identity
                                     :collections       :port/collections
                                     :dictionary        :port/dictionary
                                     :examples          :port/examples
                                     :navigation        :port/navigation
                                     :progress-store    :port/progress-store}
                          :start    identity}

    :app/render          {:requires {:capabilities :app/capabilities
                                     :store        :app/store}
                          :start    (fn [{:keys [store] :as system}]
                                      (let [dispatch (fn [dispatch-data actions]
                                                       (nxr/dispatch system dispatch-data actions))]
                                        (nxr/register-system->state! #(-> % :store deref))
                                        (r/set-dispatch! dispatch)
                                        (add-watch store ::render #(application/render! %4))
                                        {:dispatch #(dispatch {} %)}))}

    :pwa/init            {:requires {:render :app/render}
                          :start    (fn [{:keys [render]}]
                                      (let [dispatch (:dispatch render)]
                                        (dispatch [[:effect/pwa-init]])))}

    :app/sync            {:requires {:render :app/render}
                          :start    (fn [{:keys [render]}]
                                      (let [dispatch (:dispatch render)]
                                        (js/window.addEventListener
                                         "online"
                                         #(dispatch [[:effect/sync-pull]]))))}

    :app/router          {:requires {:render :app/render}
                          :after    [:worker/service-worker
                                     :document/listeners]
                          :start    (fn [{:keys [render]}]
                                      (let [dispatch    (:dispatch render)
                                            controllers (atom nil)]
                                        (rfe/start!
                                         (rf/router
                                          (application/routes dispatch))
                                         (fn [match _history]
                                           (if match
                                             (reset! controllers (rfc/apply-controllers @controllers match))
                                             (rfe/navigate :page/home)))
                                         {:use-fragment false})))}}))


(defn ^:async ^:export start
  []
  (try
    (await (init))
    (catch js/Error err
      (log/error :main/boot-failed {:error (str err)}))))

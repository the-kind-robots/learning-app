(ns main
  (:require
   [adapters.collections :as collections-adapter]
   [adapters.examples :as examples-adapter]
   [adapters.lessons :as lessons-adapter]
   [adapters.reviews :as reviews-adapter]
   [adapters.words :as words-adapter]
   [application]
   [db.pouch :as pouch]
   [db.sqlite :as sqlite]
   [install-guide.core]
   [instrumentation :as instrumentation]
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
   [ports.backup :as backup]
   [ports.clock :as clock]
   [ports.collections :as collections]
   [ports.dictionary :as dictionary]
   [ports.examples :as examples]
   [ports.lessons :as lessons]
   [ports.navigation :as navigation]
   [ports.reviews :as reviews]
   [ports.task-queue :as task-queue]
   [ports.words :as words]
   [reitit.frontend :as rf]
   [reitit.frontend.controllers :as rfc]
   [reitit.frontend.easy :as rfe]
   [replicant.dom :as r]
   [runtime.system :as system]
   [service-worker]
   [sync]
   [tasks]
   [use-cases.examples]))


(def ^:private schemas
  "Every document type the app stores, declared by the adapter that owns it.
   The engine learns its indexes, views and routing from this list alone."
  [words-adapter/schema
   reviews-adapter/schema
   lessons-adapter/schema
   collections-adapter/schema
   examples-adapter/schema
   tasks/schema])


(defn ^:async init
  []
  (when ^boolean goog/DEBUG
    (action-log/inspect))

  (system/start!
   {:app/store             {:start (fn [_] (atom {:page/current :page/loading}))}

    ;; Ask the browser to exempt our storage (device-db, the durable home of the
    ;; account token) from automatic eviction. The auth cookie is rebuilt from
    ;; device-db each boot, so protecting device-db is what protects sign-in.
    :storage/persistent    {:start (fn [_]
                                     (when (some-> js/navigator .-storage .-persist)
                                       (.then (js/navigator.storage.persist)
                                              #(log/info :storage/persisted {:granted %})))
                                     nil)}

    ;; Before render, and it depends on nothing: registering the worker needs
    ;; no dispatch. What it hands back — the registration — is what render
    ;; carries into every effect.
    :worker/service-worker {:start service-worker/start!}

    :document/listeners    {:start
                            (fn [_]
                              (js/window.addEventListener "pageshow" application/sync-virtual-keyboard!)
                              application/sync-virtual-keyboard!)}

    :db/sqlite             {:start sqlite/init!}
    :identity/incoming     {:start sync/check-incoming-auth!}

    :db/pouch              {:after [:identity/incoming]
                            :start (fn [_] (pouch/init! schemas))}

    ;; Starting it asks for every example this device is missing and hands back
    ;; the hook the engine calls when a pass is home, knowing nothing else about
    ;; it. A component of its own, with its ports named again rather than taken
    ;; from :app/capabilities, because that one already depends on
    ;; :sync/identity — which is what needs the hook.
    :sync/identity         {:requires {:db :db/pouch}
                            :start    sync/start!
                            :stop     sync/stop!}

    :port/clock            {:start clock/start!}

    ;; After :sync/identity, not beside it: that component writes the auth
    ;; cookie, and the first task off the queue may be an example fetch, which
    ;; the backend answers 401 without it. Same layer meant that race was a
    ;; coin toss on every boot.
    :worker/task-runner    {:after    [:sync/identity]
                            :requires {:db    :db/pouch
                                       :clock :port/clock}
                            :start    task-queue/start!
                            :stop     task-queue/stop!}

    :port/dictionary       {:requires {:db :db/sqlite}
                            :start    dictionary/start!}

    :port/words            {:requires {:db    :db/pouch
                                       :clock :port/clock}
                            :start    words/start!}

    :port/reviews          {:requires {:db    :db/pouch
                                       :clock :port/clock}
                            :start    reviews/start!}

    :port/lessons          {:requires {:db    :db/pouch
                                       :clock :port/clock}
                            :start    lessons/start!}

    :port/backup           {:requires {:db :db/pouch}
                            :start    backup/start!}

    :port/examples         {:requires {:clock :port/clock
                                       :db    :db/pouch}
                            :start    examples/start!}

    :port/navigation       {:start navigation/start!}

    :port/collections      {:requires {:clock :port/clock
                                       :db    :db/pouch}
                            :start    collections/start!}

    :app/capabilities      {:requires {:capabilities/sync :sync/identity
                                       :backup            :port/backup
                                       :clock             :port/clock
                                       :collections       :port/collections
                                       :dictionary        :port/dictionary
                                       :examples          :port/examples
                                       :lessons           :port/lessons
                                       :navigation        :port/navigation
                                       :reviews           :port/reviews
                                       :words             :port/words}
                            :start    identity}

    ;; Starting it asks for every example this device is missing, and it
    ;; subscribes for what each later pass brings. The engine publishes ids
    ;; grouped by document type and interprets none of them (#432); the two
    ;; types the backfill has anything to say about are named here, by the
    ;; schemas that own them.
    :examples/backfill     {:requires {:capabilities :app/capabilities}
                            :start
                            (fn [{:keys [capabilities]}]
                              (let [listen   (get-in capabilities
                                                     [:capabilities/sync :sync/on-pass])
                                    backfill (use-cases.examples/start! capabilities)
                                    ours     (fn [pulled-ids]
                                               (into (get pulled-ids
                                                          (:type words-adapter/schema)
                                                          #{})
                                                     (get pulled-ids
                                                          (:type collections-adapter/schema)
                                                          #{})))]
                                (listen (fn [{:keys [pulled-ids]}]
                                          (backfill {:pulled-ids (ours pulled-ids)})))))}

    :app/render            {:requires {:capabilities   :app/capabilities
                                       :service-worker :worker/service-worker
                                       :store          :app/store}
                            :start    (fn [{:keys [store] :as system}]
                                        (let [dispatch (fn [dispatch-data actions]
                                                         (nxr/dispatch system dispatch-data actions))]
                                          (nxr/register-system->state! #(-> % :store deref))
                                          (r/set-dispatch! dispatch)
                                          (application/install-render!
                                           store
                                           (if ^boolean goog/DEBUG
                                             (fn [state]
                                               (instrumentation/render! application/render! state))
                                             application/render!))
                                          (when ^boolean goog/DEBUG
                                            (instrumentation/install!))
                                          {:dispatch #(dispatch {} %)}))}

    :pwa/init              {:requires {:render :app/render}
                            :start    (fn [{:keys [render]}]
                                        (let [dispatch (:dispatch render)]
                                          (dispatch [[:effect/pwa-init]])))}

    ;; The worker's other half: a waiting build becomes the state flag
    ;; «Обновить» reads. Here rather than in the worker component because it
    ;; is the part that needs dispatch, and the worker must not.
    :pwa/new-build         {:requires {:render :app/render
                                       :worker :worker/service-worker}
                            :start    service-worker/announce-new-builds!}

    :app/sync              {:requires {:capabilities :app/capabilities
                                       :render       :app/render}
                            :start    (fn [{:keys [capabilities render]}]
                                        (let [dispatch (:dispatch render)]
                                          (dispatch [[:effect/load-account]])
                                          ;; A reconnect after offline is not a
                                          ;; navigation: it passes the throttle.
                                          (js/window.addEventListener
                                           "online"
                                           #(dispatch [[:effect/sync-pull :poke]]))
                                          ;; Push channel (ADR-0009): a poke pulls
                                          ;; through the normal path. A waiting
                                          ;; pairing dialog closes only when the
                                          ;; pull brings the receipt echoing that
                                          ;; dialog's nonce.
                                          (when (get-in capabilities [:capabilities/sync :sync/account-id])
                                            (sync/connect-push!
                                             #(dispatch [[:effect/sync-pull :poke]])))))}

    :app/router            {:requires {:render :app/render}
                            :after    [:worker/service-worker
                                       :document/listeners]
                            :start    (fn [{:keys [render]}]
                                        (let [dispatch    (:dispatch render)
                                              controllers (atom nil)
                                              router      (rf/router (application/routes dispatch))]
                                          (navigation/put-home-beneath! router)
                                          (rfe/start!
                                           router
                                           (fn [match _history]
                                             (if match
                                               (reset! controllers (rfc/apply-controllers @controllers match))
                                               ;; A path with no route is a redirect, not a visit: replace the
                                               ;; entry rather than pushing one. That also drops the fragment,
                                               ;; taking an incoming credential out of the address bar.
                                               (rfe/replace-state :page/home)))
                                           ;; Reitit's anchor handler stays out: every
                                           ;; shell link takes its own click and asks
                                           ;; the navigation port (ADR-0015).
                                           {:ignore-anchor-click? (constantly false)
                                            :use-fragment false})))}}))


(defn ^:async ^:export start
  []
  (try
    (await (init))
    (catch js/Error err
      (log/error :main/boot-failed {:error (str err)}))))

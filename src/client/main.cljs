(ns main
  (:require
   [adapters.learner.documents :as documents]
   [adapters.learner.loader :as loader]
   [adapters.learner.memory :as memory]
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
   [ports.dictionary :as dictionary]
   [ports.learner :as learner]
   [ports.navigation :as navigation]
   [ports.task-queue :as task-queue]
   [reitit.frontend :as rf]
   [reitit.frontend.controllers :as rfc]
   [reitit.frontend.easy :as rfe]
   [replicant.dom :as r]
   [runtime.system :as system]
   [service-worker]
   [sync]
   [use-cases.examples]))


(defn ^:async init
  []
  ;; The action log re-renders its whole history in dataspex on every
  ;; dispatch: in a dev build it was 80-90 % of a keystroke's latency (a
  ;; keystroke measured 16-32 ms without it, 56-240 ms with it). A browser
  ;; driven by automation has no one to read it, and the browser specs measure
  ;; the app's latency, not the inspector's.
  (when (and ^boolean goog/DEBUG (not (.-webdriver js/navigator)))
    (action-log/inspect))

  (system/start!
   {:app/store             {:start (fn [_]
                                     (atom {:learner/memory memory/empty-memory
                                            :page/current   :page/loading}))}

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
                            :start (fn [_] (pouch/init!))}

    ;; The learner's data is read into memory as soon as the databases are
    ;; open: the read needs no dispatch, and the splash waits for it;
    ;; :learner/memory hands it over. The value holds, under :checked, a
    ;; promise of the snapshot check (ADR-0018), which resolves with nil. A
    ;; promise returned bare would make every later component wait for it.
    :learner/read          {:requires {:db    :db/pouch
                                       :store :app/store}
                            :start    (fn [{:keys [db store]}]
                                        {:checked (loader/start-reading! db store)})}

    ;; Starting it asks for every example this device is missing and hands back
    ;; the hook the engine calls when a pass is home, knowing nothing else about
    ;; it. A component of its own, with its ports named again rather than taken
    ;; from :app/capabilities, because that one already depends on
    ;; :sync/identity — which is what needs the hook. Its passes wait for the
    ;; snapshot check (ADR-0018); the components after it do not.
    :sync/identity         {:requires {:db   :db/pouch
                                       :read :learner/read}
                            :start    (fn [{:keys [db read]}]
                                        (sync/start! {:db db :passes-wait-for (:checked read)}))
                            :stop     sync/stop!}

    :port/clock            {:start clock/start!}

    ;; After :sync/identity, not beside it: that component writes the auth
    ;; cookie, and the first task off the queue may be an example fetch, which
    ;; the backend answers 401 without it. Same layer meant that race was a
    ;; coin toss on every boot. The runner itself starts once memory is
    ;; loaded, so that the queue's queries do not compete with the load.
    :worker/task-runner    {:after    [:sync/identity]
                            :requires {:clock   :port/clock
                                       :db      :db/pouch
                                       :learner :port/learner}
                            :start    task-queue/start!
                            :stop     task-queue/stop!}

    :port/dictionary       {:requires {:db :db/sqlite}
                            :start    dictionary/start!}

    ;; The learner's data for the use cases: memory to read, and every
    ;; write. A write changes the document PouchDB holds and catches memory
    ;; up with user-db's change log (ADR-0019).
    :port/learner          {:requires {:clock :port/clock
                                       :db    :db/pouch
                                       :store :app/store}
                            :start    learner/start!}

    :port/backup           {:requires {:db :db/pouch}
                            :start    backup/start!}

    :port/navigation       {:start navigation/start!}

    :app/capabilities      {:requires {:capabilities/sync :sync/identity
                                       :backup            :port/backup
                                       :clock             :port/clock
                                       :dictionary        :port/dictionary
                                       :learner           :port/learner
                                       :navigation        :port/navigation}
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
                                                          (:type documents/vocab-schema)
                                                          #{})
                                                     (get pulled-ids
                                                          (:type documents/collection-schema)
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
                                          (application/guard-double-clicks! store)
                                          (application/install-render!
                                           store
                                           (if ^boolean goog/DEBUG
                                             (fn [state]
                                               (instrumentation/render! application/render! state))
                                             application/render!))
                                          (when ^boolean goog/DEBUG
                                            (instrumentation/install!))
                                          {:dispatch #(dispatch {} %)}))}

    ;; The learner's data, kept in the store as a projection of the local
    ;; databases (ADR-0016). Starting returns at once. When the read
    ;; completes, memory is handed over and the screen asked for is shown.
    :learner/memory        {:after    [:learner/read]
                            :requires {:db     :db/pouch
                                       :render :app/render
                                       :store  :app/store}
                            :start    (fn [{:keys [db render store]}]
                                        (loader/start! db store (:dispatch render))
                                        nil)}

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
                                          ;; A reconnect after offline flushes
                                          ;; what was written while offline.
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

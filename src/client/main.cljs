(ns main
  (:require
   [adapters.learner.loader :as loader]
   [adapters.learner.memory :as memory]
   [application]
   [db-migrations]
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
   [ports.examples :as examples]
   [ports.learner :as learner]
   [ports.navigation :as navigation]
   [ports.page :as page]
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
                                     (atom {:learner/memory memory/empty-memory}))}

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

    ;; The once-per-device migrations that need no waiting run once the
    ;; databases are open.
    :db/background-migrations {:after [:db/pouch]
                               :start (fn [_] (db-migrations/run-in-background!))}

    ;; The learner's data is read into memory as soon as the databases are
    ;; open: the read needs no dispatch, and the splash waits for it;
    ;; :learner/memory hands it over. The value holds, under :checked, a
    ;; promise of the snapshot check (ADR-0018), which resolves with nil. A
    ;; promise returned bare would make every later component wait for it.
    :learner/read          {:requires {:db    :db/pouch
                                       :store :app/store}
                            :start    (fn [{:keys [db store]}]
                                        {:checked (loader/start-reading! db store)})}

    ;; Starting it loads the stored identity, writes the session cookie and,
    ;; when the device has an account, drives replication: it hands back the
    ;; pull a route entry or a poke asks for, and `:sync/first-pass`, which
    ;; resolves once the first pass of this start has completed. Its passes
    ;; wait for the snapshot check (ADR-0018); the components after it do
    ;; not.
    :sync/identity         {:requires {:db   :db/pouch
                                       :page :port/page
                                       :read :learner/read}
                            :start    (fn [{:keys [db page read]}]
                                        (sync/start! {:db db :page page :passes-wait-for (:checked read)}))
                            :stop     sync/stop!}

    :port/clock            {:start clock/start!}

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

    :port/examples         {:start examples/start!}

    :port/navigation       {:start navigation/start!}

    :port/page             {:start page/start!}

    :app/capabilities      {:requires {:capabilities/sync :sync/identity
                                       :backup            :port/backup
                                       :clock             :port/clock
                                       :dictionary        :port/dictionary
                                       :examples          :port/examples
                                       :learner           :port/learner
                                       :navigation        :port/navigation
                                       :page              :port/page}
                            :start    identity}

    ;; Fetches the examples memory is missing (`use-cases.examples`). Through
    ;; :app/capabilities it starts after :sync/identity, which writes the
    ;; session cookie: a request without the cookie is answered 401. A
    ;; device without an account sends nothing; one with an account sends
    ;; nothing before memory is loaded, device-db is tidied and the first
    ;; pass has completed. Its value is the function that stops it.
    :examples/fetcher      {:requires {:capabilities :app/capabilities}
                            :start    (fn [{:keys [capabilities]}]
                                        (use-cases.examples/start! capabilities))
                            :stop     (fn [stop]
                                        (stop))}

    :app/render            {:requires {:capabilities   :app/capabilities
                                       :service-worker :worker/service-worker
                                       :store          :app/store}
                            :start    (fn [{:keys [store] :as system}]
                                        (let [dispatch (fn [dispatch-data actions]
                                                         (nxr/dispatch system dispatch-data actions))]
                                          (nxr/register-system->state! #(-> % :store deref))
                                          (r/set-dispatch! dispatch)
                                          (application/guard-double-clicks! store)
                                          ;; Nothing is rendered until memory is loaded, so the
                                          ;; server's splash stays until the first screen
                                          ;; replaces it. When the start read fails, the shell
                                          ;; is rendered in its place to ask for a reload.
                                          (application/install-render!
                                           store
                                           (fn [state]
                                             (when (or (:learner/loaded? state)
                                                       (:learner/unreadable? state))
                                               (if ^boolean goog/DEBUG
                                                 (instrumentation/render! application/render! state)
                                                 (application/render! state)))))
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
                                          ((:page/on-online (:page capabilities))
                                           #(dispatch [[:effect/sync-pull :poke]]))
                                          ;; Push channel (ADR-0009): a poke pulls
                                          ;; through the normal path. A waiting
                                          ;; pairing dialog closes only when the
                                          ;; pull brings the receipt echoing that
                                          ;; dialog's nonce.
                                          (when (get-in capabilities [:capabilities/sync :sync/account-id])
                                            (sync/connect-push!
                                             (:page capabilities)
                                             #(dispatch [[:effect/sync-pull :poke]])))))}

    :app/router            {:requires {:render :app/render}
                            :after    [:worker/service-worker
                                       :document/listeners]
                            :start    (fn [{:keys [render]}]
                                        (let [dispatch    (:dispatch render)
                                              controllers (atom nil)
                                              router      (rf/router (application/routes dispatch))]
                                          ;; A query that is not valid percent-encoding makes the
                                          ;; router throw (`URIError`) and the app would not start.
                                          ;; Such a query is dropped: the address keeps its path.
                                          (try
                                            (js/decodeURIComponent js/location.search)
                                            (catch :default _
                                              (js/history.replaceState nil "" (str js/location.pathname js/location.hash))))
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

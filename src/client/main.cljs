(ns main
  (:require
   [application]
   [application.example-backfill :as example-backfill]
   [application.pages :as pages]
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
   [pages.lesson.answer-field]
   [pages.lesson.effects]
   [pages.words.actions]
   [pages.words.effects]
   [ports.account :as account]
   [ports.dictionary :as dictionary]
   [ports.examples-generator :as examples-generator]
   [ports.install :as install]
   [ports.learner :as learner]
   [ports.navigation :as navigation]
   [ports.page :as page]
   [ports.world :as world]
   [replicant.dom :as r]))


(defn ^:async init
  []
  ;; The action log re-renders its whole history on every dispatch: 80-90 % of a keystroke's latency
  ;; in a dev build. Automation has no one to read it, and the specs measure the app's latency, not the inspector's.
  (when (and ^boolean goog/DEBUG (not (.-webdriver js/navigator)))
    (action-log/inspect))

  (let [;; The state of the interface only; the learner's data is the learner's.
        app-state          (atom {})
        world              (world/start!)
        page               (page/start!)
        navigation         (navigation/start! {:home pages/home :routes pages/routes})
        ;; The service worker registers before render; the registration arrives later, as a promise.
        install            (install/start!)
        examples-generator (examples-generator/start!)
        ;; The dictionary opens SQLite while the learner opens PouchDB; neither waits for the other.
        ;; The account's sync writes the session cookie. Its `initialized` is a promise: memory loaded,
        ;; first sync pass done.
        [dictionary learner] (await (js/Promise.all #js [(dictionary/start!)
                                                         (.then (account/check-incoming-auth!) #(learner/start!))]))
        account            (await (account/start! {:learner learner :page page :world world}))
        capabilities       {:account    account
                            :dictionary dictionary
                            :examples-generator examples-generator
                            :install    install
                            :learner    learner
                            :navigation navigation
                            :page       page
                            :world      world}
        system             {:app-state    app-state
                            :capabilities capabilities}
        dispatch           (fn [dispatch-data actions]
                             (nxr/dispatch system dispatch-data actions))]
    ;; Starts after the account, whose sync wrote the session cookie: a request without it is answered 401.
    (example-backfill/start! capabilities)
    (nxr/register-system->state! application/state)
    (r/set-dispatch! dispatch)
    (application/start-rendering! system dispatch)
    (install/listen-for-install!
     install
     #(swap! app-state assoc :pwa/install-available? true)
     #(swap! app-state assoc :pwa/install-available? false))
    (swap! app-state assoc :app/has-account? (account/exists? account))
    ;; After render, so the first route has a screen to compute. Starting the router reads the address,
    ;; which `ports.account/check-incoming-auth!` has already handled.
    (navigation/follow! navigation
                        (fn [entered]
                          (let [current-page (pages/table entered)]
                            ;; The keyboard's mode belongs to the screen, so it is set once, as the screen is entered.
                            (page/overlay-keyboard! page (:keyboard-overlays? current-page))
                            (dispatch {}
                                      [[:effect/enter (:open current-page)]
                                       [:effect/after-paint [[:effect/sync]]]]))))))


(defn ^:async ^:export start
  []
  (try
    (await (init))
    (catch js/Error err
      (log/error :main/boot-failed {:error (str err)}))))

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
   [pages.collections.arrange-panel]
   [pages.collections.effects]
   [pages.collections.view]
   [pages.home.actions]
   [pages.home.effects]
   [pages.home.view]
   [pages.lesson.actions]
   [pages.lesson.answer-field]
   [pages.lesson.effects]
   [pages.lesson.page-panel]
   [pages.lesson.page-strip]
   [pages.lesson.view]
   [pages.stats.actions]
   [pages.stats.effects]
   [pages.stats.view]
   [pages.words.actions]
   [pages.words.effects]
   [pages.words.view]
   [ports.account :as account]
   [ports.dictionary :as dictionary]
   [ports.examples-generator :as examples-generator]
   [ports.install :as install]
   [ports.learner :as learner]
   [ports.navigation :as navigation]
   [ports.page :as page]
   [ports.sharing :as sharing]
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
        navigation         (navigation/start! {:home pages/home :paths (pages/paths)})
        ;; The service worker registers before render; the registration arrives later, as a promise.
        ;; Listened to before the first await: Chrome offers installing right after the page's load,
        ;; while the learner's data is still opening, and an offer nobody hears is gone (#814).
        install            (doto (install/start!)
                             (install/listen-for-install!
                              #(swap! app-state assoc :pwa/install-available? true)
                              #(swap! app-state assoc :pwa/install-available? false)))
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
                            :sharing    (sharing/start!)
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
    (account/on-status account #(swap! app-state assoc :sync/status %))
    ;; After render, so the first window has a screen to compute. Following the history reads the address,
    ;; which `ports.account/check-incoming-auth!` has already handled.
    (navigation/follow! navigation #(dispatch {} [[:action/enter-windows %]]))))


(defn ^:async ^:export start
  []
  (try
    (await (init))
    (catch js/Error err
      (log/error :main/boot-failed {:error (str err)}))))

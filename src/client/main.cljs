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
   [ports.browser :as browser]
   [ports.dictionary :as dictionary]
   [ports.examples-generator :as examples-generator]
   [ports.learner :as learner]
   [ports.world :as world]
   [replicant.dom :as r]
   [runtime.system :as system]))


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
   {;; The state of the interface only; the learner's data is the learner's.
    :app/state             {:start (fn [_]
                                     (atom {}))}

    :port/world            {:start world/start!}

    ;; The page, navigation, installing the app and the service worker, which
    ;; is registered before render: it depends on nothing, and what it hands
    ;; back, the registration, arrives later as a promise.
    :port/browser          {:start #(browser/start! {:routes pages/routes})}

    ;; The dictionary opens its SQLite first.
    :port/dictionary       {:start dictionary/start!}

    :port/examples-generator {:start examples-generator/start!}

    ;; The learner's data: the incoming credential, the databases, reading
    ;; memory, sync and following the databases, in the order the learner
    ;; adapter starts them. It returns at once, with what it
    ;; waits for as promises in the capabilities: `:learner/initialized`
    ;; resolves once memory is loaded and the first pass of sync is done. Its
    ;; value is `{:capabilities :stops}`.
    :port/learner          {:requires {:browser :port/browser
                                       :world   :port/world}
                            :start    learner/start!
                            :stop     learner/stop!}

    :app/capabilities      {:requires {:browser            :port/browser
                                       :dictionary         :port/dictionary
                                       :examples-generator :port/examples-generator
                                       :learner            :port/learner
                                       :world              :port/world}
                            :start    (fn [ports]
                                        (update ports :learner :capabilities))}

    ;; Fetches the examples memory is missing (`use-cases.examples`). Through
    ;; :app/capabilities it starts after the learner, whose sync writes the
    ;; session cookie: a request without the cookie is answered 401. A device without
    ;; an account sends nothing; one with an account sends nothing before
    ;; the learner is initialized: memory loaded, the first pass done.
    ;; Its value is the function that stops it.
    :examples/backfill     {:requires {:capabilities :app/capabilities}
                            :start    (fn [{:keys [capabilities]}]
                                        (example-backfill/start! capabilities))
                            :stop     (fn [stop]
                                        (stop))}

    :app/render            {:requires {:app-state    :app/state
                                       :capabilities :app/capabilities}
                            :start    (fn [system]
                                        (let [dispatch (fn [dispatch-data actions]
                                                         (nxr/dispatch system dispatch-data actions))]
                                          (nxr/register-system->state! application/state)
                                          (r/set-dispatch! dispatch)
                                          (application/start-rendering! system dispatch)
                                          {:dispatch #(dispatch {} %)}))}

    :pwa/init              {:requires {:render :app/render}
                            :start    (fn [{:keys [render]}]
                                        (let [dispatch (:dispatch render)]
                                          (dispatch [[:effect/pwa-init]])))}

    :app/sync              {:requires {:render :app/render}
                            :start    (fn [{:keys [render]}]
                                        ((:dispatch render) [[:effect/load-account]]))}

    ;; The pages entered, from the address, from the first match on: each is a
    ;; route entry of the application.
    :app/router            {:requires {:render :app/render}
                            :after    [:port/browser]
                            :start    (fn [{:keys [render]}]
                                        ((:dispatch render) [[:effect/follow-routes]]))}}))


(defn ^:async ^:export start
  []
  (try
    (await (init))
    (catch js/Error err
      (log/error :main/boot-failed {:error (str err)}))))

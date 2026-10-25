(ns application
  (:require
   [application.pages :as pages]
   [application.presenter :as presenter]
   [application.settings.actions]
   [application.settings.effects]
   [application.settings.presenter :as settings-presenter]
   [application.sync.actions]
   [application.sync.effects]
   [application.view :as view]
   [i18n :as i18n]
   [install-guide.core]
   [instrumentation :as instrumentation]
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [ports.account :as account]
   [ports.learner :as learner]
   [ports.navigation :as navigation]
   [ports.page :as page]
   [ports.world :as world]
   [replicant.dom :as r]
   [view-transition]))


;;
;; Interceptors
;;


(nxr/register-interceptor!
  :before-effect
  (fn [{:keys [system] :as ctx}]
    (assoc ctx :capabilities (:capabilities system))))


;;
;; Effects
;;


(nxr/register-effect! :effect/save
  (fn save [_ system m]
    (swap! (:app-state system) merge m)))


(nxr/register-effect! :effect/log-warning
  (fn log-warning [_ _ id data]
    (log/warn id data)))


(nxr/register-effect! :effect/navigate
  (fn navigate-effect [{:keys [capabilities]} _ page]
    (navigation/navigate (:navigation capabilities) page)))


(nxr/register-effect! :effect/sync
  ;; The result reaches the screen through memory's change feed; nothing is reloaded here.
  (fn sync [{:keys [capabilities]} _ & [reason]]
    (account/sync! (:account capabilities) reason)
    nil))


(nxr/register-effect! :effect/enter
  ;; Hands a page's action what state cannot give it, in the task of the entry.
  (fn enter [{:keys [capabilities dispatch]} _ action & args]
    (dispatch [(into [action
                      ;; Marked skip-interpolation: Nexus would otherwise walk every word id of a theme
                      ;; for placeholders on each keystroke.
                      (with-meta {:active-collection (learner/active-collection (:learner capabilities))
                                  :learner (:learner capabilities)
                                  :now-ms  (world/clock (:world capabilities) :now-ms)
                                  :random  #(world/random (:world capabilities))}
                                 {:nexus/skip-interpolation true})]
                     args)])))


(nxr/register-effect! :effect/after-paint
  ;; Work the screen does not show goes after its paint.
  (fn after-paint [{:keys [dispatch]} _ actions]
    (js/requestAnimationFrame
     (fn [_]
       (js/setTimeout #(dispatch actions) 0)))))


;;
;; The learner's data in memory (ADR-0016). A screen reads memory when it is entered
;; and does not follow it while open; `start-rendering!` refreshes once, when the load arrives.
;;


(nxr/register-action! :action/refresh-page
  ;; The screen on display computed again from memory, after the load and after the reader's own change.
  (fn refresh-page [state context]
    (when-let [refresh (:refresh (pages/table (:page/current state)))]
      (refresh state context))))


(nxr/register-effect! :effect/announce
  ;; A live region speaks only when its text changes: the message goes into an emptied
  ;; region, so a repeated one is spoken again.
  (fn announce [_ _ region-id message]
    (when-let [region (js/document.getElementById region-id)]
      (set! (.-textContent region) "")
      ;; After the frame that renders the emptied region, so the accessibility tree sees it empty first.
      (js/setTimeout #(set! (.-textContent region) message) 100))))


(nxr/register-effect! :effect/show-modal
  (fn show-modal [{:keys [dispatch-data]} _]
    (.showModal (:replicant/node dispatch-data))))


(nxr/register-effect! :effect/close-modal
  (fn close-modal [{:keys [dispatch-data]} _]
    (.close (:replicant/node dispatch-data))))


(nxr/register-effect! :effect/focus-child
  (fn focus-child [{:keys [dispatch-data]} _ selector]
    (some-> (.querySelector (:replicant/node dispatch-data) selector) .focus)))


(nxr/register-effect! :effect/mobile-autofocus
  (fn focus-child-pointer-fine [_ _ element-id]
    (when (.. js/window (matchMedia "(pointer:fine)") -matches)
      (some-> (js/document.getElementById element-id) .focus))))


(nxr/register-effect! :effect/focus
  (fn focus [_ _ element-id]
    (some-> (js/document.getElementById element-id) .focus)))


(nxr/register-effect! :effect/cursor-to-end
  (fn cursor-to-end [{:keys [dispatch-data]} _]
    (let [node (:replicant/node dispatch-data)
          len  (.-length (.-value node))]
      (.focus node)
      (.setSelectionRange node len len))))


(nxr/register-effect! :effect/autogrow-target
  ;; Safari lacks `field-sizing: content`: collapse, then measure (`scrollHeight` omits borders).
  (fn autogrow-target [{:keys [dispatch-data]} _]
    (let [node (some-> dispatch-data :replicant/dom-event .-target)]
      ;; Asked by CSS property name: `fieldSizing` is missing from Closure's externs, so the
      ;; dotted accessor would not survive :advanced.
      (when (and node (not= "content" (.getPropertyValue (js/getComputedStyle node) "field-sizing")))
        (set! (.. node -style -height) "auto")
        (let [borders (- (.-offsetHeight node) (.-clientHeight node))]
          (set! (.. node -style -height) (str (+ (.-scrollHeight node) borders) "px")))))))


(nxr/register-effect! :effect/clear-autogrow
  ;; The measured height is an inline style; nothing else clears it when the field empties.
  (fn clear-autogrow [_ _ element-id]
    (some-> (js/document.getElementById element-id) .-style (.removeProperty "height"))))


(nxr/register-effect! :effect/select-all
  (fn select-all [{:keys [dispatch-data]} _]
    (some-> dispatch-data :replicant/node .select)))


(nxr/register-effect! :effect/blur-target
  (fn blur-target [{:keys [dispatch-data]} _]
    (some-> dispatch-data :replicant/dom-event .-target .blur)))


(nxr/register-effect! :effect/set-target-text
  (fn set-target-text [{:keys [dispatch-data]} _ text]
    (when-let [target (some-> dispatch-data :replicant/dom-event .-target)]
      (set! (.-textContent target) (or text "")))))


(nxr/register-effect! :effect/prevent-default
  (fn prevent-default [{:keys [dispatch-data]} _]
    (some-> dispatch-data :replicant/dom-event .preventDefault)))


(nxr/register-effect! :effect/stop-propagation
  (fn stop-propagation [{:keys [dispatch-data]} _]
    (some-> dispatch-data :replicant/dom-event .stopPropagation)))


(nxr/register-effect! :effect/request-submit
  (fn request-submit [{:keys [dispatch-data]} _]
    (some-> dispatch-data :replicant/dom-event .-target .-form .requestSubmit)))


(nxr/register-effect! :effect/scroll-nearest
  (fn scroll-nearest [_ _ selector]
    (some-> (js/document.querySelector selector)
            (.scrollIntoView #js {:block "nearest"}))))


;;
;; Actions
;;


(nxr/register-action! :action/open-dialog
  (fn open-dialog [_]
    [[:effect/show-modal]]))


(nxr/register-action! :action/dismiss-on-backdrop
  (fn dismiss-on-backdrop [_ backdrop?]
    ;; A native modal's backdrop is part of the dialog element: a click on the element itself came from outside the content.
    (when backdrop?
      [[:effect/close-modal]])))


(nxr/register-action! :action/move-cursor-to-end
  (fn move-cursor-to-end [_]
    [[:effect/cursor-to-end]]))


(nxr/register-action! :action/select-all
  (fn select-all [_]
    [[:effect/select-all]]))


(nxr/register-action! :action/go-to-collections
  (fn go-to-collections [_]
    [[:effect/navigate :page/collections]]))


;; What leaving does to a screen's work belongs to its route; the lesson ends on its `:stop`.
(nxr/register-action! :action/close-screen
  (fn close-screen [_]
    [[:action/go-to-home]]))


;; The lesson has none: `Escape` would end it.
(nxr/register-action! :action/press-hotkey
  (fn press-hotkey [state keystroke]
    (when-let [actions (some-> (pages/table (:page/current state)) :hotkeys (apply [state]) (get keystroke))]
      (into [[:effect/prevent-default]] actions))))


;;
;; Placeholder
;;


(nxr/register-placeholder! :event.target/value
  (fn [dispatch-data]
    (some-> (:replicant/dom-event dispatch-data) .-target .-value)))


(nxr/register-placeholder! :event.target/text-content
  (fn [dispatch-data]
    (some-> (:replicant/dom-event dispatch-data) .-target .-textContent)))


(nxr/register-placeholder! :event.form.field/value
  (fn [dispatch-data field-name]
    (some-> (:replicant/dom-event dispatch-data) .-target .-elements (.namedItem field-name) .-value)))


(nxr/register-placeholder! :event.keyboard/key
  (fn [dispatch-data]
    (some-> (:replicant/dom-event dispatch-data) .-key)))


(nxr/register-placeholder! :event.keyboard/keystroke
  (fn [dispatch-data]
    (when-let [^js event (:replicant/dom-event dispatch-data)]
      (str (when (.-ctrlKey event) "Ctrl+")
           (when (.-altKey event) "Alt+")
           (when (.-shiftKey event) "Shift+")
           (when (.-metaKey event) "Meta+")
           (.-code event)))))


(nxr/register-placeholder! :event.keyboard/ctrl?
  (fn [dispatch-data]
    (some-> (:replicant/dom-event dispatch-data) .-ctrlKey)))


(nxr/register-placeholder! :event.keyboard/meta?
  (fn [dispatch-data]
    (some-> (:replicant/dom-event dispatch-data) .-metaKey)))


(nxr/register-placeholder! :event.keyboard/shift?
  (fn [dispatch-data]
    (some-> (:replicant/dom-event dispatch-data) .-shiftKey)))


(nxr/register-placeholder! :event/self-click?
  (fn [dispatch-data]
    (let [e (:replicant/dom-event dispatch-data)]
      (= (.-target e) (.-currentTarget e)))))


;; `Cmd` counts: it is the Mac modifier. The default is suppressed so a browser that submits
;; on `Ctrl`+`Enter` does not submit twice.
(nxr/register-action! :action/submit-if-ctrl-enter
  (fn submit-if-ctrl-enter [_ {:keys [key ctrl? meta?]}]
    (when (and (= "Enter" key) (or ctrl? meta?))
      [[:effect/prevent-default]
       [:effect/request-submit]])))


;;
;; Render
;;


(defn render!
  "Renders the shell of `after`, moving the way the screen change from `before` (nil: none) calls for."
  [before after]
  (let [language (settings-presenter/language after)]
    (set! (.-lang js/document.documentElement) language)
    (view-transition/change! (presenter/screen-change before after)
                             #(r/render js/document.body (i18n/interpolate language (view/shell after))))))


(defn state
  "The state the screens are computed from: the interface's own, in `:app-state` of `system`, with whether the
   learner's data is loading, loaded or unreadable (`:learner/status`), the language the learner picked
   (`:learner/language`) and the system's (`:system/language`). The data itself is read from the learner, which
   `:effect/enter` hands to the actions."
  [{:keys [app-state capabilities]}]
  (let [{:keys [learner page]} capabilities]
    (assoc @app-state
           :learner/language (learner/language learner)
           :learner/status   (learner/status learner)
           :system/language  (page/system-language page))))


(defn start-render!
  "Renders now and on every change of the interface's state or the learner's data,
   only when it differs (`identical?`): a dispatch that saves nothing costs no render.
   `render-fn` gets the state before the change, nil for the learner's data, and the state now."
  [{:keys [app-state capabilities] :as system} render-fn]
  (let [{:keys [learner]} capabilities
        render! (fn [before] (render-fn before (state system)))]
    (add-watch app-state
               ::render
               (fn [_ _ old new]
                 (when-not (identical? old new)
                   (render! (assoc old :learner/status (learner/status learner))))))
    (learner/on-change learner #(render! nil))
    (render! nil)))


(def ^:private fonts-cap-ms
  "How long after navigation the splash waits for fonts at most, so a failed
   fetch cannot hold the start."
  1000)


(defn fonts-settled
  "A promise that resolves once the regular and the bold Nunito are loaded
   for both scripts, or after `fonts-cap-ms`, whichever is first. Never
   rejects."
  []
  (let [loads (for [weight ["400" "700"]]
                (js/document.fonts.load (str weight " 1rem Nunito") "Аa"))
        cap   (js/Promise. (fn [resolve _]
                             (js/setTimeout resolve (max 0 (- fonts-cap-ms (js/performance.now))))))]
    (js/Promise.race #js [(.catch (js/Promise.all (clj->js loads)) (fn [_] nil))
                          cap])))


(defn guard-double-clicks!
  "A double click is one activation: the second and later clicks of a series
   (`detail` above 1) do nothing, wherever they land. A click from the keyboard
   has `detail` 0 and passes."
  []
  (js/window.addEventListener
   "click"
   (fn [^js event]
     (when (> (.-detail event) 1)
       (.stopPropagation event)
       (.preventDefault event)))
   #js {:capture true}))


;; The keystroke's target is `body` when nothing has focus; the page table says what it does on the screen shown.
(defn listen-for-hotkeys!
  "Hands every keydown on the document to `:action/press-hotkey`."
  [dispatch]
  (js/document.addEventListener
   "keydown"
   (fn [event]
     (dispatch {:replicant/dom-event event}
               [[:action/press-hotkey [:event.keyboard/keystroke]]]))))


(defn start-rendering!
  "Makes the app show: guards double clicks, listens for the hotkeys,
   and renders the state of `system` (`state`), now and on every change to it,
   once memory is loaded."
  [{:keys [capabilities] :as system} dispatch]
  (guard-double-clicks!)
  (listen-for-hotkeys! dispatch)
  ;; The load renders the slice the route entry computed from empty memory, then the refresh
  ;; renders from memory in the same task, so only the second paints over the splash.
  ;; The refresh is a microtask: a watch that saved would show the render watch the load's
  ;; state after the refresh's.
  (let [{:keys [learner]} capabilities
        refresh!          #(dispatch {} [[:effect/enter :action/refresh-page]])
        was-loaded?       (volatile! (= :status/loaded (learner/status learner)))]
    (learner/on-change learner
                       (fn []
                         (let [now-loaded? (= :status/loaded (learner/status learner))]
                           (when (and now-loaded? (not @was-loaded?))
                             (js/queueMicrotask refresh!))
                           (vreset! was-loaded? now-loaded?))))
    (when @was-loaded?
      (refresh!)))
  ;; Nothing renders before memory is loaded, so the server's splash stays; a failed read renders
  ;; the shell to ask for a reload. The first screen waits for the fonts, so they are there from its first paint.
  (.then (fonts-settled)
         (fn [_]
           (start-render!
            system
            (fn [before after]
              (when (not= :status/loading (:learner/status after))
                (if ^boolean goog/DEBUG
                  (instrumentation/render! #(render! before %) after)
                  (render! before after)))))))
  (when ^boolean goog/DEBUG
    (instrumentation/install!)))

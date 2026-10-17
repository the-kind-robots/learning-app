(ns application
  (:require
   [application.pages :as pages]
   [application.sync.actions]
   [application.sync.effects]
   [application.view :as view]
   [install-guide.core]
   [instrumentation :as instrumentation]
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [replicant.dom :as r]))


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
  (fn navigate-effect [{:keys [capabilities dispatch]} _ page]
    (when-let [navigate! (get-in capabilities [:browser :browser/navigate])]
      (navigate! page #(dispatch [[:effect/enter :action/open-home]])))))


(nxr/register-effect! :effect/sync
  ;; What a sync brings reaches the screen through memory, which follows the
  ;; local change feed; the sync itself reloads nothing.
  (fn sync [{:keys [capabilities]} _ & [reason]]
    (when-let [sync! (get-in capabilities [:learner :learner/sync!])]
      (sync! reason)
      nil)))


(nxr/register-effect! :effect/enter
  ;; A page's action, handed what it cannot read from state — then `args` —
  ;; and dispatched in the same task: a route's entry, a query, a page more.
  (fn enter [{:keys [capabilities dispatch]} _ action & args]
    (dispatch [(into [action
                      ;; What a page's content needs besides app state: the
                      ;; active collection, or nil for «Всё подряд», the learner
                      ;; port, whose reads of memory a page uses, the time
                      ;; retention is measured at, and the source of randomness.
                      ;; The context is marked so that Nexus hands it over as it
                      ;; is: the collection carries every word id of a theme, and
                      ;; Nexus would otherwise walk them all for placeholders on
                      ;; every keystroke.
                      (with-meta {:active-collection ((get-in capabilities [:learner :learner/active-collection]))
                                  :learner           (:learner capabilities)
                                  :now-ms            ((get-in capabilities [:world :world/clock :clock/now-ms]))
                                  :random            (get-in capabilities [:world :world/random])}
                                 {:nexus/skip-interpolation true})]
                     args)])))


(nxr/register-effect! :effect/after-paint
  ;; Runs `actions` once the frame the current task is building has been
  ;; painted: work the screen does not show goes after the screen.
  (fn after-paint [{:keys [dispatch]} _ actions]
    (js/requestAnimationFrame
     (fn [_]
       (js/setTimeout #(dispatch actions) 0)))))


;;
;; The learner's data in memory (ADR-0016). The learner reads the databases
;; into memory it owns, then follows their change feeds and writes each change
;; into memory itself. A screen reads memory when it is entered, and does not
;; follow memory while it is open; `start-rendering!` refreshes the screen
;; once, when the load arrives.
;;


(nxr/register-action! :action/refresh-page
  ;; The screen on display computed again from memory: after the load, and
  ;; after the reader's own change on it.
  (fn refresh-page [state context]
    (when-let [refresh (:refresh (pages/table (:page/current state)))]
      (refresh state context))))


(nxr/register-effect! :effect/announce
  ;; A live region speaks when its text changes: the message is written into
  ;; an emptied region, so the same one twice in a row is spoken twice. The
  ;; region is named by its id, which the control that started the action
  ;; put into its payload.
  (fn announce [_ _ region-id message]
    (when-let [region (js/document.getElementById region-id)]
      (set! (.-textContent region) "")
      ;; Past the frame that renders the emptied region, so the accessibility
      ;; tree sees it empty before the message and speaks a repeated one again.
      (js/setTimeout #(set! (.-textContent region) message) 100))))


(nxr/register-effect! :effect/load-account
  (fn load-account [{:keys [capabilities dispatch]} _]
    (dispatch
     [[:effect/save
       {:app/has-account? (get-in capabilities [:learner :learner/has-account?])}]])))


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
  ;; Fits a textarea's height to its content: collapse, then measure. The
  ;; fallback for Safari, which lacks `field-sizing: content` — where the
  ;; property works, measuring only overrides a height the browser got right.
  ;; `scrollHeight` omits the borders a border-box height must carry.
  (fn autogrow-target [{:keys [dispatch-data]} _]
    (let [node (some-> dispatch-data :replicant/dom-event .-target)]
      ;; The browser sizing it itself is asked by the CSS property name:
      ;; `fieldSizing` is missing from Closure's externs, so the dotted
      ;; accessor survives :advanced only as long as extern inference keeps
      ;; resolving it. A string never has to be resolved.
      (when (and node (not= "content" (.getPropertyValue (js/getComputedStyle node) "field-sizing")))
        (set! (.. node -style -height) "auto")
        (let [borders (- (.-offsetHeight node) (.-clientHeight node))]
          (set! (.. node -style -height) (str (+ (.-scrollHeight node) borders) "px")))))))


(nxr/register-effect! :effect/clear-autogrow
  ;; Hands the height back to CSS. The measured height is an inline style, so
  ;; nothing else clears it: an emptied field would keep the height its former
  ;; content earned.
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
    ;; A native modal renders its backdrop as part of the dialog element, so a
    ;; click that lands on the element itself came from outside the content.
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


;; The corner ✕ closes whichever screen is on display. What leaving does to a
;; screen's own work belongs to its route: the lesson ends on its `:stop`.
(nxr/register-action! :action/close-screen
  (fn close-screen [_]
    [[:action/go-to-home]]))


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


(nxr/register-placeholder! :event.keyboard/ctrl?
  (fn [dispatch-data]
    (some-> (:replicant/dom-event dispatch-data) .-ctrlKey)))


(nxr/register-placeholder! :event.keyboard/meta?
  (fn [dispatch-data]
    (some-> (:replicant/dom-event dispatch-data) .-metaKey)))


(nxr/register-placeholder! :event.keyboard/shift?
  (fn [dispatch-data]
    (some-> (:replicant/dom-event dispatch-data) .-shiftKey)))


(nxr/register-placeholder! :event.keyboard/alt?
  (fn [dispatch-data]
    (some-> (:replicant/dom-event dispatch-data) .-altKey)))


(nxr/register-placeholder! :event/self-click?
  (fn [dispatch-data]
    (let [e (:replicant/dom-event dispatch-data)]
      (= (.-target e) (.-currentTarget e)))))


;; The modifier is what tells a submit from a newline in a multi-line field, so
;; `Cmd` counts too — it is the one a Mac keyboard offers. The default is
;; suppressed because a browser that submits on `Ctrl`+`Enter` by itself would
;; submit the form twice.
(nxr/register-action! :action/submit-if-ctrl-enter
  (fn submit-if-ctrl-enter [_ {:keys [key ctrl? meta?]}]
    (when (and (= "Enter" key) (or ctrl? meta?))
      [[:effect/prevent-default]
       [:effect/request-submit]])))


;;
;; Render
;;


(defn render!
  "Renders the shell of `state`, then lets the browser's virtual keyboard
   follow what the screen asks of it (`sync-virtual-keyboard!`, the browser
   capability's)."
  [state sync-virtual-keyboard!]
  (r/render js/document.body (view/shell state))
  (sync-virtual-keyboard!))


(defn state
  "The state the screens are computed from: the interface's own, in
   `:app-state` of `system`, with the learner's data in memory and whether it
   is loaded or unreadable."
  [{:keys [app-state capabilities]}]
  (let [{:learner/keys [loaded? memory unreadable?]} (:learner capabilities)]
    (assoc @app-state
           :learner/loaded?     (loaded?)
           :learner/memory      (memory)
           :learner/unreadable? (unreadable?))))


(defn start-render!
  "Renders the state now and again on every change to the interface's state or
   the learner's data, and only on change. `identical?` is enough: CLJS
   `assoc`/`merge` hand back the same map when nothing differs, so a dispatch
   that saves nothing costs no render. A dispatch that saves several times
   renders several times; the owner chose this simplicity over a coalescing
   counter (#213)."
  [{:keys [app-state capabilities] :as system} render-fn]
  (let [render! #(render-fn (state system))]
    (add-watch app-state
               ::render
               (fn [_ _ old new]
                 (when-not (identical? old new)
                   (render!))))
    ((get-in capabilities [:learner :learner/on-change]) render!)
    (render!)))


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


;; `Alt`+`Enter` starts the lesson from home even when nothing has focus, and
;; then the keystroke's target is `body`, above the page. The action itself
;; decides whether the screen is home.
(defn listen-for-lesson-hotkey!
  "Hands every keydown on the document to `:action/start-lesson-if-alt-enter`."
  [dispatch]
  (js/document.addEventListener
   "keydown"
   (fn [event]
     (dispatch {:replicant/dom-event event}
               [[:action/start-lesson-if-alt-enter
                 {:alt? [:event.keyboard/alt?]
                  :key  [:event.keyboard/key]}]]))))


(nxr/register-effect! :effect/follow-routes
  ;; A page is entered: its screen computed from memory in the task that
  ;; changed the route, the pull after the paint. The page the address matched
  ;; before this ran is entered at once.
  (fn follow-routes [{:keys [capabilities dispatch]} _]
    ((get-in capabilities [:browser :browser/on-route])
     (fn [page]
       (dispatch [[:effect/enter (:open (pages/table page))]
                  [:effect/after-paint [[:effect/sync]]]])))))


(defn start-rendering!
  "Makes the app show: guards double clicks, listens for the lesson hotkey,
   and renders the state of `system` (`state`), now and on every change to it,
   once memory is loaded."
  [{:keys [capabilities] :as system} dispatch]
  (guard-double-clicks!)
  (listen-for-lesson-hotkey! dispatch)
  ;; Everything user-db held at start arrives in memory in one write.
  ;; Nothing is rendered before it, so the server's splash is still on
  ;; display. The write renders the screen from the slice its route entry
  ;; computed from empty memory, and the refresh renders it again from memory.
  ;; Both renders run in this task, so only the second is painted, in place of
  ;; the splash. The refresh is a microtask of the same task: a watch that
  ;; saves would have the render watch see the load's state after the
  ;; refresh's. A load that came before this ran is refreshed at once.
  (let [{:learner/keys [loaded? on-change]} (:learner capabilities)
        refresh!                            #(dispatch {} [[:effect/enter :action/refresh-page]])
        was-loaded?                         (volatile! (loaded?))]
    (on-change (fn []
                 (let [now-loaded? (loaded?)]
                   (when (and now-loaded? (not @was-loaded?))
                     (js/queueMicrotask refresh!))
                   (vreset! was-loaded? now-loaded?))))
    (when @was-loaded?
      (refresh!)))
  ;; Nothing is rendered until memory is loaded, so the
  ;; server's splash stays until the first screen
  ;; replaces it. When the start read fails, the shell
  ;; is rendered in its place to ask for a reload.
  ;; The first screen replaces the splash, so it
  ;; waits for the fonts: they are on the screen
  ;; from its first paint, not swapped in after it.
  (.then (fonts-settled)
         (fn [_]
           (start-render!
            system
            (fn [state]
              (when (or (:learner/loaded? state)
                        (:learner/unreadable? state))
                (let [render-state! #(render! % (get-in capabilities [:browser :browser/sync-virtual-keyboard!]))]
                  (if ^boolean goog/DEBUG
                    (instrumentation/render! render-state! state)
                    (render-state! state))))))))
  (when ^boolean goog/DEBUG
    (instrumentation/install!)))

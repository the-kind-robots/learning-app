(ns pages.collections.effects
  (:require
   [clojure.string :as str]
   [instrumentation :as instrumentation]
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [use-cases.collections :as collections]
   [view-transition]))


(def ^:private long-press-delay-ms 500)


;;
;; What a tap on a card decides, without the DOM.
;; Chrome cancels a touch it hands to the scroller, more readily while the main thread is busy,
;; and then no `click` follows. It cancels as the scroll begins (on Android before any `pointermove`),
;; so the cancel says nothing; the gesture is judged at the lift: no farther than a tap and nothing
;; scrolled is a tap. A gesture fires at most once, so a `click` after a recovered tap is a no-op.
;;


(def max-move-px
  "Farther than this since pointerdown is a drag, not a tap; the same limit
   makes the long press give up."
  10)


(def max-scroll-px
  "A touch that scrolled the page more than this was a scroll."
  2)


(defn recoverable?
  "True when a cancelled touch travelled at most `max-move-px` and the page
   scrolled at most `max-scroll-px` between pointerdown and the lift. A
   gesture with no move seen has travelled 0."
  [{:keys [moved-px scroll-delta]}]
  (and (<= (or moved-px 0) max-move-px)
       (<= (js/Math.abs (or scroll-delta 0)) max-scroll-px)))


(defn on-lift
  "The gesture, when the finger of a cancelled touch lifts, that counts as
   its tap, `:fired?` set — or nil when it does not: the gesture already
   fired, the finger moved, or the page scrolled."
  [gesture]
  (when (and (not (:fired? gesture)) (recoverable? gesture))
    (assoc gesture :fired? true)))


(defn- scroll-top
  []
  (or (some-> js/document .-scrollingElement .-scrollTop) 0))


(defn- swallow-next-click!
  "Once a gesture has fired its click has nothing left to do (Chrome may still deliver one after a
   cancelled touch or a long press), so the next click on this card within a second stops at the window."
  [card-id]
  (let [listener (js/AbortController.)]
    (js/window.addEventListener
     "click"
     (fn [^js click]
       (when (= card-id (some-> ^js (.-target click)
                                (.closest "[data-collection-id]")
                                (.getAttribute "data-collection-id")))
         (.abort listener)
         (.stopPropagation click)
         (.preventDefault click)))
     #js {:capture true :signal (.-signal listener)})
    (js/setTimeout #(.abort listener) 1000)))


(defn- track-gesture!
  "Follows one pointer from its pointerdown on `card-id`: a cancelled touch is followed through its touch
   events and a tap recovered at the lift (`tap-actions`); `on-long-press` fires after `long-press-delay-ms`.
   A fired gesture swallows the click that may follow."
  [{:keys [dispatch dispatch-data]} card-id tap-actions on-long-press]
  (let [event     (:replicant/dom-event dispatch-data)
        start-x   (.-clientX event)
        start-y   (.-clientY event)
        start-st  (scroll-top)
        gesture   (volatile! {:moved-px 0})
        listeners (js/AbortController.)
        timer     (when on-long-press
                    (js/setTimeout
                     (fn []
                       (vswap! gesture assoc :fired? true)
                       (swallow-next-click! card-id)
                       (on-long-press))
                     long-press-delay-ms))
        finish    (fn []
                    (js/clearTimeout timer)
                    (.abort listeners))
        listen    (fn [type f]
                    (js/window.addEventListener type f #js {:passive true :signal (.-signal listeners)}))
        moved-to  (fn [^js at]
                    (let [dist (js/Math.hypot (- (.-clientX at) start-x)
                                              (- (.-clientY at) start-y))]
                      (vswap! gesture update :moved-px max dist)
                      (when (> dist max-move-px)
                        (js/clearTimeout timer))))
        touch     (fn [^js e] (aget (.-changedTouches e) 0))]
    (listen "pointermove" moved-to)
    (listen "pointerup" (fn [_] (finish)))
    ;; Only a touch goes on after its cancel.
    (listen "pointercancel"
            (fn [^js e]
              (if (= "touch" (.-pointerType e))
                (vswap! gesture assoc :cancelled? true)
                (finish))))
    (listen "touchmove" (fn [e] (moved-to (touch e))))
    (listen "touchcancel" (fn [_] (finish)))
    (listen "touchend"
            (fn [e]
              ;; A touch that was not cancelled ended at its pointerup, which already aborted this listener.
              (when (:cancelled? @gesture)
                (moved-to (touch e))
                (finish)
                (when-let [tap (on-lift (assoc @gesture :scroll-delta (- (scroll-top) start-st)))]
                  (vreset! gesture tap)
                  (when ^boolean goog/DEBUG
                    (instrumentation/trace! "tap-recovered"
                                            (clj->js {:movedPx (:moved-px tap) :scrollDelta (:scroll-delta tap)})))
                  (swallow-next-click! card-id)
                  (dispatch tap-actions)))))))


(nxr/register-effect! :effect/begin-long-press
  (fn begin-long-press [{:keys [dispatch] :as ctx} _ coll-id tap-actions]
    (track-gesture! ctx
                    coll-id
                    tap-actions
                    (fn []
                      (dispatch [[:effect/save {:collections/editing-id coll-id}]])))))


(nxr/register-effect! :effect/begin-tap
  (fn begin-tap [ctx _ card-id tap-actions]
    (track-gesture! ctx card-id tap-actions nil)))


(nxr/register-effect! :effect/prompt-create-collection
  (fn ^:async prompt-create-collection
    [{{:keys [learner world]} :capabilities :keys [dispatch]} _ message]
    (try
      (when-let [raw-name (js/prompt message)]
        (await (collections/create! learner world raw-name))
        (dispatch [[:effect/enter :action/refresh-page]]))
      (catch js/Error err
        (log/error :effect/prompt-create-collection {:error (str err)})))))


(defn neighbour
  "The target that takes focus once `deleted` is gone, from the targets' ids
   in keyboard order: the one after it, else the one before. A deleted
   folder parent leaves a label, and its first row follows it."
  [ids deleted]
  (let [[before [_ & after]] (split-with #(not= deleted %) ids)]
    (or (first after) (last before))))


(nxr/register-effect! :effect/delete-collection
  (fn ^:async delete-collection!
    [{{:keys [learner]} :capabilities :keys [dispatch]} _ {:keys [deleted-message id status-region]}]
    ;; Picked before the delete: afterwards the deleted target is gone.
    (let [focus-id (neighbour (->> (js/Array.from (js/document.querySelectorAll ".masonry [data-collection-id]"))
                                   (map #(.getAttribute ^js % "data-collection-id")))
                              id)]
      (try
        ;; The tiles without it are shown once memory took the deletion, so the transition runs around both.
        (await (view-transition/change!
                :transition/close-gap
                (fn ^:async delete-and-show! []
                  (await (collections/delete! learner id))
                  (dispatch [[:effect/enter :action/show-deleted
                              {:deleted-message deleted-message
                               :focus-id        focus-id
                               :status-region   status-region}]]))))
        (catch js/Error err
          (log/error :effect/delete-collection {:error (str err)}))))))


(nxr/register-effect! :effect/focus-collection
  ;; The render after a save is synchronous, so the target is on the page.
  (fn focus-collection [_ _ id]
    (some-> (js/document.querySelector
             (str ".masonry [data-collection-id=\"" (js/CSS.escape id) "\"]"))
            .focus)))


(def ^:private typing-listener
  "Removes the keydown listener of the visit on display."
  (volatile! nil))


(nxr/register-effect! :effect/listen-for-typing
  ;; On the document: when the screen opens nothing has focus, and a keystroke reaches no tile.
  ;; A pause over a second starts a new search. A space counts only inside a search, so on its own it still
  ;; presses the focused target.
  (fn listen-for-typing [_ _]
    (let [listener (js/AbortController.)
          typed    (volatile! {:text "" :at 0})]
      (vreset! typing-listener listener)
      (js/document.addEventListener
       "keydown"
       (fn [^js event]
         (let [character (.-key event)
               now       (js/Date.now)
               text      (if (< (- now (:at @typed)) 1000) (:text @typed) "")]
           (when (and (= 1 (count character))
                      (not (or (.-ctrlKey event) (.-altKey event) (.-metaKey event)))
                      (not (and (= " " character) (= "" text))))
             (let [text (str text character)]
               (vreset! typed {:text text :at now})
               (.preventDefault event)
               (some-> (->> (js/document.querySelectorAll ".masonry [data-collection-id]")
                            (js/Array.from)
                            (filter #(str/includes? (str/lower-case (.-textContent
                                                                     (.querySelector % ".tile__name, .tile__row-name")))
                                                    (str/lower-case text)))
                            first)
                       .focus)))))
       #js {:signal (.-signal listener)}))))


(nxr/register-effect! :effect/stop-listening-for-typing
  (fn stop-listening-for-typing [_ _]
    (.abort @typing-listener)))


(nxr/register-effect! :effect/switch-active-collection
  (fn switch-active-collection
    [{{:keys [learner]} :capabilities :keys [dispatch]} _ collection-id]
    (collections/switch-active! learner collection-id)
    (dispatch [[:action/go-to-home]])))

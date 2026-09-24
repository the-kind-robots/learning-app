(ns pages.collections.effects
  (:require
   [instrumentation :as instrumentation]
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [use-cases.collections :as collections]))


(def ^:private long-press-delay-ms 500)


;;
;; What a tap on a card decides, without the DOM.
;;
;; Chrome cancels a touch it hands to the scroller — more readily while the
;; main thread is busy — and then no `click` follows. It cancels as the
;; scroll begins: before the page has moved, and on Android before any
;; `pointermove`, since moves inside the touch slop are never sent. So the
;; cancel itself says nothing; the touch events keep coming after it, and
;; the gesture is judged when the finger lifts. A lift that travelled no
;; farther than a tap and scrolled nothing was a tap, and is taken as one. A
;; gesture fires at most once: a `click` that Chrome delivers after a
;; recovered tap is a no-op.
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


(defn- trace!
  [kind data]
  (when ^boolean goog/DEBUG
    (instrumentation/trace! kind (clj->js data))))


(defn- card-of
  [^js node]
  (some-> node (.closest "[data-collection-id]") (.getAttribute "data-collection-id")))


(defn- swallow-next-click!
  "Once a gesture has fired there is nothing left for its click to do — the
   click Chrome may still deliver after a cancelled touch, or after a long
   press — so the next click on this card, within a second, stops at the
   window."
  [card-id]
  (let [listener (js/AbortController.)]
    (js/window.addEventListener
     "click"
     (fn [^js click]
       (when (= card-id (card-of (.-target click)))
         (.abort listener)
         (.stopPropagation click)
         (.preventDefault click)))
     #js {:capture true :signal (.-signal listener)})
    (js/setTimeout #(.abort listener) 1000)))


(defn- track-gesture!
  "Follows one pointer from its pointerdown on `card-id`: how far it moved,
   whether the page scrolled, and whether the gesture already fired. A
   touch the browser cancels is followed on through its touch events, and
   when the finger lifts a tap is recovered by dispatching `tap-actions`.
   `on-long-press`, when given, fires after `long-press-delay-ms` unless the
   finger moved or lifted first, before or after a cancel. Either way a
   fired gesture swallows the click that may still follow, so it fires
   once. One closure per gesture: its window listeners hang off one
   AbortController, so the gesture ends with one abort."
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
    ;; Only a touch goes on after its cancel; any other pointer ends here.
    (listen "pointercancel"
            (fn [^js e]
              (if (= "touch" (.-pointerType e))
                (vswap! gesture assoc :cancelled? true)
                (finish))))
    (listen "touchmove" (fn [e] (moved-to (touch e))))
    (listen "touchcancel" (fn [_] (finish)))
    (listen "touchend"
            (fn [e]
              ;; A touch that was not cancelled ended at its pointerup,
              ;; which already aborted this listener.
              (when (:cancelled? @gesture)
                (moved-to (touch e))
                (finish)
                (when-let [tap (on-lift (assoc @gesture :scroll-delta (- (scroll-top) start-st)))]
                  (vreset! gesture tap)
                  (trace! "tap-recovered" {:movedPx (:moved-px tap) :scrollDelta (:scroll-delta tap)})
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
  ;; «Всё подряд» has no long press; it gets the same tap recovery.
  (fn begin-tap [ctx _ card-id tap-actions]
    (track-gesture! ctx card-id tap-actions nil)))


(nxr/register-effect! :effect/exit-editing-on-background
  (fn exit-editing-on-background [{:keys [dispatch dispatch-data]} system _]
    (let [event (:replicant/dom-event dispatch-data)]
      (when (and (= (.-target event) (.-currentTarget event))
                 (:collections/editing-id @(:store system)))
        (dispatch [[:effect/save {:collections/editing-id nil}]])))))


(nxr/register-effect! :effect/load-collections
  (fn ^:async load-collections
    [{:keys [capabilities dispatch]} _]
    (try
      (let [data (await (collections/summary capabilities))]
        (dispatch [[:action/show-collections data]]))
      (catch js/Error err
        (log/error :effect/load-collections {:error (str err)})
        ;; The screen shows what it has (its empty state at worst), not
        ;; «Загружаем…» forever.
        (dispatch [[:effect/save {:collections/loading? false}]])))))


(nxr/register-effect! :effect/prompt-create-collection
  (fn ^:async prompt-create-collection
    [{:keys [capabilities dispatch]} _]
    (try
      (when-let [raw-name (js/prompt "Название нового набора:")]
        (let [{:keys [ok]} (await (collections/create! capabilities raw-name))]
          (when ok
            (dispatch [[:effect/load-collections]]))))
      (catch js/Error err
        (log/error :effect/prompt-create-collection {:error (str err)})))))


(defn neighbour
  "The target that takes focus once `deleted` is gone, from the targets' ids
   in keyboard order: the one after it, else the one before. A deleted
   folder parent leaves a label, and its first row follows it."
  [ids deleted]
  (let [[before [_ & after]] (split-with #(not= deleted %) ids)]
    (or (first after) (last before))))


(defn- target-ids
  "The ids of the themes screen's targets, in document order."
  []
  (->> (js/Array.from (js/document.querySelectorAll ".masonry [data-collection-id]"))
       (map #(.getAttribute ^js % "data-collection-id"))))


(nxr/register-effect! :effect/delete-collection
  (fn ^:async delete-collection!
    [{:keys [capabilities dispatch]} _ {:keys [id name]}]
    ;; Picked before the delete: afterwards the deleted target is gone.
    (let [focus-id (neighbour (target-ids) id)]
      (try
        (await (collections/delete! capabilities id))
        (let [data (await (collections/summary capabilities))]
          (dispatch [[:action/show-deleted data {:name name :focus-id focus-id}]]))
        (catch js/Error err
          (log/error :effect/delete-collection {:error (str err)})
          (dispatch [[:effect/load-collections]]))))))


(nxr/register-effect! :effect/focus-collection
  ;; The render after a save is synchronous, so the target is on the page.
  (fn focus-collection [_ _ id]
    (some-> (js/document.querySelector
             (str ".masonry [data-collection-id=\"" (js/CSS.escape id) "\"]"))
            .focus)))


(nxr/register-effect! :effect/switch-active-collection
  (fn switch-active-collection
    [{:keys [capabilities dispatch]} _ collection-id]
    (collections/switch-active! capabilities collection-id)
    (dispatch [[:action/go-to-home]])))

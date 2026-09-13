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
;; main thread is busy — and then no `click` follows. A cancel during which
;; the finger did not move and the page did not scroll was still a tap, and
;; is taken as one. A gesture fires at most once: a `click` that Chrome
;; delivers after a recovered cancel is a no-op.
;;


(def max-move-px
  "Farther than this since pointerdown is a drag, not a tap; the same limit
   makes the long press give up."
  10)


(def max-scroll-px
  "A cancel that scrolled the page more than this was a scroll."
  2)


(defn recoverable?
  "True when a cancelled pointer travelled at most `max-move-px` and the page
   scrolled at most `max-scroll-px` since pointerdown. A gesture with no move
   seen has travelled 0."
  [{:keys [moved-px scroll-delta]}]
  (and (<= (or moved-px 0) max-move-px)
       (<= (js/Math.abs (or scroll-delta 0)) max-scroll-px)))


(defn on-cancel
  "The gesture after a pointercancel that counts as its tap, `:fired?` set —
   or nil when it does not: the gesture already fired, the pointer moved,
   or the page scrolled."
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
   whether the page scrolled, and whether the gesture already fired. On
   pointercancel a tap the browser took without movement is recovered by
   dispatching `tap-actions`. `on-long-press`, when given, fires after
   `long-press-delay-ms` unless the pointer moved or lifted first. Either
   way a fired gesture swallows the click that may still follow, so it
   fires once. One closure per gesture: its window listeners hang off one
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
                    (js/window.addEventListener type f #js {:signal (.-signal listeners)}))]
    (listen "pointermove"
            (fn [e]
              (let [dist (js/Math.hypot (- (.-clientX e) start-x)
                                        (- (.-clientY e) start-y))]
                (vswap! gesture update :moved-px max dist)
                (when (> dist max-move-px)
                  (js/clearTimeout timer)))))
    (listen "pointerup" (fn [_] (finish)))
    (listen "pointercancel"
            (fn [_]
              (finish)
              (when-let [tap (on-cancel (assoc @gesture :scroll-delta (- (scroll-top) start-st)))]
                (vreset! gesture tap)
                (trace! "tap-recovered" {:movedPx (:moved-px tap) :scrollDelta (:scroll-delta tap)})
                (swallow-next-click! card-id)
                (dispatch tap-actions))))))


(nxr/register-effect! :effect/begin-long-press
  (fn begin-long-press [{:keys [dispatch] :as ctx} _ coll-id]
    (track-gesture! ctx
                    coll-id
                    [[:action/handle-tab-click coll-id]]
                    (fn []
                      (dispatch [[:effect/save {:collections/editing-id coll-id}]])))))


(nxr/register-effect! :effect/begin-tap
  ;; The main card has no long press; it gets the same tap recovery.
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


(nxr/register-effect! :effect/delete-collection
  (fn ^:async delete-collection!
    [{:keys [capabilities dispatch]} _ {:keys [id]}]
    (try
      (await (collections/delete! capabilities id))
      (catch js/Error err
        (log/error :effect/delete-collection {:error (str err)}))
      (finally
       (dispatch [[:effect/load-collections]])))))


(nxr/register-effect! :effect/switch-active-collection
  (fn switch-active-collection
    [{:keys [capabilities dispatch]} _ collection-id]
    (collections/switch-active! capabilities collection-id)
    (dispatch [[:action/go-to-home]])))

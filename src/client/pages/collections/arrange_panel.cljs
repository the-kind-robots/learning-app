(ns pages.collections.arrange-panel
  (:require
   [shadow.cljs.modern :as modern]))


;; The arrange panel: a custom element, `<arrange-panel>`, whose children are dragged into a new order. It knows
;; nothing of the app state, Nexus or the page's CSS. A child with a `sort-key` can be dragged; one without
;; stays where it stands, and a child let go beside it lands after it. A mouse drags a child once it travels
;; past `drag-threshold-px`; a touch drags only a child marked `armed`, which the page sets when that child
;; may be dragged, and then the page does not scroll under that touch from its first move.
;;
;; While a child is carried it has `lifted` and follows the pointer by a transform, its place left empty; the
;; child it would land by has `caret`, `before` or `after`. A pointer held near the top or bottom of the area
;; the element scrolls in, its nearest scrolling ancestor, scrolls that area. On release the element fires `arrange` with `detail.order`, the `sort-key`s in the
;; new order, and moves nothing itself: the page puts the children in that order, and the element slides
;; them from where they stood to where they are then. A drag swallows the click that follows it.
;;
;; Properties the element keeps: `drag` (the AbortController of the pointer it follows).


(def ^:private drag-threshold-px
  "A pointer that travelled no farther than this was a tap."
  10)


(def ^:private edge-px
  "How near the top or bottom of the scrolling area a pointer scrolls it."
  56)


(def ^:private edge-scroll-px
  "How far a frame scrolls the area with the pointer at the very edge."
  14)


(def ^:private slide-ms 200)


(def ^:private arrange-wait-ms
  "How long the carried child stays where it was let go, waiting for the page to put it in its place."
  1000)


(def ^:private reduced-motion
  (delay (js/matchMedia "(prefers-reduced-motion: reduce)")))


(defn- child-of
  "The child of `el` that holds `node`, or nil."
  [^js el ^js node]
  (loop [^js node node]
    (cond
      (nil? node) nil
      (identical? el (.-parentNode node)) node
      :else (recur (.-parentNode node)))))


(defn- scroller
  "The nearest ancestor of `el` that scrolls, or the document's scrolling element."
  [^js el]
  (or (->> (iterate (fn [^js node] (.-parentElement node)) (.-parentElement el))
           (take-while some?)
           (filter (fn [^js node] (#{"auto" "scroll"} (.-overflowY (js/getComputedStyle node)))))
           first)
      (.-scrollingElement js/document)))


(defn- swallow-next-click!
  "The click a drag ends with is no tap: the next click inside `el` within a second stops there."
  [^js el]
  (let [listener (js/AbortController.)]
    (.addEventListener el
                       "click"
                       (fn [^js click]
                         (.abort listener)
                         (.stopPropagation click)
                         (.preventDefault click))
                       #js {:capture true :signal (.-signal listener)})
    (js/setTimeout #(.abort listener) 1000)))


(defn- slide-in!
  "Once the page has put the children in their new order, slides each from its rect in `before`, a Map of
   child to rect, to where it is now, and lets `carried` down. Without a change within `arrange-wait-ms`
   it only lets `carried` down."
  [^js el before ^js carried]
  (let [settled? (volatile! false)
        settle!  (fn []
                   (vreset! settled? true)
                   (.removeAttribute carried "lifted")
                   (set! (.. carried -style -transform) ""))
        observer (js/MutationObserver.
                  (fn [_ ^js observer]
                    (.disconnect observer)
                    (settle!)
                    (when-not (.-matches @reduced-motion)
                      (doseq [^js child (array-seq (.-children el))
                              :let      [^js was (.get before child)]
                              :when     was
                              :let      [is (.getBoundingClientRect child)
                                         dx (- (.-left was) (.-left is))
                                         dy (- (.-top was) (.-top is))]
                              :when     (or (> (js/Math.abs dx) 0.5) (> (js/Math.abs dy) 0.5))]
                        (.animate child
                                  #js [#js {:transform (str "translate(" dx "px, " dy "px)") :zIndex 1}
                                       #js {:transform "none" :zIndex 1}]
                                  #js {:duration slide-ms :easing "cubic-bezier(.2, .8, .2, 1)"})))))]
    (.observe observer el #js {:childList true})
    (js/setTimeout (fn []
                     (when-not @settled?
                       (.disconnect observer)
                       (settle!)))
                   arrange-wait-ms)))


(defn- lift!
  "Lifts `child` to follow the pointer that went down at (`start-x`, `start-y`). Returns the drag: `:follow`
   moves it to a point in the window, `:let-go` fires `arrange` when the caret stands where `child` does not,
   `:put-back` returns it to its place and fires nothing. The caret stands by the child under the pointer, or
   the nearest one between children."
  [^js el ^js child start-x start-y]
  (let [^js area    (scroller el)
        start-st    (.-scrollTop area)
        pointer     (volatile! [start-x start-y])
        frame       (volatile! nil)
        children    (array-seq (.-children el))
        others      (mapv (fn [^js other]
                            (let [rect (.getBoundingClientRect other)]
                              {:child  other
                               :left   (.-left rect)
                               :top    (.-top rect)
                               :width  (.-width rect)
                               :height (.-height rect)}))
                          (remove #(identical? child %) children))
        target      (volatile! nil)
        hide-caret! (fn []
                      (when-let [^js caret-child (first @target)]
                        (.removeAttribute caret-child "caret")))
        ;; The rects were read at the lift; the pointer is put where it is in the page as it was then.
        place!      (fn []
                      (let [[x window-y] @pointer
                            y            (+ window-y (- (.-scrollTop area) start-st))
                            centre-y     (fn [{:keys [top height]}] (+ top (/ height 2)))
                            ;; A centre nearer than the one under the pointer can stand in the next column.
                            nearest      (or (some (fn [{:keys [left top width height] :as other}]
                                                     (when (and (<= left x (+ left width)) (<= top y (+ top height)))
                                                       other))
                                                   others)
                                             (apply min-key
                                                    (fn [{:keys [left width] :as other}]
                                                      (js/Math.hypot (- x (+ left (/ width 2))) (- y (centre-y other))))
                                                    others))
                            ^js beside   (:child nearest)
                            side         (if (and (< y (centre-y nearest)) (.hasAttribute beside "sort-key"))
                                           :before
                                           :after)]
                        (set! (.. child -style -transform)
                              (str "translate(" (- x start-x) "px, " (- y start-y) "px) scale(1.04)"))
                        (when (not= [beside side] @target)
                          (hide-caret!)
                          (.setAttribute beside "caret" (name side))
                          (vreset! target [beside side]))))
        ;; Up is negative; nothing away from the edges of the area seen, the scroller's rect within the window.
        edge-scroll (fn []
                      (let [window-y (second @pointer)
                            rect     (.getBoundingClientRect area)
                            top      (+ (max 0 (.-top rect)) edge-px)
                            bottom   (- (min js/window.innerHeight (.-bottom rect)) edge-px)]
                        (cond
                          (< window-y top)    (- (* edge-scroll-px (min 1 (/ (- top window-y) edge-px))))
                          (> window-y bottom) (* edge-scroll-px (min 1 (/ (- window-y bottom) edge-px)))
                          :else               0)))
        scroll!     (fn scroll! []
                      (let [step (edge-scroll)]
                        (if (zero? step)
                          (vreset! frame nil)
                          (do (.scrollBy area 0 step)
                              (place!)
                              (vreset! frame (js/requestAnimationFrame scroll!))))))
        put-back!   (fn []
                      (js/cancelAnimationFrame @frame)
                      (hide-caret!)
                      (.removeAttribute child "lifted")
                      (set! (.. child -style -transform) ""))]
    (.setAttribute child "lifted" "")
    {:follow   (fn [x y]
                 (vreset! pointer [x y])
                 (place!)
                 (when (and (nil? @frame) (not (zero? (edge-scroll))))
                   (vreset! frame (js/requestAnimationFrame scroll!))))
     :put-back put-back!
     ;; The click a release makes is no tap, however long the drag took.
     :let-go   (fn []
                 (swallow-next-click! el)
                 (let [order (when-let [[beside side] @target]
                               (let [[before after] (->> children
                                                         (remove #(identical? child %))
                                                         (split-with #(not (identical? beside %))))
                                     cut (if (= :after side) 1 0)]
                                 (concat before (take cut after) [child] (drop cut after))))]
                   (if (or (nil? order) (= order children))
                     (put-back!)
                     (let [before (js/Map.)]
                       (js/cancelAnimationFrame @frame)
                       (hide-caret!)
                       (doseq [^js each children]
                         (.set before each (.getBoundingClientRect each)))
                       (slide-in! el before child)
                       (.dispatchEvent el
                                       (js/CustomEvent.
                                        "arrange"
                                        #js {:bubbles true
                                             :detail  #js {:order (->> order
                                                                       (keep #(.getAttribute ^js % "sort-key"))
                                                                       into-array)}}))))))}))


(defn- follow!
  "Follows the pointer that went down on `child`: past the threshold, a mouse or an `armed` child is lifted
   and carried until the pointer goes up."
  [^js el ^js child ^js down]
  (let [start-x    (.-clientX down)
        start-y    (.-clientY down)
        mouse?     (= "mouse" (.-pointerType down))
        drag       (volatile! nil)
        controller (js/AbortController.)
        listen     (fn [type f]
                     (js/window.addEventListener type f #js {:signal (.-signal controller)}))
        stop!      (fn []
                     (.abort controller)
                     (set! (.-drag el) nil))]
    (set! (.-drag el) controller)
    (listen "pointermove"
            (fn [^js move]
              (when (and (nil? @drag)
                         (> (js/Math.hypot (- (.-clientX move) start-x) (- (.-clientY move) start-y))
                            drag-threshold-px)
                         (or mouse? (.hasAttribute child "armed")))
                (vreset! drag (lift! el child start-x start-y)))
              (when-let [{:keys [follow]} @drag]
                (follow (.-clientX move) (.-clientY move)))))
    (listen "pointerup"
            (fn [_]
              (stop!)
              (when-let [{:keys [let-go]} @drag]
                (let-go))))
    ;; A pointer the browser took back: a touch it scrolls, or one the system claims. A carried child goes
    ;; back to its place, and the order stays.
    (listen "pointercancel"
            (fn [_]
              (stop!)
              (when-let [{:keys [put-back]} @drag]
                (put-back))))))


(defn- listen!
  "The element's own listeners, for its lifetime."
  [^js el]
  (.addEventListener el
                     "pointerdown"
                     (fn [^js event]
                       (let [child (child-of el (.-target event))]
                         ;; The main button only: a right press opens a menu, a middle one scrolls.
                         (when (and child
                                    (.-isPrimary event)
                                    (zero? (.-button event))
                                    (nil? (.-drag el))
                                    (.hasAttribute child "sort-key"))
                           (follow! el child event)))))
  ;; Chrome starts a scroll from moves shorter than a drag and then lets nothing cancel it, so a touch on an
  ;; armed child has every move cancelled from the first. Only a listener there when the touch starts may
  ;; cancel its moves, so it stays for the element's lifetime.
  (.addEventListener el
                     "touchmove"
                     (fn [^js event]
                       (when (some-> (child-of el (.-target event)) (.hasAttribute "armed"))
                         (.preventDefault event)))
                     #js {:passive false}))


;; Registered when this namespace loads, once even if it loads again.


(when-not (js/customElements.get "arrange-panel")
  (modern/defclass ArrangePanel
    (extends js/HTMLElement)

    (constructor [this]
                 (set! (.-drag this) nil)
                 (listen! this)))

  (js/customElements.define "arrange-panel" ArrangePanel))

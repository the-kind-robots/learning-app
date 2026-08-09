(ns pages.lesson.popover
  "Anchored popover for the lesson token-info card — port of the pre-Replicant
   popover.js. The HTML Popover API supplies light-dismiss and top-layer
   rendering; positioning against the anchor rect and the auto-close timers
   live here. Replicant owns the popover contents; this namespace only moves
   and shows/hides the shell element.")


(def ^:private pad 8)


(def ^:private offset 10)


(def ^:private idle-open-ms 3000)


(def ^:private idle-leave-ms 600)


(defonce ^:private state (atom {:anchor nil :on-closed nil :timer nil}))


(defn- popover-el
  []
  (js/document.getElementById "popover"))


(defn- content-el
  []
  (js/document.getElementById "popover-content"))


(defn- arrow-el
  []
  (js/document.getElementById "popover-arrow"))


(defn- open?
  [el]
  (.matches el ":popover-open"))


(defn- position!
  []
  (let [pop    (popover-el)
        arrow  (arrow-el)
        anchor (:anchor @state)]
    (when (and pop arrow anchor (open? pop))
      (let [rect     (.getBoundingClientRect anchor)
            pw       (.-offsetWidth pop)
            ph       (.-offsetHeight pop)
            center-x (+ (.-left rect) (/ (.-width rect) 2))
            left     (max pad
                          (min (- js/window.innerWidth pw pad)
                               (- center-x (/ pw 2))))
            above    (- (.-top rect) ph offset)
            flip?    (and (< above pad)
                          (<= (+ (.-bottom rect) ph offset pad)
                              js/window.innerHeight))
            top      (if flip? (+ (.-bottom rect) offset) above)]
        (set! (.. pop -style -left) (str left "px"))
        (set! (.. pop -style -top) (str top "px"))
        (.setAttribute pop "data-side" (if flip? "bottom" "top"))
        (set! (.. arrow -style -left)
              (str (max 12 (min (- pw 12) (- center-x left))) "px"))))))


(defn- active?
  []
  (when-let [pop (popover-el)]
    (or (.matches pop ":hover")
        (.contains pop js/document.activeElement))))


(defn- clear-auto-timer!
  []
  (when-let [timer (:timer @state)]
    (js/clearTimeout timer)
    (swap! state assoc :timer nil)))


(defn- schedule-auto-close!
  [ms]
  (clear-auto-timer!)
  (when-let [pop (and (pos? (or ms 0)) (popover-el))]
    (swap! state assoc
      :timer
      (js/setTimeout #(when (and (open? pop) (not (active?)))
                        (.hidePopover pop))
                     ms))))


(defn- initial-close-ms
  []
  (let [card   (some-> (content-el) .-firstElementChild)
        forced (some-> card (.getAttribute "data-dismiss") js/parseInt)]
    (cond
      (and forced (pos? forced)) forced
      (.-matches (js/window.matchMedia "(hover: hover)")) idle-open-ms
      :else 0)))


(defn- wire-listeners!
  "One-time listeners per shell element. Replicant may recreate the element
   between opens; the data flag keeps a reused element from double-wiring."
  [pop]
  (when-not (.getAttribute pop "data-wired")
    (.setAttribute pop "data-wired" "true")
    (.addEventListener pop
                       "toggle"
                       (fn [event]
                         (when (= "closed" (.-newState event))
                           (clear-auto-timer!)
                           (let [on-closed (:on-closed @state)]
                             (swap! state assoc :anchor nil :on-closed nil)
                             (when on-closed (on-closed))))))
    (.addEventListener pop "pointerenter" (fn [_] (clear-auto-timer!)))
    (.addEventListener pop
                       "pointerleave"
                       (fn [_] (schedule-auto-close! idle-leave-ms)))
    (.addEventListener pop "focusin" (fn [_] (clear-auto-timer!)))
    (.addEventListener pop
                       "focusout"
                       (fn [_]
                         (js/setTimeout
                          #(when-not (active?)
                             (schedule-auto-close! idle-leave-ms))
                          0)))))


(defn- open!
  "Shows the popover anchored to `anchor`, or re-anchors and repositions an
   already-open one. `on-closed` runs once when the popover closes for any
   reason (light-dismiss, Escape, auto-close)."
  [anchor on-closed]
  (when-let [pop (popover-el)]
    (wire-listeners! pop)
    (swap! state assoc :anchor anchor :on-closed on-closed)
    (when-not (open? pop)
      (.showPopover pop))
    ;; Positioning reads the popover's rendered size (offsetWidth/Height), and
    ;; this runs from a Replicant life-cycle hook — the card's content may not
    ;; be laid out yet. One frame later the size is real and reading it does
    ;; not force a mid-patch reflow.
    (js/requestAnimationFrame
     (fn []
       (position!)
       (schedule-auto-close! (if (active?) 0 (initial-close-ms)))))))


(defn open-for-word!
  "Shows the popover anchored to the answer token carrying `word-index`.
   Owns the markup details (token selector) so effects stay markup-free.
   Calls `on-closed` immediately when no such token is rendered."
  [word-index on-closed]
  (if-let [anchor (js/document.querySelector
                   (str ".lesson__answer-token[data-word-index=\"" word-index "\"]"))]
    (open! anchor on-closed)
    (on-closed)))


(defonce window-listeners
  (do (js/window.addEventListener "resize" position! #js {:passive true})
      (js/window.addEventListener "scroll" position! #js {:passive true :capture true})
      true))

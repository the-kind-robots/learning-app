(ns pages.collections.tap
  "What a tap on a card decides, without the DOM.

   Chrome cancels a touch it hands to the scroller — more readily while the
   main thread is busy — and then no `click` follows. A cancel during which
   the finger did not move and the page did not scroll was still a tap, and
   is taken as one. A gesture fires at most once: a `click` that Chrome
   delivers after a recovered cancel is a no-op.")


(def max-move-px
  "Farther than this since pointerdown is a drag, not a tap. The same limit
   the long press uses to give up."
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
  "The gesture after a pointercancel: `:dispatch?` says whether the tap's
   action fires now, and `:fired?` remembers that it did."
  [gesture]
  (let [fire? (and (not (:fired? gesture)) (recoverable? gesture))]
    (assoc gesture :dispatch? fire? :fired? (or (:fired? gesture) fire?))))


(defn on-click
  "The gesture after a click: fires unless the gesture already fired."
  [gesture]
  (let [fire? (not (:fired? gesture))]
    (assoc gesture :dispatch? fire? :fired? true)))

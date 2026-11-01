(ns domain.retention
  "The memory of a word: how likely the learner recalls it, from its reviews. Nothing here knows of lessons.

   A word's review history is four columns, one entry per review: `reviewed-at`, when it was made, in whole
   seconds; `retained`, 1 or 0; `answer-ms`, how long the answer took, NaN when unknown; `review-ids`. The
   entries are ordered by time and, within one second, by review id, so the order does not depend on the
   order the reviews arrived in. `log-recall` walks the columns: a lesson ranks every word in scope on the
   tap.

   Why columns, and why a new history per review (ADR-0016): a lesson walks every word's history on the tap,
   and typed arrays keep that walk fast and the memory snapshot small. Memory must stay a value — an older
   memory never sees a later review — so a history is never changed in place. A typed array has a fixed
   length, so a review in is a copy of each column one slot longer."
  (:require
   [goog.array :as garray]
   [utils :as utils]))


(defrecord Reviews [reviewed-at retained answer-ms review-ids])


(def empty-reviews
  (->Reviews (js/Float64Array. 0) (js/Uint8Array. 0) (js/Float64Array. 0) #js []))


(defn- later?
  "Whether the review at `position` of `history` comes after a review made at `reviewed-at` (whole seconds)
   with the id `review-id`. Within one second the ids decide, the same on every device."
  [^Reviews history position reviewed-at review-id]
  (let [made-at (aget (.-reviewed-at history) position)]
    (or (> made-at reviewed-at)
        (and (== made-at reviewed-at)
             (pos? (garray/defaultCompare (aget (.-review-ids history) position) review-id))))))


(defn with-review
  "`history` and `review` — `{:id :created-at :retained :answer-ms}`, `:answer-ms` optional — as one history,
   the review in its place in time order. `history` is left as it was. Nearly always that place is the end."
  [^Reviews history {:keys [answer-ms created-at id retained]}]
  (let [reviewed-at (utils/iso->secs created-at)
        size        (.-length (.-review-ids history))
        ;; After every review made before this one: walk back from the end past the later ones.
        insert-position (loop [position size]
                          (if (and (pos? position) (later? history (dec position) reviewed-at id))
                            (recur (dec position))
                            position))
        ;; `column` copied into `longer`, a slot longer, `value` in the slot at the insert position.
        inserted    (fn [^js column ^js longer value]
                      (.set longer (.subarray column 0 insert-position))
                      (.set longer (.subarray column insert-position) (inc insert-position))
                      (aset longer insert-position value)
                      longer)]
    (->Reviews (inserted (.-reviewed-at history) (js/Float64Array. (inc size)) reviewed-at)
               (inserted (.-retained history) (js/Uint8Array. (inc size)) (if retained 1 0))
               (inserted (.-answer-ms history) (js/Float64Array. (inc size)) (or answer-ms ##NaN))
               (doto (.slice (.-review-ids history)) (.splice insert-position 0 id)))))


(defn log-recall
  "The natural log of the probability that the learner recalls the word at `now-ms`, from 0 (certain) down;
   nil for a word with no reviews, whose memory is unknown. A log, so that words long unreviewed, whose
   probability underflows to zero (3.8 days at the initial rate), still compare.

   The forgetting rate starts at 0.00231 per second. Each retained review divides it by one plus the rate
   times the interval; each lapse doubles it. The probability is e to the minus rate times the time since the
   last review; a clock moved backwards reads as certain."
  [^Reviews history now-ms]
  (let [size (if history (.-length (.-review-ids history)) 0)]
    (when (pos? size)
      (let [reviewed-at (.-reviewed-at history)
            retained    (.-retained history)
            rate        (loop [position 1
                               rate     0.00231]
                          (if (< position size)
                            (recur (inc position)
                                   (if (== 1 (aget retained position))
                                     (/ rate
                                        (inc (* rate
                                                (- (aget reviewed-at position) (aget reviewed-at (dec position))))))
                                     (* 2 rate)))
                            rate))]
        (min 0 (- (* rate (utils/ms->secs (- now-ms (* 1000 (aget reviewed-at (dec size))))))))))))

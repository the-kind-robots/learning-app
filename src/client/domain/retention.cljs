(ns domain.retention
  "How due a word is, from its reviews.

   A word's review history is three columns — when each review was made, in
   seconds, whether it was retained, and its id — ordered by time and,
   within one second, by id, so the result does not depend on the order the
   reviews arrived in. A history is never changed: a review in or out gives
   a new one. `urgency` walks the columns: a lesson ranks every word in
   scope on the tap."
  (:require
   [clojure.math :as math]
   [goog.array :as garray]
   [utils :as utils]))


(defrecord Reviews [seconds retained ids])


(def empty-reviews
  (->Reviews (js/Float64Array. 0) (js/Uint8Array. 0) #js []))


(defn- after?
  "Whether the review at `index` of `history` comes after one made at
   `seconds` with `id`."
  [^Reviews history index seconds id]
  (let [made (aget (.-seconds history) index)]
    (or (> made seconds)
        (and (== made seconds) (pos? (garray/defaultCompare (aget (.-ids history) index) id))))))


(defn with-review
  "Returns `history` with `review` — `{:id :created-at :retained}` — in its
   place in time order. Nearly always that place is the end."
  [^Reviews history {:keys [created-at id retained]}]
  (let [seconds (utils/iso->secs created-at)
        size    (.-length (.-ids history))
        index   (loop [index size]
                  (if (and (pos? index) (after? history (dec index) seconds id))
                    (recur (dec index))
                    index))
        times   (js/Float64Array. (inc size))
        flags   (js/Uint8Array. (inc size))]
    (.set times (.subarray (.-seconds history) 0 index))
    (.set times (.subarray (.-seconds history) index) (inc index))
    (aset times index seconds)
    (.set flags (.subarray (.-retained history) 0 index))
    (.set flags (.subarray (.-retained history) index) (inc index))
    (aset flags index (if retained 1 0))
    (->Reviews times flags (doto (.slice (.-ids history)) (.splice index 0 id)))))


(defn without-review
  "Returns `history` without the review `id`."
  [^Reviews history id]
  (let [index (.indexOf (.-ids history) id)]
    (if (neg? index)
      history
      (let [size  (.-length (.-ids history))
            times (js/Float64Array. (dec size))
            flags (js/Uint8Array. (dec size))]
        (.set times (.subarray (.-seconds history) 0 index))
        (.set times (.subarray (.-seconds history) (inc index)) index)
        (.set flags (.subarray (.-retained history) 0 index))
        (.set flags (.subarray (.-retained history) (inc index)) index)
        (->Reviews times flags (doto (.slice (.-ids history)) (.splice index 1)))))))


(defn urgency
  "How overdue a word is: the time since its last review, counted in its own
   forgetting time-constants. It ranks words as retention does, reversed,
   but keeps separating them after retention underflows to zero (3.8 days
   unreviewed at the initial rate). A word with no reviews is as due as a
   word can be.

   The forgetting rate starts at 0.00231 per second. Each retained review
   divides it by one plus the rate times the interval; each lapse doubles
   it."
  [^Reviews history now-ms]
  (let [size (if history (.-length (.-ids history)) 0)]
    (if (zero? size)
      ##Inf
      (let [seconds  (.-seconds history)
            retained (.-retained history)
            rate     (loop [index 1
                            rate  0.00231]
                       (if (< index size)
                         (recur (inc index)
                                (if (== 1 (aget retained index))
                                  (/ rate (inc (* rate (- (aget seconds index) (aget seconds (dec index))))))
                                  (* 2 rate)))
                         rate))]
        (* rate (utils/ms->secs (- now-ms (* 1000 (aget seconds (dec size))))))))))


(defn level
  "Retention percentage (0-100) for an urgency. A word never reviewed gets
   0. When the clock has moved backwards, the cap keeps the level at 100."
  [urgency]
  (min 100 (* 100 (math/exp (- urgency)))))

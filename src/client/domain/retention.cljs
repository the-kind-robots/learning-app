(ns domain.retention
  "How due a word is, from its reviews.

   A word's review history is four columns — when each review was made, in
   seconds, whether it was retained, how long the answer took in
   milliseconds (NaN when unknown), and its id — ordered by time and,
   within one second, by id, so the result does not depend on the order the
   reviews arrived in. A history is never changed: a review in or out gives
   a new one. `urgency` walks the columns: a lesson ranks every word in
   scope on the tap."
  (:require
   [clojure.math :as math]
   [goog.array :as garray]
   [utils :as utils]))


(defrecord Reviews [seconds retained answer-ms ids])


(def empty-reviews
  (->Reviews (js/Float64Array. 0) (js/Uint8Array. 0) (js/Float64Array. 0) #js []))


(defn- after?
  "Whether the review at `index` of `history` comes after one made at
   `seconds` with `id`."
  [^Reviews history index seconds id]
  (let [made (aget (.-seconds history) index)]
    (or (> made seconds)
        (and (== made seconds) (pos? (garray/defaultCompare (aget (.-ids history) index) id))))))


(defn with-review
  "Returns `history` with `review` — `{:id :created-at :retained :answer-ms}`, `:answer-ms` optional — in its
   place in time order. Nearly always that place is the end."
  [^Reviews history {:keys [answer-ms created-at id retained]}]
  (let [seconds    (utils/iso->secs created-at)
        size       (.-length (.-ids history))
        ;; The review's position: after every review that comes before it, walking back from the end.
        at         (loop [at size]
                     (if (and (pos? at) (after? history (dec at) seconds id))
                       (recur (dec at))
                       at))
        ;; A typed array does not grow: `column` is copied into `longer`, one slot longer, with `value` at `at`.
        spliced-in (fn [^js column ^js longer value]
                     (.set longer (.subarray column 0 at))
                     (.set longer (.subarray column at) (inc at))
                     (aset longer at value)
                     longer)]
    (->Reviews (spliced-in (.-seconds history) (js/Float64Array. (inc size)) seconds)
               (spliced-in (.-retained history) (js/Uint8Array. (inc size)) (if retained 1 0))
               (spliced-in (.-answer-ms history) (js/Float64Array. (inc size)) (or answer-ms ##NaN))
               (doto (.slice (.-ids history)) (.splice at 0 id)))))


(defn without-review
  "Returns `history` without the review `id`."
  [^Reviews history id]
  (let [at (.indexOf (.-ids history) id)]
    (if (neg? at)
      history
      (let [size        (.-length (.-ids history))
            ;; A typed array does not shrink: `column` is copied into `shorter`, one slot shorter, without `at`.
            spliced-out (fn [^js column ^js shorter]
                          (.set shorter (.subarray column 0 at))
                          (.set shorter (.subarray column (inc at)) at)
                          shorter)]
        (->Reviews (spliced-out (.-seconds history) (js/Float64Array. (dec size)))
                   (spliced-out (.-retained history) (js/Uint8Array. (dec size)))
                   (spliced-out (.-answer-ms history) (js/Float64Array. (dec size)))
                   (doto (.slice (.-ids history)) (.splice at 1)))))))


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

(ns domain.retention
  "The memory of a word: how likely the learner recalls it, from its reviews, and what a review of it would
   add. Nothing here knows of lessons.

   A word's review history is four columns, one entry per review: `reviewed-at`, when it was made, in whole
   seconds; `retained`, 1 or 0; `answer-ms`, how long the answer took, NaN when unknown; `review-ids`. The
   entries are ordered by time and, within one second, by review id, so the order does not depend on the
   order the reviews arrived in. `memory` walks the columns: a lesson ranks every word in scope on the tap.

   Why columns, and why a new history per review (ADR-0016): a lesson walks every word's history on the tap,
   and typed arrays keep that walk fast and the memory snapshot small. Memory must stay a value — an older
   memory never sees a later review — so a history is never changed in place. A typed array has a fixed
   length, so a review in is a copy of each column one slot longer.

   The memory is FSRS-34 — FSRS-7, 34 weights, two memory traces; #513 compared it on the learner's log with the
   rate model it replaced (`dev.kernel-comparison`). A review is rated from its outcome and its time to answer.
   Wrong is Again. Right is Easy, Good or Hard by how fast it was against the learner's pace (`pace`); right
   with no time, or before the pace is known, is Good.

   The formulas and the default weights are ported from fsrs-rs (`src/model_v7.rs`, `src/inference_v7.rs`
   at 3be7d2af, BSD-3-Clause) and ts-fsrs (`packages/fsrs/src/models/fsrs-7` at e5af3b9f, MIT), both
   Copyright (c) Open Spaced Repetition. The tests check them against those projects' reference values."
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


;;
;; FSRS-34
;;


(def default-weights
  "The 34 weights FSRS-7 ships with, fitted by its authors on other learners' logs."
  [0.1104 2.2395 3.9221 11.7841 6.1686 0.6457 3.6807 1.9795 0.0 1.3826 0.7024 0.5999 0.8146 0.6398 1.0 1.3207
   0.6707 3.8668 0.4416 0.0934 1.8631 0.6162 1.0869 0.1567 0.0801 0.2421 0.9464 0.1433 0.7145 0.0 0.5667 0.3734
   0.5333 0.3048])


(def ^:private grade
  "A rating as the formulas count it."
  {:rating/again 1 :rating/easy 4 :rating/good 3 :rating/hard 2})


(defn- clamp
  [value low high]
  (max low (min high value)))


(def ^:private stability-min 0.0001)


(def ^:private stability-max 36500)


(defn initial-state
  "The memory after a first review rated `rating` (`:rating/again` … `:rating/easy`): `{:stability
   :stability-fast :difficulty}`, stabilities in days."
  [weights rating]
  (let [counted   (grade rating)
        stability (clamp (nth weights (dec counted)) stability-min stability-max)]
    {:difficulty     (clamp (inc (- (nth weights 4) (js/Math.exp (* (nth weights 5) (dec counted))))) 1 10)
     :stability      stability
     :stability-fast (clamp (* 0.8 stability) stability-min stability-max)}))


(defn- fast-recall
  "The fast trace alone, `days` after its last review."
  [weights days stability-fast]
  (let [stability (clamp stability-fast stability-min stability-max)
        decay     (- (clamp (* (nth weights 23) (js/Math.pow stability (- (nth weights 33) 0.3))) 0.01 0.95))
        scale     (/ (dec (js/Math.exp (min (/ (js/Math.log (nth weights 25)) decay) 60))) stability)]
    (js/Math.pow (inc (* scale (max days 0))) decay)))


(defn- recall-after
  "The probability of recall `days` after the last review of a word whose memory is `state`, in [1e-5, 1)."
  [weights days {:keys [difficulty stability stability-fast]}]
  (let [stability      (clamp stability stability-min stability-max)
        stability-fast (clamp stability-fast stability-min stability-max)
        difficulty     (clamp difficulty 1 10)
        decay          (- (clamp (nth weights 24) 0.01 0.95))
        scale          (/ (* (dec (js/Math.pow (nth weights 26) (/ 1 decay)))
                             (js/Math.exp (* (- difficulty 5) (- (nth weights 32) 0.3))))
                          stability)
        slow-recall    (js/Math.pow (inc (* scale (max days 0))) decay)
        fast-weight    (* (nth weights 27) (js/Math.pow stability-fast (- (nth weights 29))))
        slow-weight    (* (nth weights 28)
                          (js/Math.pow stability (nth weights 30))
                          (js/Math.exp (* (- difficulty 5) (- (nth weights 31) 0.5))))]
    (+ (* (/ (+ (* fast-weight (fast-recall weights days stability-fast)) (* slow-weight slow-recall))
             (+ fast-weight slow-weight))
          (- 1 2e-5))
       1e-5)))


(defn- next-stability
  "One trace's stability after a review rated `rating`, given that trace's `recall` at the review; its eight
   weights start at `first-weight`. A lapse — a word known, then not recalled — takes the lapse stability."
  [weights stability difficulty recall rating first-weight]
  (let [weight #(nth weights (+ first-weight %))
        lapse-stability (* (weight 3)
                           (dec (js/Math.pow (inc stability) (weight 4)))
                           (js/Math.exp (* (- 1 recall) (weight 5))))
        post-lapse-stability (min stability lapse-stability)]
    (clamp (if (= :rating/again rating)
             post-lapse-stability
             (max post-lapse-stability
                  (* stability
                     (inc
                      (* (js/Math.exp (- (weight 0) 1.5))
                         (- 11 difficulty)
                         (js/Math.pow stability (- (weight 1)))
                         (dec (js/Math.exp (* (- 1 recall) (weight 2))))
                         (if (= :rating/hard rating)
                           (weight 6)
                           1)
                         (if (= :rating/easy rating)
                           (weight 7)
                           1))))))
           stability-min
           stability-max)))


(defn next-state
  "The memory `state` after a review rated `rating`, `days` after the one before."
  [weights state days rating]
  (let [stability       (clamp (:stability state) stability-min stability-max)
        stability-fast  (clamp (:stability-fast state) stability-min stability-max)
        difficulty      (clamp (:difficulty state) 1 10)
        recall          (recall-after weights days state)
        next-stability-slow (next-stability weights stability difficulty recall rating 7)
        next-stability-fast (next-stability weights
                                            stability-fast
                                            difficulty
                                            (fast-recall weights days stability-fast)
                                            rating
                                            15)
        difficulty-step (cond-> (* (- (nth weights 6)) (- (grade rating) 3))
                          (= :rating/again rating) (* (+ recall 0.1)))]
    ;; Difficulty reverts towards the first Easy's, unclamped: about 0.23 by default.
    {:difficulty     (clamp (+ (* 0.01 (inc (- (nth weights 4) (js/Math.exp (* (nth weights 5) 3)))))
                               (* 0.99 (+ difficulty (/ (* (- 10 difficulty) difficulty-step) 9))))
                            1
                            10)
     :stability      next-stability-slow
     :stability-fast (clamp (if (= :rating/again rating)
                              (min next-stability-fast (* 0.8 next-stability-slow))
                              next-stability-fast)
                            stability-min
                            stability-max)}))


;;
;; The rating from time to answer
;;


(def ^:private ceiling-ms
  "A longer answer counts as this long: the learner was away, not thinking."
  60000)


(defn- median
  "The median of `values`, a Float64Array, which it sorts in place."
  [^js values]
  (let [sorted (.sort values)
        size   (.-length sorted)
        middle (quot size 2)]
    (if (odd? size)
      (aget sorted middle)
      (/ (+ (aget sorted (dec middle)) (aget sorted middle)) 2))))


(defn- percentile
  "The value below which a `share` of `sorted`, an ascending Float64Array, lies."
  [^js sorted share]
  (aget sorted (min (dec (.-length sorted)) (js/Math.floor (* share (.-length sorted))))))


(defn pace
  "How fast the learner answers, from `cards`, `{:word :reviews}` of every word (`Reviews`). The expected time of
   a right answer is `a + b × length`, the length that of the word in characters, fitted by median (Theil–Sen)
   on the 500 most recent timed right answers; `:fast` and `:slow` are the 25th and 75th percentiles of actual /
   expected. Nil while there are fewer than 30."
  [cards]
  ;; Each timed right answer as #js [reviewed-at answer-ms length].
  (let [answers #js []]
    (doseq [{:keys [reviews word]} cards
            :when    reviews
            :let     [^js history reviews]
            position (range (.-length (.-review-ids history)))
            :let     [answer-ms (aget (.-answer-ms history) position)]
            :when    (and (== 1 (aget (.-retained history) position)) (not (js/isNaN answer-ms)))]
      (.push answers #js [(aget (.-reviewed-at history) position) (min answer-ms ceiling-ms) (count (:value word))]))
    (when (>= (.-length answers) 30)
      ;; Typed arrays and a numeric sort: 500 answers make 124 750 slopes, too many for vectors on a lesson's tap.
      (let [recent      (.slice (.sort answers #(- (aget %1 0) (aget %2 0))) -500)
            size        (.-length recent)
            answer-ms   (js/Float64Array.from recent #(aget % 1))
            lengths     (js/Float64Array.from recent #(aget % 2))
            ;; Theil–Sen: the slope is the median of the slopes between every two answers of different lengths.
            slopes      (js/Float64Array. (quot (* size (dec size)) 2))
            slope-count (loop [one    0
                               other  1
                               filled 0]
                          (cond
                            (>= one size)   filled
                            (>= other size) (recur (inc one) (+ one 2) filled)
                            (== (aget lengths one) (aget lengths other)) (recur one (inc other) filled)
                            :else           (do (aset slopes
                                                      filled
                                                      (/ (- (aget answer-ms other) (aget answer-ms one))
                                                         (- (aget lengths other) (aget lengths one))))
                                                (recur one (inc other) (inc filled)))))
            b           (if (pos? slope-count)
                          (max 0 (median (.subarray slopes 0 slope-count)))
                          0)
            a           (max 1 (median (.map answer-ms (fn [ms position] (- ms (* b (aget lengths position)))))))
            ratios      (.sort (.map answer-ms (fn [ms position] (/ ms (+ a (* b (aget lengths position)))))))]
        {:a    a
         :b    b
         :fast (percentile ratios 0.25)
         :slow (percentile ratios 0.75)}))))


(defn rating
  "The rating of a review, `:rating/again` … `:rating/easy`: `retained`, how long it took (`answer-ms`, NaN or
   nil when unknown), the length of its answer, against the learner's `pace` (nil while unknown)."
  [pace retained answer-ms length]
  (let [{:keys [a b fast slow]} pace]
    (cond
      (not retained) :rating/again
      (or (nil? pace) (nil? answer-ms) (js/isNaN answer-ms)) :rating/good
      :else (let [ratio (/ (min answer-ms ceiling-ms) (+ a (* b length)))]
              (cond
                (< ratio fast) :rating/easy
                (> ratio slow) :rating/hard
                :else          :rating/good)))))


;;
;; A word's memory
;;


(defn memory
  "The memory of `word` after its `history` (`Reviews`), by FSRS-34 with weights `weights` and the learner's
   `pace`: `{:difficulty :stability :stability-fast :reviewed-at}`, `:reviewed-at` the last review in whole
   seconds; nil for a word with no answer. The review an earlier version wrote when the word was added — the
   first, retained, untimed, within two seconds of the word's `:created-at` — is no answer, and is skipped."
  [{:keys [pace weights word] ^js history :history}]
  (let [size         (if history
                       (.-length (.-review-ids history))
                       0)
        reviewed-at  (when history (.-reviewed-at history))
        retained     (when history (.-retained history))
        answer-ms    (when history (.-answer-ms history))
        first-answer (if (and (pos? size)
                              (== 1 (aget retained 0))
                              (js/isNaN (aget answer-ms 0))
                              (<= (abs (- (aget reviewed-at 0) (utils/iso->secs (:created-at word)))) 2))
                       1
                       0)]
    (when (< first-answer size)
      (let [length    (count (:value word))
            rating-at #(rating pace (== 1 (aget retained %)) (aget answer-ms %) length)
            state     (loop [position (inc first-answer)
                             state    (initial-state weights (rating-at first-answer))]
                        (if (< position size)
                          (recur (inc position)
                                 (next-state weights
                                             state
                                             (/ (- (aget reviewed-at position) (aget reviewed-at (dec position))) 86400)
                                             (rating-at position)))
                          state))]
        (assoc state :reviewed-at (aget reviewed-at (dec size)))))))


(defn- days-since
  "Days from the last review of `memory` to `now-ms`."
  [memory now-ms]
  (/ (- (/ now-ms 1000) (:reviewed-at memory)) 86400))


(defn recall
  "The probability, in [1e-5, 1), that the learner recalls at `now-ms` a word whose memory is `memory` (`memory`),
   by `weights`."
  [{:keys [memory now-ms weights]}]
  (recall-after weights (days-since memory now-ms) memory))


(defn stability-gain
  "How much a review of a word at `now-ms` is expected to add to its stability, in log. `memory` is the word's
   (`memory`). The review is right with the word's recall, then rated Good, and Again otherwise."
  [{:keys [memory now-ms weights]}]
  (let [days  (days-since memory now-ms)
        right (recall-after weights days memory)
        after #(js/Math.log (:stability (next-state weights memory days %)))]
    (- (+ (* right (after :rating/good)) (* (- 1 right) (after :rating/again)))
       (js/Math.log (:stability memory)))))

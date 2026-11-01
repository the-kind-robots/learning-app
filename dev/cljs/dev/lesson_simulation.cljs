(ns dev.lesson-simulation
  "Days of lessons over a learner's vocabulary (#513), FSRS-34 standing for the learner's memory, to compare how a
   lesson ranks its words: least recall (A), the drop of the forgetting over a horizon (B), the gain of stability
   (C, `domain.retention/stability-gain`, the one the app takes), and SSP-MMC's thresholds. Every lesson takes its
   words by `chosen-words`, the rules of `domain.lesson/lesson-words`; an answer is right with the word's recall, a new word's first answer
   half the time. Reported per policy: the forgetting of the vocabulary — the mean over the words answered of
   minus their log-recall — at some days and over the span; how the reviews spread over the words; reviews per
   word lasting a week and a month; the longest run of lessons with one word.

     npx shadow-cljs compile lesson-simulation
     node target/lesson-simulation.js log.json [lessons-per-day] [days, 30] [part of a policy's name]

   Lessons go evenly over twelve hours a day. Without `lessons-per-day`, it is the log's: answers per day the
   learner answered at all, over the three words of a lesson."
  (:require
   [clojure.string :as str]
   [dev.kernel-comparison :as kernel-comparison]
   [domain.retention :as retention]))


(def ^:private weights retention/default-weights)


(defn- days-since
  [memory now-ms]
  (/ (- (/ now-ms 1000) (:reviewed-at memory)) 86400))


(defn- recall-after
  "The probability of recall `days` after the last review of a memory in `state`."
  [days state]
  (retention/recall {:memory (assoc state :reviewed-at 0) :now-ms (* days 86400000) :weights weights}))


(defn- forgetting
  "The mean over the words of `memories` that have one of minus their log-recall at `now-ms`."
  [memories now-ms]
  (let [known (keep val memories)]
    (/ (reduce + (map #(- (js/Math.log (recall-after (days-since % now-ms) %))) known))
       (count known))))


(defn- forgetting-gain
  "How much a review at `now-ms` is expected to lower the forgetting of the word whose memory is `memory` — minus
   its log-recall — summed over the next `horizon` days, against no review: policy B of #513. Right with the
   word's recall, then Good, else Again."
  [memory horizon now-ms]
  (let [days       (days-since memory now-ms)
        right      (recall-after days memory)
        ;; Minus log-recall of `state` from `from` days after its last review over `horizon` days: the midpoint rule.
        forgetting (fn [state from]
                     (let [step (/ horizon 16)]
                       (* step
                          (reduce +
                                  (map #(- (js/Math.log (recall-after (+ from (* step (+ % 0.5))) state)))
                                       (range 16))))))
        reviewed   #(retention/next-state weights memory days %)]
    (- (forgetting memory days)
       (* right (forgetting (reviewed :rating/good) 0))
       (* (- 1 right) (forgetting (reviewed :rating/again) 0)))))


(defn- chosen-words
  "The `size` words a lesson takes from `gains`, `[word gain]` pairs, nil for a new word, by the rules of
   `domain.lesson/lesson-words` — one new word, a 0.1 random share of known words — but ranked by any gain, so
   that policies compare. Keep it in step with `lesson-words`."
  [gains size random]
  (let [shuffled    (let [pairs (to-array gains)]
                      (doseq [position (range (dec (alength pairs)) 0 -1)
                              :let     [other (js/Math.floor (* (random) (inc position)))
                                        pair  (aget pairs position)]]
                        (aset pairs position (aget pairs other))
                        (aset pairs other pair))
                      pairs)
        new-words   (->> shuffled (remove second) (mapv first))
        known-words (->> shuffled (filter second) (sort-by (comp - second)) (mapv first))
        new-places  (if (and (seq new-words) (pos? size)) 1 0)]
    (loop [chosen      (vec (take new-places new-words))
           known-words known-words
           new-words   (drop new-places new-words)]
      (cond
        (or (>= (count chosen) size) (and (empty? known-words) (empty? new-words))) chosen
        (empty? known-words) (recur (conj chosen (first new-words)) known-words (rest new-words))
        :else (let [position (if (< (random) 0.1) (js/Math.floor (* (random) (count known-words))) 0)]
                (recur (conj chosen (known-words position))
                       (into (subvec known-words 0 position) (subvec known-words (inc position)))
                       new-words))))))


(defn- generator
  "A function of nothing returning numbers in [0, 1), the same ones for the same `seed`."
  [seed]
  (let [state (volatile! seed)]
    (fn []
      (/ (vswap! state #(mod (* % 16807) 2147483647)) 2147483647))))


(defn- simulated
  "`span` days of `lessons-per-day` lessons from `start-ms`, the words ranked by `gain-of`, a function of a memory
   and a moment: `{:daily :reviews :longest-run}` — the forgetting at the end of each day, the reviews of each
   word, and the most lessons in a row one word was in."
  [memories gain-of lessons-per-day span start-ms seed]
  (let [random  (generator seed)
        reviews (volatile! {})
        runs    (volatile! {})
        longest (volatile! 0)]
    (loop [day      0
           memories memories
           daily    []]
      (if (= day span)
        {:daily daily :longest-run @longest :memories memories :reviews @reviews}
        (let [memories
              (reduce (fn [memories lesson-number]
                        (let [now-ms (+ start-ms
                                        (* day 86400000)
                                        (* lesson-number (/ 43200000 lessons-per-day)))
                              ;; A known word `gain-of` gives nil for is left out of the lesson.
                              gains  (keep (fn [[word-id memory]]
                                             (if memory
                                               (some->> (gain-of memory now-ms) (vector word-id))
                                               [word-id nil]))
                                           memories)
                              chosen (chosen-words gains 3 random)]
                          ;; A word's run: the lessons in a row it was in, up to this one.
                          (vreset! runs (into {} (map (fn [word-id] [word-id (inc (get @runs word-id 0))])) chosen))
                          (vswap! longest max (apply max 0 (vals @runs)))
                          (vswap! reviews #(reduce (fn [counts word-id] (update counts word-id (fnil inc 0))) % chosen))
                          (reduce (fn [memories word-id]
                                    (let [memory (memories word-id)
                                          right? (< (random)
                                                    (if memory
                                                      (recall-after (days-since memory now-ms) memory)
                                                      0.5))
                                          rating (if right? :rating/good :rating/again)]
                                      (assoc memories
                                             word-id
                                             (assoc (if memory
                                                      (retention/next-state weights
                                                                            memory
                                                                            (days-since memory now-ms)
                                                                            rating)
                                                      (retention/initial-state weights rating))
                                                    :reviewed-at
                                                    (/ now-ms 1000)))))
                                  memories
                                  chosen)))
                      memories
                      (range lessons-per-day))]
          (recur (inc day) memories (conj daily (forgetting memories (+ start-ms (* (inc day) 86400000))))))))))


;;
;; SSP-MMC: the review thresholds that bring a word to a year's stability in the fewest reviews
;;


(def ^:private ln-stabilities
  "The grid of the slow stability, in log days: 0.05 to 365, the last being the goal."
  (let [low  (js/Math.log 0.05)
        high (js/Math.log 365)]
    (mapv #(+ low (* % (/ (- high low) 39))) (range 40))))


(def ^:private difficulties (mapv inc (range 10)))


(def ^:private ln-ratios
  "The grid of the fast stability over the slow one, in log: 0.02 to 2."
  (let [low  (js/Math.log 0.02)
        high (js/Math.log 2)]
    (mapv #(+ low (* % (/ (- high low) 7))) (range 8))))


(def ^:private thresholds [0.3 0.4 0.5 0.6 0.7 0.75 0.8 0.85 0.9 0.95])


(defn- node
  [i j k]
  (+ (* (+ (* i (count difficulties)) j) (count ln-ratios)) k))


(defn- coordinates
  "Where `memory` falls on the grid, as fractional indices `[i j k]`, clamped to it."
  [{:keys [difficulty stability stability-fast]}]
  (let [fraction (fn [grid value]
                   (let [low  (first grid)
                         step (- (second grid) (first grid))]
                     (max 0 (min (dec (count grid)) (/ (- value low) step)))))]
    [(fraction ln-stabilities (js/Math.log (max stability 1e-4)))
     (fraction difficulties (max 1 (min 10 difficulty)))
     (fraction ln-ratios (js/Math.log (max 1e-4 (/ stability-fast (max stability 1e-4)))))]))


(defn- interpolated
  "`table` over the grid, trilinear at `[i j k]`."
  [^js table [i j k]]
  (let [i0 (js/Math.floor i)
        j0 (js/Math.floor j)
        k0 (js/Math.floor k)
        i1 (min (inc i0) (dec (count ln-stabilities)))
        j1 (min (inc j0) (dec (count difficulties)))
        k1 (min (inc k0) (dec (count ln-ratios)))
        di (- i i0)
        dj (- j j0)
        dk (- k k0)]
    (reduce +
            (for [[ii wi] [[i0 (- 1 di)] [i1 di]]
                  [jj wj] [[j0 (- 1 dj)] [j1 dj]]
                  [kk wk] [[k0 (- 1 dk)] [k1 dk]]
                  :let    [w (* wi wj wk)]
                  :when   (pos? w)]
              (* w (aget table (node ii jj kk)))))))


(defn- days-to
  "The days after its last review at which `state` is recalled with probability `target`."
  [state target]
  (loop [low  0
         high 1]
    (if (> (recall-after high state) target)
      (recur low (* 2 high))
      (loop [low  low
             high high
             n    0]
        (if (= n 50)
          high
          (let [middle (/ (+ low high) 2)]
            (if (> (recall-after middle state) target)
              (recur middle high (inc n))
              (recur low middle (inc n)))))))))


(defn- ssp-mmc-thresholds
  "The threshold of recall at which each grid state is best reviewed, by value iteration: the expected reviews
   to reach a stability of 365 days, one per review, Good when recalled and Again when not."
  []
  (let [size       (* (count ln-stabilities) (count difficulties) (count ln-ratios))
        states     (for [i (range (count ln-stabilities))
                         j (range (count difficulties))
                         k (range (count ln-ratios))]
                     [(node i j k) i
                      (let [stability (js/Math.exp (ln-stabilities i))]
                        {:difficulty     (difficulties j)
                         :stability      stability
                         :stability-fast (* stability (js/Math.exp (ln-ratios k)))})])
        ;; Per state and threshold: where Good and Again lead.
        successors (into {}
                         (for [[position i state] states
                               :when (< i (dec (count ln-stabilities)))]
                           [position
                            (mapv (fn [threshold]
                                    (let [days (days-to state threshold)]
                                      [threshold
                                       (coordinates (retention/next-state weights state days :rating/good))
                                       (coordinates (retention/next-state weights state days :rating/again))]))
                                  thresholds)]))
        expected   (js/Float64Array. size)
        best       (js/Float64Array. size)]
    (loop [iteration 0]
      (let [change (reduce (fn [change [position actions]]
                             (let [[cost threshold]
                                   (apply min-key
                                          first
                                          (for [[threshold good again] actions]
                                            [(+ 1
                                                (* threshold (interpolated expected good))
                                                (* (- 1 threshold) (interpolated expected again)))
                                             threshold]))]
                               (aset best position threshold)
                               (let [before (aget expected position)]
                                 (aset expected position cost)
                                 (max change (abs (- cost before))))))
                           0
                           successors)]
        (when (and (> change 1e-3) (< iteration 1000))
          (recur (inc iteration)))))
    {:best best :expected expected}))


(defn main
  [path & [lessons-per-day span only]]
  (let [{:keys [cards words]} (kernel-comparison/learner-log path)
        memory-of       (into {}
                              (map (fn [{:keys [reviews word]}]
                                     [(:_id word) (retention/memory {:history reviews :weights weights :word word})]))
                              cards)
        memories        (into {} (map (fn [word-id] [word-id (memory-of word-id)])) (keys words))
        start-ms        (* 1000 (apply max (keep #(some-> % :reviewed-at) (vals memories))))
        ;; Answers: every review but the one written on add, which `retention/memory` skips.
        answer-days     (for [{:keys [reviews]} cards
                              :let     [^js history reviews]
                              position (range (.-length (.-review-ids history)))]
                          (js/Math.floor (/ (aget (.-reviewed-at history) position) 86400)))
        lessons-per-day (or (some-> lessons-per-day js/parseInt)
                            (max 1 (js/Math.round (/ (count answer-days) (count (distinct answer-days)) 3))))
        span            (or (some-> span js/parseInt) 30)
        policies        (into
                         [["least recall"
                           (fn [memory now-ms]
                             (- (js/Math.log (recall-after (days-since memory now-ms) memory))))]]
                         (concat
                          (for [horizon [7 30]]
                            [(str "gain over " horizon " d")
                             (fn [memory now-ms] (forgetting-gain memory horizon now-ms))])
                          (let [started (js/performance.now)
                                {:keys [best expected]} (ssp-mmc-thresholds)
                                urgency (fn [memory now-ms]
                                          (- (interpolated best (coordinates memory))
                                             (recall-after (days-since memory now-ms) memory)))]
                            (println (str "SSP-MMC solved in "
                                          (.toFixed (- (js/performance.now) started) 0)
                                          " ms; "
                                          "reviews from a first Good to a year: "
                                          (.toFixed (interpolated expected
                                                                  (coordinates (retention/initial-state weights
                                                                                                        :rating/good)))
                                                    1)))
                            ;; Urgency: how far recall is below the best threshold.
                            [["SSP-MMC, reviews saved"
                              ;; How much a review now shortens the expected way to a year's stability.
                              (fn [memory now-ms]
                                (let [days  (days-since memory now-ms)
                                      right (recall-after days memory)
                                      after #(interpolated expected
                                                           (coordinates (retention/next-state weights memory days %)))]
                                  (- (interpolated expected (coordinates memory))
                                     (* right (after :rating/good))
                                     (* (- 1 right) (after :rating/again)))))]
                             ["SSP-MMC, one new word a lesson" urgency]
                             ["SSP-MMC, new words when nothing is due"
                              (fn [memory now-ms] (let [u (urgency memory now-ms)] (when-not (neg? u) u)))]])
                          ;; As SSP-MMC, one step ahead: what a review adds to the word's stability, in log.
                          [["stability gain" #(retention/stability-gain {:memory %1 :now-ms %2 :weights weights})]]))]
    (println (str (count memories)
                  " words, "
                  (count (filter val memories))
                  " answered, "
                  lessons-per-day
                  " lessons a day of 3 words"))
    (doseq [[policy gain-of] policies
            :when (or (nil? only) (str/includes? policy only))]
      (let [started (js/performance.now)
            ;; Seeds far from 0, whose first draws are not near 0.
            results (mapv #(simulated memories gain-of lessons-per-day span start-ms (* 104729 %)) (range 1 11))
            runs    (map :daily results)
            mean-of (fn [f] (/ (reduce + (map f results)) (count results)))
            mean-at (fn [day] (/ (reduce + (map #(nth % (dec day)) runs)) (count runs)))
            means   (map #(/ (reduce + %) span) runs)
            mean    (/ (reduce + means) (count means))
            spread  (js/Math.sqrt (/ (reduce + (map #(* (- % mean) (- % mean)) means))
                                     (* (count means) (dec (count means)))))]
        (println
         (str
          policy
          ": day 7 "
          (.toFixed (mean-at 7) 3)
          ", day 14 "
          (.toFixed (mean-at 14) 3)
          ", day 30 "
          (.toFixed (mean-at 30) 3)
          ", day "
          span
          " "
          (.toFixed (mean-at span) 3)
          ", mean "
          (.toFixed mean 3)
          " ± "
          (.toFixed spread 3)
          "; words reviewed "
          (.toFixed (mean-of #(count (:reviews %))) 0)
          ", top 10 words take "
          (.toFixed (mean-of #(/ (* 100 (reduce + (take 10 (sort > (vals (:reviews %))))))
                                 (reduce + (vals (:reviews %)))))
                    1)
          " % of reviews, most of one word "
          (.toFixed (mean-of #(apply max (vals (:reviews %)))) 1)
          "; reviews per word lasting a week "
          (.toFixed (mean-of #(/ (reduce + (vals (:reviews %)))
                                 (count (filter (fn [m] (some-> m :stability (>= 7))) (vals (:memories %))))))
                    1)
          ", a month "
          (.toFixed (mean-of #(/ (reduce + (vals (:reviews %)))
                                 (count (filter (fn [m] (some-> m :stability (>= 30))) (vals (:memories %))))))
                    1)
          ", longest run "
          (.toFixed (mean-of :longest-run) 1)
          " lessons  ("
          (.toFixed (/ (- (js/performance.now) started) 1000) 1)
          " s)"))))))

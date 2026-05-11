(ns domain.retention
  (:require
   [clojure.math :as math]
   [utils :as utils]))


(def ^:private initial-forgetting-rate
  0.00231)


(defn- reviews->retention-state
  [reviews]
  (let [reviews (->> reviews
                     (map #(update % :created-at utils/iso->secs))
                     (sort-by :created-at))
        review-count (count reviews)
        last-review-ms (some-> (last reviews) :created-at (* 1000))
        forgetting-rate
        (if (< review-count 2)
          initial-forgetting-rate
          (reduce
           (fn [rate [prev curr]]
             (let [interval (- (:created-at curr) (:created-at prev))]
               (if (:retained curr)
                 (/ rate (inc (* rate interval)))
                 (* 2 rate))))
           initial-forgetting-rate
           (map vector reviews (rest reviews))))]
    {:forgetting-rate forgetting-rate
     :last-review-ms  last-review-ms
     :review-count    review-count}))


(defn- retention-state->retention-level
  [{:keys [forgetting-rate last-review-ms]} now-ms]
  (if (nil? last-review-ms)
    0
    (let [elapsed-secs (utils/ms->secs (- now-ms last-review-ms))]
      (min 100 (* 100 (math/exp (- (* forgetting-rate elapsed-secs))))))))


(defn retention-level
  "Pure function. Returns retention percentage (0-100) for a sequence
   of reviews at a given point in time."
  [reviews now-ms]
  (-> reviews reviews->retention-state (retention-state->retention-level now-ms)))

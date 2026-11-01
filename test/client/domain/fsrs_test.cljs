(ns client.domain.fsrs-test
  "FSRS-34 against the reference values of ts-fsrs (`packages/fsrs/src/models/fsrs-7/model.spec.ts` at
   e5af3b9f), which fsrs-rs and srs-benchmark computed; within their tolerance, max(1e-7, |e| × 1e-4)."
  (:require
   [cljs.test :refer-macros [are deftest is testing]]
   [domain.fsrs :as sut]
   [domain.retention :as retention]
   [utils :as utils]))


(defn- close?
  [expected actual]
  (<= (abs (- expected actual)) (max 1e-7 (* 1e-4 (abs expected)))))


(defn- same-state?
  [expected actual]
  (every? #(close? (% expected) (% actual)) [:difficulty :stability :stability-fast]))


(def ^:private rated
  "A reference rating, 1 to 4, as a rating."
  [nil :rating/again :rating/hard :rating/good :rating/easy])


(defn- states
  "The memory after each review of `history`, `[days rating]` each, the first starting it."
  [history]
  (let [[[_ first-rating] & more] history]
    (reductions (fn [state [days rating]] (sut/next-state sut/default-weights state days (rated rating)))
                (sut/initial-state sut/default-weights (rated first-rating))
                more)))


(deftest the-memory-after-each-review-is-the-reference-one
  (are [history expected] (every? true? (map same-state? expected (states history)))
   [[0 1] [0 3] [1 3] [3 3] [8 3] [21 3]]
    [{:stability 0.1104 :stability-fast 0.08832 :difficulty 6.1686}
     {:stability 0.11041191 :stability-fast 0.08832 :difficulty 6.109214}
     {:stability 0.91686124 :stability-fast 44.444206 :difficulty 6.0504217}
     {:stability 3.5337858 :stability-fast 193.5973 :difficulty 5.9922175}
     {:stability 10.186002 :stability-fast 407.62646 :difficulty 5.934595}
     {:stability 25.985723 :stability-fast 700.5589 :difficulty 5.877549}]

   [[0 1] [0.99791664 1] [0.82361114 1] [1.0388889 1] [0.0013888889 3] [0.0020833334 3] [0.0020833334 3]
    [0.0027777778 3]]
    [{:stability 0.1104 :stability-fast 0.08832 :difficulty 6.1686}
     {:stability 0.06706447 :stability-fast 0.016565936 :difficulty 8.047908}
     {:stability 0.04536918 :stability-fast 0.0034119464 :difficulty 8.764432}
     {:stability 0.0329967 :stability-fast 0.00074974925 :difficulty 9.098293}
     {:stability 0.14780034 :stability-fast 2.8802187 :difficulty 9.009611}
     {:stability 0.24254547 :stability-fast 7.019944 :difficulty 8.921815}
     {:stability 0.3307042 :stability-fast 10.166433 :difficulty 8.834897}
     {:stability 0.4365655 :stability-fast 13.619719 :difficulty 8.748848}]

   [[0 4] [0.001 2] [0.25 1] [0 3] [1.5 4] [2.75 2] [100.5 1]]
    [{:stability 11.7841 :stability-fast 9.427279 :difficulty 1}
     {:stability 12.22947 :stability-fast 13.9512825 :difficulty 4.636193}
     {:stability 2.6588404 :stability-fast 0.3485167 :difficulty 9.259369}
     {:stability 2.6589437 :stability-fast 0.3485167 :difficulty 9.169076}
     {:stability 5.028668 :stability-fast 25.826185 :difficulty 8.743264}
     {:stability 7.37282 :stability-fast 65.00047 :difficulty 9.166955}
     {:stability 2.3163264 :stability-fast 0.89478546 :difficulty 9.615685}]))


(deftest recall-and-the-next-memory-are-the-reference-ones
  (are [state days r after-again after-hard after-good after-easy]
   (and (close? r (sut/recall sut/default-weights days state))
        (every? true?
                (map #(same-state? %1 (sut/next-state sut/default-weights state days %2))
                     [after-again after-hard after-good after-easy]
                     [:rating/again :rating/hard :rating/good :rating/easy])))
   {:stability 10 :stability-fast 8 :difficulty 5} 0 0.99999
    {:stability 2.2577837 :stability-fast 0.100594744 :difficulty 9.405907}
    {:stability 10.000857 :stability-fast 8 :difficulty 6.9766846}
    {:stability 10.00134 :stability-fast 8 :difficulty 4.9522996}
    {:stability 10.00134 :stability-fast 8 :difficulty 2.927915}

   {:stability 10 :stability-fast 8 :difficulty 5} 0.5 0.96410644
    {:stability 2.3247545 :stability-fast 0.32210514 :difficulty 9.260623}
    {:stability 13.15481 :stability-fast 70.479515 :difficulty 6.9766846}
    {:stability 14.930931 :stability-fast 109.39487 :difficulty 4.9522996}
    {:stability 14.930931 :stability-fast 118.20608 :difficulty 2.927915}

   {:stability 10 :stability-fast 8 :difficulty 5} 21 0.8774615
    {:stability 2.4947672 :stability-fast 0.4401131 :difficulty 8.909818}
    {:stability 21.447685 :stability-fast 133.01303 :difficulty 6.9766846}
    {:stability 27.8926 :stability-fast 210.87737 :difficulty 4.9522996}
    {:stability 27.8926 :stability-fast 228.5074 :difficulty 2.927915}

   {:stability 0.0001 :stability-fast 0.0001 :difficulty 10} 0.000011574074 0.34352458
    {:stability 0.0001 :stability-fast 0.0001 :difficulty 9.9023}
    {:stability 0.0002527925 :stability-fast 0.30059442 :difficulty 9.9023}
    {:stability 0.0003388129 :stability-fast 0.48775733 :difficulty 9.9023}
    {:stability 0.0003388129 :stability-fast 0.53013474 :difficulty 9.9023}

   {:stability 36500 :stability-fast 0.01 :difficulty 1} 36500 0.973712
    {:stability 390.85403 :stability-fast 0.0025306938 :difficulty 8.817285}
    {:stability 36500 :stability-fast 48.136513 :difficulty 4.636193}
    {:stability 36500 :stability-fast 78.11211 :difficulty 1}
    {:stability 36500 :stability-fast 84.89918 :difficulty 1}))


(deftest the-first-review-starts-the-memory-as-the-reference-does
  (are [rating expected] (same-state? expected (sut/initial-state sut/default-weights rating))
   :rating/again {:stability 0.1104 :stability-fast 0.08832 :difficulty 6.1686}
   :rating/hard  {:stability 2.2395 :stability-fast 1.7916001 :difficulty 5.261278}
   :rating/good  {:stability 3.9221 :stability-fast 3.13768 :difficulty 3.5307243}
   :rating/easy  {:stability 11.7841 :stability-fast 9.427279 :difficulty 1}))


(def ^:private steady-pace
  "Thirty answers that took 1 s plus 200 ms a character."
  (sut/pace (for [i (range 30)] {:answer-ms (+ 1000 (* 200 (+ 3 (mod i 7)))) :length (+ 3 (mod i 7))})))


(deftest the-pace-is-the-learners-own-line
  (testing "a steady learner: 1 s to start, 200 ms a character"
    (is (= 1000 (:a steady-pace)))
    (is (= 200 (:b steady-pace))))
  (testing "fewer than thirty timed answers: no pace yet"
    (is (nil? (sut/pace (repeat 29 {:answer-ms 2000 :length 5}))))))


(deftest a-review-is-rated-by-its-outcome-and-its-time
  (let [pace {:a 1000 :b 200 :fast 0.8 :slow 1.3}]
    (are [pace retained answer-ms length rating] (= rating (sut/rating pace retained answer-ms length))
     pace false 500   5 :rating/again
     pace true  ##NaN 5 :rating/good
     pace true  nil   5 :rating/good
     nil  true  500   5 :rating/good
     pace true  1500  5 :rating/easy
     pace true  2000  5 :rating/good
     pace true  2700  5 :rating/hard
     pace true  99000 5 :rating/hard)))


(deftest log-recall-follows-the-history
  (let [history (reduce retention/with-review
                        retention/empty-reviews
                        [{:id "r0" :created-at "2026-01-01T00:00:00.000Z" :retained true}
                         {:id "r1" :created-at "2026-01-02T00:00:00.000Z" :retained true}])
        word    {:created-at "2025-12-01T00:00:00.000Z" :value "Hund"}
        at      #(sut/log-recall sut/default-weights history word nil (utils/iso->ms %))]
    (testing "a word never reviewed has an unknown recall"
      (is (nil? (sut/log-recall sut/default-weights retention/empty-reviews word nil 0))))
    (testing "recall is a probability and fades with time"
      (is (< (at "2026-01-30T00:00:00.000Z") (at "2026-01-03T00:00:00.000Z") 0)))
    (testing "the fold is the reference memory: two Good reviews a day apart"
      (is (close? (js/Math.log (sut/recall sut/default-weights
                                           1
                                           (sut/next-state sut/default-weights
                                                           (sut/initial-state sut/default-weights :rating/good)
                                                           1
                                                           :rating/good)))
                  (at "2026-01-03T00:00:00.000Z"))))))


(deftest the-review-written-when-a-word-was-added-is-no-answer
  (let [added   "2026-01-01T00:00:00.000Z"
        history (fn [& reviews]
                  (reduce retention/with-review
                          retention/empty-reviews
                          (map-indexed #(assoc %2 :id (str "r" %1)) reviews)))
        now     (utils/iso->ms "2026-01-05T00:00:00.000Z")
        recall  #(sut/log-recall sut/default-weights %1 {:created-at %2 :value "Hund"} nil now)]
    (testing "alone, it leaves the memory unknown"
      (is (nil? (recall (history {:created-at "2026-01-01T00:00:01.000Z" :retained true}) added))))
    (testing "before a real answer, the memory starts at the answer"
      (is (= (recall (history {:created-at "2026-01-02T00:00:00.000Z" :retained true}) "2025-12-01T00:00:00.000Z")
             (recall (history {:created-at "2026-01-01T00:00:00.000Z" :retained true}
                              {:created-at "2026-01-02T00:00:00.000Z" :retained true})
                     added))))
    (are [review] (some? (recall (history review) added))
     {:created-at "2026-01-01T00:00:00.000Z" :retained false}
     {:created-at "2026-01-01T00:00:00.000Z" :retained true :answer-ms 2400}
     {:created-at "2026-01-01T00:00:05.000Z" :retained true})))

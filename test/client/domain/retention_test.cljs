(ns client.domain.retention-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [clojure.math :as math]
   [domain.retention :as sut]
   [utils :as utils]))


(defn- log
  "`reviews`, `{:created-at :retained}` each, as a review history."
  [reviews]
  (reduce sut/with-review
          sut/empty-reviews
          (map-indexed #(assoc %2 :id (str "r" %1)) reviews)))


(defn- log-recall
  [reviews now-ms]
  (sut/log-recall (log reviews) now-ms))


(defn- recall
  "The probability itself, which underflows where its log does not."
  [reviews now-ms]
  (math/exp (log-recall reviews now-ms)))


(deftest recall-is-a-probability-after-reviews
  (testing "recall is numeric and in (0, 1]"
    (let [reviews [{:created-at "2024-08-20T10:00:00.000Z" :retained true}
                   {:created-at "2024-08-20T10:05:00.000Z" :retained true}]
          recall  (recall reviews (utils/iso->ms "2024-08-20T10:10:00.000Z"))]
      (is (number? recall))
      (is (> recall 0))
      (is (<= recall 1)))))


(deftest recall-decays-with-time
  (testing "recall decays with time"
    (let [reviews [{:created-at "2024-08-20T10:00:00.000Z" :retained true}
                   {:created-at "2024-08-20T10:05:00.000Z" :retained true}]]
      (is (> (recall reviews (utils/iso->ms "2024-08-20T10:10:00.000Z"))
             (recall reviews (utils/iso->ms "2024-08-21T10:00:00.000Z")))))))


(def ^:private now-ms (utils/iso->ms "2024-08-20T10:00:00.000Z"))


(defn- last-reviewed-days-ago
  [days]
  [{:created-at (utils/ms->iso (- now-ms (* days 24 3600 1000))) :retained true}])


(deftest log-recall-separates-words-whose-recall-has-underflowed
  (testing "five and thirty days unreviewed both read as recall zero, yet their logs order them"
    (let [five-days   (last-reviewed-days-ago 5)
          thirty-days (last-reviewed-days-ago 30)]
      (is (zero? (recall five-days now-ms)))
      (is (zero? (recall thirty-days now-ms))
          "recall underflows to a flat zero past 3.8 days, so it cannot order these")
      (is (< (log-recall thirty-days now-ms) (log-recall five-days now-ms))
          "thirty days unreviewed is the less likely recalled of the two"))))


(deftest a-word-never-reviewed-has-an-unknown-recall
  (testing "no review at all: nothing is known, which is not a probability of zero"
    (is (nil? (log-recall [] now-ms)))))


(deftest log-recall-orders-the-words-recall-does
  (testing "where recall still resolves, its log orders the same way"
    (let [fresh (last-reviewed-days-ago 0.001)
          stale (last-reviewed-days-ago 0.01)]
      (is (> (recall fresh now-ms) (recall stale now-ms)))
      (is (> (log-recall fresh now-ms) (log-recall stale now-ms))))))


(deftest recall-ties-for-reviews-within-one-second
  (testing "elapsed time is truncated to seconds, so a batch added together ties"
    (let [at    (fn [iso] [{:created-at iso :retained true}])
          early (at "2024-08-20T09:59:00.100Z")
          late  (at "2024-08-20T09:59:00.900Z")]
      (is (= (log-recall early now-ms) (log-recall late now-ms))
          "800 ms apart is one recall — the tie `pick-vocab` has to break")
      (is (not= (log-recall early now-ms)
                (log-recall (at "2024-08-20T09:59:01.100Z") now-ms))
          "a full second apart does separate them"))))


(deftest a-clock-moved-backwards-reads-as-certain-recall
  (is (zero? (log-recall (last-reviewed-days-ago -1) now-ms))))


(deftest reviews-are-read-in-time-order-whatever-order-they-arrive-in
  (let [reviews [{:created-at "2024-08-19T10:00:00.000Z" :id "a" :retained true}
                 {:created-at "2024-08-18T10:00:00.000Z" :id "b" :retained false}
                 {:created-at "2024-08-19T22:00:00.000Z" :id "c" :retained true}]
        mixed   (reduce sut/with-review sut/empty-reviews reviews)
        ordered (reduce sut/with-review sut/empty-reviews (sort-by :created-at reviews))]
    (is (= (sut/log-recall ordered now-ms) (sut/log-recall mixed now-ms)))
    (is (= ["b" "a" "c"] (vec (:ids mixed))))
    (is (= ["a" "c"] (vec (:ids (sut/without-review mixed "b")))) "a review goes by its id")))


(deftest a-history-is-never-changed
  (let [held  (log [{:created-at "2024-08-19T10:00:00.000Z" :retained true}])
        later (sut/with-review held {:created-at "2024-08-19T11:00:00.000Z" :id "later" :retained true})]
    (is (= ["r0"] (vec (:ids held))))
    (is (= ["r0" "later"] (vec (:ids later))))
    (is (= ["r0"] (vec (:ids (sut/without-review later "later")))))
    (is (= ["r0" "later"] (vec (:ids later))))))

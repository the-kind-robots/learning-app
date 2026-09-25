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


(defn- urgency
  [reviews now-ms]
  (sut/urgency (log reviews) now-ms))


(defn- level
  [reviews now-ms]
  (sut/level (urgency reviews now-ms)))


(deftest retention-level-calculates-from-reviews
  (testing "retention percentage is numeric and bounded"
    (let [reviews [{:created-at "2024-08-20T10:00:00.000Z" :retained true}
                   {:created-at "2024-08-20T10:05:00.000Z" :retained true}]
          now-ms  (utils/iso->ms "2024-08-20T10:10:00.000Z")
          level   (level reviews now-ms)]
      (is (number? level))
      (is (> level 0))
      (is (<= level 100)))))


(deftest retention-level-decreases-over-time
  (testing "retention decays with time"
    (let [reviews     [{:created-at "2024-08-20T10:00:00.000Z" :retained true}
                       {:created-at "2024-08-20T10:05:00.000Z" :retained true}]
          soon-ms     (utils/iso->ms "2024-08-20T10:10:00.000Z")
          later-ms    (utils/iso->ms "2024-08-21T10:00:00.000Z")
          level-soon  (level reviews soon-ms)
          level-later (level reviews later-ms)]
      (is (> level-soon level-later)))))


(def ^:private now-ms (utils/iso->ms "2024-08-20T10:00:00.000Z"))


(defn- last-reviewed-days-ago
  [days]
  [{:created-at (utils/ms->iso (- now-ms (* days 24 3600 1000))) :retained true}])


(deftest urgency-separates-words-whose-retention-has-underflowed
  (testing "five and thirty days unreviewed both read as retention zero, yet rank"
    (let [five-days   (last-reviewed-days-ago 5)
          thirty-days (last-reviewed-days-ago 30)]
      (is (zero? (level five-days now-ms)))
      (is (zero? (level thirty-days now-ms))
          "retention underflows to a flat zero past 3.8 days, so it cannot order these")
      (is (> (urgency thirty-days now-ms) (urgency five-days now-ms))
          "thirty days unreviewed is the more due of the two"))))


(deftest urgency-ranks-a-never-reviewed-word-highest
  (testing "a word with no review at all is as due as a word gets"
    (is (> (urgency [] now-ms)
           (urgency (last-reviewed-days-ago 3650) now-ms)))))


(deftest urgency-orders-the-same-words-retention-does
  (testing "where retention still resolves, urgency is its mirror"
    (let [fresh (last-reviewed-days-ago 0.001)
          stale (last-reviewed-days-ago 0.01)]
      (is (> (level fresh now-ms) (level stale now-ms)))
      (is (< (urgency fresh now-ms) (urgency stale now-ms))))))


(deftest urgency-ties-for-reviews-within-one-second
  (testing "elapsed time is truncated to seconds, so a batch added together ties"
    (let [at    (fn [iso] [{:created-at iso :retained true}])
          early (at "2024-08-20T09:59:00.100Z")
          late  (at "2024-08-20T09:59:00.900Z")]
      (is (= (urgency early now-ms) (urgency late now-ms))
          "800 ms apart is one urgency — the tie `pick-vocab` has to break")
      (is (not= (urgency early now-ms)
                (urgency (at "2024-08-20T09:59:01.100Z") now-ms))
          "a full second apart does separate them"))))


(deftest retention-level-is-the-image-of-urgency
  (testing "a reviewed word: the level is exactly 100 * exp(- urgency)"
    (let [reviewed (last-reviewed-days-ago 0.01)]
      (is (= (level reviewed now-ms)
             (* 100 (math/exp (- (urgency reviewed now-ms))))))))
  (testing "a word never reviewed: urgency is infinite and the level is zero"
    (is (= ##Inf (urgency [] now-ms)))
    (is (zero? (level [] now-ms))))
  (testing "a clock that moved backwards: negative urgency still caps at 100"
    (let [future-review (last-reviewed-days-ago -1)]
      (is (neg? (urgency future-review now-ms)))
      (is (= 100 (level future-review now-ms))))))


(deftest reviews-are-read-in-time-order-whatever-order-they-arrive-in
  (let [reviews [{:created-at "2024-08-19T10:00:00.000Z" :id "a" :retained true}
                 {:created-at "2024-08-18T10:00:00.000Z" :id "b" :retained false}
                 {:created-at "2024-08-19T22:00:00.000Z" :id "c" :retained true}]
        mixed   (reduce sut/with-review sut/empty-reviews reviews)
        ordered (reduce sut/with-review sut/empty-reviews (sort-by :created-at reviews))]
    (is (= (sut/urgency ordered now-ms) (sut/urgency mixed now-ms)))
    (is (= ["b" "a" "c"] (vec (:ids mixed))))
    (is (= ["a" "c"] (vec (:ids (sut/without-review mixed "b")))) "a review goes by its id")))


(deftest a-history-is-never-changed
  (let [held  (log [{:created-at "2024-08-19T10:00:00.000Z" :retained true}])
        later (sut/with-review held {:created-at "2024-08-19T11:00:00.000Z" :id "later" :retained true})]
    (is (= ["r0"] (vec (:ids held))))
    (is (= ["r0" "later"] (vec (:ids later))))
    (is (= ["r0"] (vec (:ids (sut/without-review later "later")))))
    (is (= ["r0" "later"] (vec (:ids later))))))

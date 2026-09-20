(ns client.domain.retention-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [domain.retention :as sut]
   [utils :as utils]))


(deftest retention-level-calculates-from-reviews
  (testing "retention percentage is numeric and bounded"
    (let [reviews [{:created-at "2024-08-20T10:00:00.000Z" :retained true}
                   {:created-at "2024-08-20T10:05:00.000Z" :retained true}]
          now-ms  (utils/iso->ms "2024-08-20T10:10:00.000Z")
          level   (sut/retention-level reviews now-ms)]
      (is (number? level))
      (is (> level 0))
      (is (<= level 100)))))


(deftest retention-level-decreases-over-time
  (testing "retention decays with time"
    (let [reviews     [{:created-at "2024-08-20T10:00:00.000Z" :retained true}
                       {:created-at "2024-08-20T10:05:00.000Z" :retained true}]
          soon-ms     (utils/iso->ms "2024-08-20T10:10:00.000Z")
          later-ms    (utils/iso->ms "2024-08-21T10:00:00.000Z")
          level-soon  (sut/retention-level reviews soon-ms)
          level-later (sut/retention-level reviews later-ms)]
      (is (> level-soon level-later)))))


(def ^:private now-ms (utils/iso->ms "2024-08-20T10:00:00.000Z"))


(defn- last-reviewed-days-ago
  [days]
  [{:created-at (utils/ms->iso (- now-ms (* days 24 3600 1000))) :retained true}])


(deftest urgency-separates-words-whose-retention-has-underflowed
  (testing "five and thirty days unreviewed both read as retention zero, yet rank"
    (let [five-days   (last-reviewed-days-ago 5)
          thirty-days (last-reviewed-days-ago 30)]
      (is (zero? (sut/retention-level five-days now-ms)))
      (is (zero? (sut/retention-level thirty-days now-ms))
          "retention underflows to a flat zero past 3.8 days, so it cannot order these")
      (is (> (sut/urgency thirty-days now-ms) (sut/urgency five-days now-ms))
          "thirty days unreviewed is the more due of the two"))))


(deftest urgency-ranks-a-never-reviewed-word-highest
  (testing "a word with no review at all is as due as a word gets"
    (is (> (sut/urgency [] now-ms)
           (sut/urgency (last-reviewed-days-ago 3650) now-ms)))))


(deftest urgency-orders-the-same-words-retention-does
  (testing "where retention still resolves, urgency is its mirror"
    (let [fresh (last-reviewed-days-ago 0.001)
          stale (last-reviewed-days-ago 0.01)]
      (is (> (sut/retention-level fresh now-ms) (sut/retention-level stale now-ms)))
      (is (< (sut/urgency fresh now-ms) (sut/urgency stale now-ms))))))

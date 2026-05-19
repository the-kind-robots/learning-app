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

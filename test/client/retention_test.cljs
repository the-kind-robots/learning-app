(ns client.retention-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [client.support.db-fixtures :as db-fixtures]
   [cljs.test :refer-macros [deftest is testing use-fixtures]]
   [db :as db]
   [domain.retention :as domain]
   [repo.retention :as sut]
   [utils :as utils]))


(def user-db-name (db-fixtures/db-name "client.retention-test.user"))


(use-fixtures :each (db-fixtures/db-fixture-multi [user-db-name]))


(defn- with-test-dbs
  [f]
  (db-fixtures/with-test-dbs
   [user-db-name]
   (fn [[user-db]]
     (f {:user/db user-db}))))


(deftest retention-level-calculates-from-reviews
  (testing "retention percentage is numeric and bounded"
    (let [reviews [{:created-at "2024-08-20T10:00:00.000Z" :retained true}
                   {:created-at "2024-08-20T10:05:00.000Z" :retained true}]
          now-ms  (utils/iso->ms "2024-08-20T10:10:00.000Z")
          level   (domain/retention-level reviews now-ms)]
      (is (number? level))
      (is (> level 0))
      (is (<= level 100)))))


(deftest retention-level-decreases-over-time
  (testing "retention decays with time"
    (let [reviews     [{:created-at "2024-08-20T10:00:00.000Z" :retained true}
                       {:created-at "2024-08-20T10:05:00.000Z" :retained true}]
          soon-ms     (utils/iso->ms "2024-08-20T10:10:00.000Z")
          later-ms    (utils/iso->ms "2024-08-21T10:00:00.000Z")
          level-soon  (domain/retention-level reviews soon-ms)
          level-later (domain/retention-level reviews later-ms)]
      (is (> level-soon level-later)))))


(deftest levels-returns-row-per-requested-word
  (async-testing "`levels` returns rows for all requested word ids"
    (with-test-dbs
     (^:async fn [dbs]
       (let [user-db (:user/db dbs)]
         (await (db/insert user-db
                           {:_id        "review-1"
                            :type       "review"
                            :word-id    "word-1"
                            :retained   true
                            :created-at "2024-08-20T10:00:00.000Z"}))
         (await (db/insert user-db
                           {:_id        "review-2"
                            :type       "review"
                            :word-id    "word-1"
                            :retained   true
                            :created-at "2024-08-20T10:05:00.000Z"}))
         (let [rows (await (sut/levels dbs
                                       ["word-1" "word-2"]
                                       (utils/iso->ms "2024-08-20T10:10:00.000Z")))]
           (is (= 2 (count rows)))
           (is (= ["word-1" "word-2"] (mapv :word-id rows)))
           (is (number? (:retention-level (first rows))))
           (is (> (:retention-level (first rows)) 0))
           (is (= 0 (:retention-level (second rows))))))))))


(deftest level-computes-from-reviews
  (async-testing "`level` computes retention from review documents"
    (with-test-dbs
     (^:async fn [dbs]
       (let [user-db (:user/db dbs)]
         (await (db/insert user-db
                           {:_id        "review-1"
                            :type       "review"
                            :word-id    "word-1"
                            :retained   true
                            :created-at "2024-08-20T10:00:00.000Z"}))
         (await (db/insert user-db
                           {:_id        "review-2"
                            :type       "review"
                            :word-id    "word-1"
                            :retained   true
                            :created-at "2024-08-20T10:05:00.000Z"}))
         (let [level (await (sut/level dbs "word-1" (utils/iso->ms "2024-08-20T10:10:00.000Z")))]
           (is (number? level))
           (is (> level 0))))))))

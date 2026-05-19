(ns client.vocabulary-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.time :as time]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [ports.progress-store :as progress-store]
   [use-cases.vocabulary :as sut]
   [utils :as utils]))


(def user-db-name (db-fixtures/db-name "client.vocabulary-test.user"))


(def device-db-name (db-fixtures/db-name "client.vocabulary-test.device"))


(use-fixtures :each (db-fixtures/db-fixture-multi [user-db-name device-db-name]))


(defn- with-test-dbs
  [f]
  (db-fixtures/with-test-dbs
   [user-db-name device-db-name]
   (^:async fn [[user-db device-db]]
     (with-redefs [utils/now-iso time/now-iso
                   utils/now-ms  time/now-ms]
       (await (f {:user/db user-db :device/db device-db}))))))


(defn- test-capabilities
  [dbs]
  {:progress-store
   (progress-store/start! {:db    dbs
                           :clock {:clock/now-iso time/now-iso
                                   :clock/now-ms  time/now-ms}})})


(deftest add-creates-vocab-and-initial-review
  (async-testing "`add!` creates vocab and initial review"
    (with-test-dbs
     (^:async fn [dbs]
       (let [{:keys [word-id created?]} (await (sut/add! (test-capabilities dbs) "der Hund" "пёс"))
             vocabs  (await (db-queries/fetch-by-type (:user/db dbs) "vocab"))
             reviews (await (db-queries/fetch-by-type (:user/db dbs) "review"))]
         (is (string? word-id))
         (is (true? created?))
         (is (= 1 (count vocabs)))
         (is (= 1 (count reviews)))
         (is (= "der Hund" (:value (first vocabs))))
         (is (= word-id (:word-id (first reviews))))
         (is (true? (:retained (first reviews)))))))))


(deftest list-returns-summaries-with-retention
  (async-testing "`list` returns summaries with retention"
    (with-test-dbs
     (^:async fn [dbs]
       (await (sut/add! (test-capabilities dbs) "der Hund" "пёс"))
       (await (sut/add! (test-capabilities dbs) "die Katze" "кот"))
       (let [{:keys [words total]} (await (sut/list (test-capabilities dbs) {}))]
         (is (= 2 (count words)))
         (is (= 2 total))
         (is (every? :retention-level words)))))))


(deftest list-filters-and-paginates
  (async-testing "`list` supports search and limit"
    (with-test-dbs
     (^:async fn [dbs]
       (await (sut/add! (test-capabilities dbs) "der Hund" "пёс"))
       (await (sut/add! (test-capabilities dbs) "die Katze" "кот"))
       (await (sut/add! (test-capabilities dbs) "der Vogel" "птица"))
       (let [{:keys [words total]} (await (sut/list (test-capabilities dbs) {:search "Hund" :limit 1}))]
         (is (= 3 total))
         (is (= 1 (count words)))
         (is (= "der Hund" (:value (first words)))))))))


(deftest count-uses-db-doc-count-as-limit
  (async-testing "`count` uses db/info doc-count for single find"
    (let [info-calls (atom 0)
          find-calls (atom [])
          doc-count  26]
      (with-redefs [db/info
                    (fn [_]
                      (swap! info-calls inc)
                      (js/Promise.resolve {:doc-count doc-count}))

                    db/find
                    (fn [_ query]
                      (swap! find-calls conj query)
                      (js/Promise.resolve {:docs (vec (repeat doc-count {:type "vocab"}))}))]
        (let [cnt (await (sut/count (test-capabilities {:user/db :fake})))
              q   (first @find-calls)]
          (is (= doc-count cnt))
          (is (= 1 @info-calls))
          (is (= doc-count (:limit q)))
          (is (nil? (:skip q))))))))


(deftest list-and-count-return-all-words-beyond-25
  (async-testing "`list` and `count` return full data when db has more than 25 words"
    (with-test-dbs
     (^:async fn [dbs]
       (await (js/Promise.all
               (into-array (map (fn [i] (sut/add! (test-capabilities dbs) (str "word-" i) (str "перевод-" i)))
                                (range 30)))))
       (let [cnt (await (sut/count (test-capabilities dbs)))
             {:keys [words total]} (await (sut/list (test-capabilities dbs) {}))]
         (is (= 30 cnt))
         (is (= 30 total))
         (is (= 30 (count words))))))))


(deftest get-returns-summary
  (async-testing "`get` returns word summary"
    (with-test-dbs
     (^:async fn [dbs]
       (let [{:keys [word-id]} (await (sut/add! (test-capabilities dbs) "der Hund" "пёс"))
             result (await (sut/get (test-capabilities dbs) word-id))]
         (is (= word-id (:_id result)))
         (is (= "der Hund" (:value result)))
         (is (= "пёс" (-> result :translation first :value)))
         (is (number? (:retention-level result))))))))


(deftest update-updates-and-returns-summary
  (async-testing "`update!` modifies and returns summary"
    (with-test-dbs
     (^:async fn [dbs]
       (let [{:keys [word-id]} (await (sut/add! (test-capabilities dbs) "der Hund" "пёс"))
             result (await (sut/update! (test-capabilities dbs) word-id "лиса"))]
         (is (= word-id (:_id result)))
         (is (= "der Hund" (:value result)))
         (is (= "лиса" (-> result :translation first :value))))))))


(deftest delete-removes-word-related-docs
  (async-testing "`delete!` removes word, reviews and examples"
    (with-test-dbs
     (^:async fn [dbs]
       (let [{:keys [word-id]} (await (sut/add! (test-capabilities dbs) "der Hund" "пёс"))]
         (await (sut/add-review (test-capabilities dbs) word-id true "пёс"))
         (await (db/insert (:device/db dbs) {:type "example" :word-id word-id :value "Der Hund läuft"}))
         (await (sut/delete! (test-capabilities dbs) word-id))
         (let [vocabs   (await (db-queries/fetch-by-type (:user/db dbs) "vocab"))
               reviews  (await (db-queries/fetch-by-type (:user/db dbs) "review"))
               examples (await (db-queries/fetch-by-type (:device/db dbs) "example"))]
           (is (empty? vocabs))
           (is (empty? reviews))
           (is (empty? examples))))))))


(deftest add-review-creates-review-document
  (async-testing "`add-review` creates review document"
    (with-test-dbs
     (^:async fn [dbs]
       (let [{:keys [word-id]} (await (sut/add! (test-capabilities dbs) "der Hund" "пёс"))]
         (await (sut/add-review (test-capabilities dbs) word-id false "собака"))
         (let [reviews (await (db-queries/fetch-by-type (:user/db dbs) "review"))]
           (is (= 2 (count reviews)))
           (is (= 1 (count (filter (fn [r] (false? (:retained r))) reviews))))))))))

(ns client.vocabulary-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.learner.memory :as memory]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.db-seed :as db-seed]
   [client.support.learner :as learner]
   [client.support.time :as time]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [domain.retention :as retention]
   [ports.learner :as ports]
   [use-cases.vocabulary :as sut]
   [utils :as utils]))


(def user-db-name (db-fixtures/db-name "client.vocabulary-test.user"))


(def device-db-name (db-fixtures/db-name "client.vocabulary-test.device"))


(use-fixtures :each (db-fixtures/db-fixture-multi [user-db-name device-db-name]))


(def ^:private clock
  {:clock/now-iso time/now-iso
   :clock/now-ms  time/now-ms})


(defn- with-test-dbs
  "Calls `f` with the test databases, under which `::capabilities` is
   what the use cases are handed: the learner port over those databases,
   and the main card active."
  [f]
  (db-fixtures/with-test-dbs
   [user-db-name device-db-name]
   (^:async fn
    [[user-db device-db]]
    (with-redefs [utils/now-iso time/now-iso
                  utils/now-ms  time/now-ms]
      (let [dbs {:device/db device-db :user/db user-db}]
        (await (learner/with-learner
                dbs
                clock
                (fn [{learner-port :learner}]
                  (f (assoc dbs
                            ::capabilities
                            {:clock   clock
                             :learner (assoc learner-port :learner/active-collection (fn [] nil))}))))))))))


(defn- test-capabilities
  [dbs]
  (::capabilities dbs))


(defn- ^:async list-of
  "`rows` over the learner's data as memory would have `dbs` now. `:word-ids`
   become a collection holding them."
  [dbs {:keys [word-ids] :as opts}]
  (let [memory (cond-> (await (db-seed/memory-of (:user/db dbs)))
                 word-ids (memory/with-docs [{:_id      "collection:listed"
                                              :_rev     "1-a"
                                              :type     "collection"
                                              :name     "listed"
                                              :word-ids (vec word-ids)}]))]
    (sut/rows ports/reads
              memory
              (cond-> (dissoc opts :word-ids)
                word-ids (assoc :collection (get-in memory [:collections "collection:listed"])))
              (time/now-ms))))


(defn- ^:async count-of
  [dbs]
  (count (:words (await (db-seed/memory-of (:user/db dbs))))))


(deftest adding-a-word-stores-it-with-a-first-review
  (async-testing "`add!` creates vocab and initial review"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [{:keys [word-id created?]} (await (sut/add! (test-capabilities dbs) "der Hund" "пёс" :word))
            vocabs  (await (db-queries/fetch-by-type (:user/db dbs) "vocab"))
            reviews (await (db-queries/fetch-by-type (:user/db dbs) "review"))]
        (is (string? word-id))
        (is (true? created?))
        (is (= 1 (count vocabs)))
        (is (= 1 (count reviews)))
        (is (= "der Hund" (:value (first vocabs))))
        (is (= word-id (:word-id (first reviews))))
        (is (true? (:retained (first reviews)))))))))


(deftest a-vocabulary-of-more-than-a-page-is-listed-and-counted-whole
  (async-testing "`list` and `count` return full data when db has more than 25 words"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (js/Promise.all
              (into-array (map (fn [i] (sut/add! (test-capabilities dbs) (str "word-" i) (str "перевод-" i) :word))
                               (range 30)))))
      (let [cnt (await (count-of dbs))
            {:keys [words total]} (await (list-of dbs {}))]
        (is (= 30 cnt))
        (is (= 30 total))
        (is (= 30 (count words))))))))


(deftest deleting-a-word-keeps-its-reviews-and-examples
  (async-testing "`delete!` removes the word; its reviews and examples stay"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [{:keys [word-id]} (await (sut/add! (test-capabilities dbs) "der Hund" "пёс" :word))]
        (await (sut/add-review (test-capabilities dbs) word-id true "пёс"))
        (await (db/insert (:user/db dbs) {:type "example" :word-id word-id :value "Der Hund läuft"}))
        (let [reviews (await (db-queries/fetch-by-type (:user/db dbs) "review"))]
          (await (sut/delete! (test-capabilities dbs) word-id))
          (is (empty? (await (db-queries/fetch-by-type (:user/db dbs) "vocab"))))
          (is (= (map :_id reviews) (map :_id (await (db-queries/fetch-by-type (:user/db dbs) "review")))))
          (is (= 1 (count (await (db-queries/fetch-by-type (:user/db dbs) "example")))))))))))


(defn- ^:async seed-reviews!
  "Seven reviews per word over the week before `test-now`, alternating
   retained, so every word lands on a different retention level."
  [dbs word-ids]
  (await (js/Promise.all
          (into-array
           (for [[n word-id] (map-indexed vector word-ids)
                 k (range 7)]
             (db/insert (:user/db dbs)
                        {:type       "review"
                         :word-id    word-id
                         :retained   (even? (+ n k))
                         :created-at (utils/ms->iso (- (time/now-ms)
                                                       (* (+ 1 n k) 6 3600 1000)))}))))))


(deftest retention-is-computed-from-each-words-own-reviews-past-a-page
  (async-testing "`list` retention equals retention over each word's own reviews when reviews exceed one page"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [word-ids (mapv #(str "vocab:wort-" %) (range 5))]
        (await (js/Promise.all
                (into-array
                 (map (fn [word-id]
                        (db/insert (:user/db dbs)
                                   {:_id         word-id
                                    :type        "vocab"
                                    :value       (subs word-id 6)
                                    :translation [{:lang "ru" :value "слово"}]
                                    :created-at  time/test-now-iso
                                    :modified-at time/test-now-iso}))
                      word-ids))))
        (await (seed-reviews! dbs word-ids))
        (let [reviews (await (db-queries/fetch-by-type (:user/db dbs) "review"))
              expected (->> (group-by :word-id reviews)
                            (map (fn [[word-id reviews]]
                                   (let [log (reduce retention/with-review
                                                     retention/empty-reviews
                                                     (map #(assoc % :id (:_id %)) reviews))]
                                     [word-id (retention/level (retention/urgency log (time/now-ms)))])))
                            (into {}))
              {:keys [words]} (await (list-of dbs {}))
              actual (into {} (map (juxt :id :retention-level)) words)
              {subset :words} (await (list-of dbs {:word-ids (take 2 word-ids)}))]
          (is (= 35 (count reviews)))
          (is (= 5 (count (distinct (vals expected)))))
          (is (= expected actual))
          (is (= (select-keys expected (take 2 word-ids))
                 (into {} (map (juxt :id :retention-level)) subset)))))))))



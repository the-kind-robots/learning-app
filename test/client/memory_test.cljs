(ns client.memory-test
  "The learner's data in memory as a projection of the local databases
   (ADR-0016): loaded, and following the feed. How memory takes this app's
   own writes is `client.learner-test`."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.learner.memory :as sut]
   [adapters.learner.loader :as loader]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-seed :as db-seed]
   [client.support.learner :as learner]
   [client.support.wait :as wait]
   [cljs.test :refer-macros [deftest is testing use-fixtures]]
   [db :as db]
   [domain.retention :as retention]))


(def user-db-name (db-fixtures/db-name "client.memory-test.user"))


(def device-db-name (db-fixtures/db-name "client.memory-test.device"))


(use-fixtures :each (db-fixtures/db-fixture-multi [user-db-name device-db-name]))


(defn- with-test-dbs
  [f]
  (db-fixtures/with-test-dbs
   [user-db-name device-db-name]
   (fn [[user-db device-db]]
     (f {:device/db device-db
         :user/db   user-db}))))


(defn- word
  "The word `id` in `memory`."
  [memory id]
  (some #(when (= id (:id %)) %) (vals (:words memory))))


(defn- review-ids
  [memory word-id]
  (some->> (get-in memory [:slot-of word-id]) (nth (:cards memory)) :reviews :ids vec))


(def ^:private hund
  {:_id "vocab:der hund" :type "vocab" :value "der Hund" :translation [{:lang "ru" :value "пёс"}]})


(deftest a-document-is-projected-once-per-revision
  (let [doc     (assoc hund :_rev "1-a")
        once    (sut/with-docs sut/empty-memory [doc])
        twice   (sut/with-docs once [doc])
        deleted (sut/with-docs once [{:_deleted true :_id "vocab:der hund" :_rev "2-b"}])]
    (is (= "der Hund" (:value (word once "vocab:der hund"))))
    (is (= "der hund\nпёс" (:search (word once "vocab:der hund")))
        "the search text is normalised once, value and translations a line each")
    (is (identical? once twice) "the revision memory has changes nothing")
    (is (nil? (word deleted "vocab:der hund")) "a deletion removes the word")
    (is (nil? (:_rev (word once "vocab:der hund"))) "no storage name reaches the entity")))


(deftest a-review-tombstone-leaves-its-word
  (testing "a deleted review carries no word id; memory's own record says which word it was"
    (let [review {:_id        "r1"
                  :_rev       "1-a"
                  :type       "review"
                  :word-id    "vocab:der hund"
                  :created-at "2026-01-01T00:00:00Z"
                  :retained   true}
          memory (sut/with-docs sut/empty-memory [review])
          gone   (sut/with-docs memory [{:_deleted true :_id "r1" :_rev "2-b"}])]
      (is (= ["r1"] (review-ids memory "vocab:der hund")))
      (is (empty? (review-ids gone "vocab:der hund"))))))


(deftest same-second-reviews-give-one-retention-whatever-their-order
  (testing "reviews tied on time are taken in id order, not arrival order"
    (let [word {:_id "vocab:w" :_rev "1-a" :type "vocab" :value "w" :translation []}
          reviews
          [{:_id "r1" :_rev "1-a" :type "review" :word-id "vocab:w" :created-at "2026-01-01T00:00:00Z" :retained true}
           {:_id "r2" :_rev "1-a" :type "review" :word-id "vocab:w" :created-at "2026-01-01T00:00:00Z" :retained false}
           {:_id "r0" :_rev "1-a" :type "review" :word-id "vocab:w" :created-at "2025-12-01T00:00:00Z" :retained true}]
          state (fn [docs]
                  (let [memory (sut/with-docs sut/empty-memory docs)]
                    (retention/urgency (:reviews (nth (:cards memory) (get-in memory [:slot-of "vocab:w"])))
                                       1790000000000)))]
      (is (= (state (cons word reviews))
             (state (cons word (reverse reviews))))))))


(deftest a-document-memory-cannot-take-is-left-out
  (let [memory (sut/with-docs sut/empty-memory
                              [{:_id "vocab:odd" :_rev "1-a" :type "vocab" :value 42 :translation 7}
                               (assoc hund :_rev "1-a")])]
    (is (nil? (sut/word memory "vocab:odd")))
    (is (some? (sut/word memory "vocab:der hund")) "the documents after it are taken")))


(deftest memory-loads-both-databases-and-follows-the-feed
  (async-testing "load, then documents written by anything else arrive through the feed"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "vocab:der hund" :value "der Hund" :translation "пёс"}]))
      (await (db-seed/seed-examples! (:device/db dbs)
                                     [{:_id         "example-1"
                                       :word-id     "vocab:der hund"
                                       :word        "der Hund"
                                       :value       "Der Hund schläft."
                                       :translation "Пёс спит"}]))
      (let [{:keys [stop store]} (await (learner/started dbs {}))
            memory #(:learner/memory @store)]
        (is (= ["vocab:der hund"] (map :id (sut/words (memory)))))
        (is (= 1 (count (review-ids (memory) "vocab:der hund"))))
        (is (= ["example-1"] (map :id (sut/examples-of (memory) ["vocab:der hund"]))))
        ;; Written past `db.pouch`, as a replication or another tab writes.
        (await (db/insert (:user/db dbs)
                          {:_id         "vocab:die katze"
                           :type        "vocab"
                           :value       "die Katze"
                           :translation [{:lang "ru" :value "кошка"}]}))
        (await (wait/until #(sut/word (memory) "vocab:die katze")))
        (let [{doc :_rev} (await (db/get (:user/db dbs) "vocab:die katze"))]
          (await (db/remove (:user/db dbs) {:_id "vocab:die katze" :_rev doc})))
        (await (wait/until #(nil? (sut/word (memory) "vocab:die katze"))))
        (is (nil? (sut/word (memory) "vocab:die katze")))
        (stop))))))


(deftest memory-is-loaded-in-one-go
  (async-testing "the load hands memory over once, with everything both databases hold"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "vocab:der hund" :value "der Hund" :translation "пёс"}]))
      (await (db/insert (:user/db dbs) {:_id "coll-tiere" :type "collection" :name "Tiere" :word-ids ["vocab:der hund"]}))
      (let [effects (atom [])
            stop    (await (loader/start! dbs
                                          (atom {})
                                          (fn [[[effect memory]]] (swap! effects conj [effect memory]))))
            [[effect memory]] @effects]
        (is (= 1 (count @effects)))
        (is (= :effect/memory-loaded effect))
        (is (some? (sut/word memory "vocab:der hund")))
        (is (= 1 (count (review-ids memory "vocab:der hund"))))
        (is (some? (sut/collection memory "coll-tiere")))
        (stop))))))

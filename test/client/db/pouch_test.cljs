(ns client.db.pouch-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [client.support.db-fixtures :as db-fixtures]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [db.pouch :as sut]))


(def local-name (db-fixtures/db-name "client.db.pouch-test.local"))


(def remote-name (db-fixtures/db-name "client.db.pouch-test.remote"))


(use-fixtures :each (db-fixtures/db-fixture-multi [local-name remote-name]))


(defn- ^:async sync-pass!
  "One replication pass through the same wrapper `sync-once!` uses, with the
   same filter; resolves when it completes."
  [local remote]
  (await (js/Promise.
          (fn [resolve reject]
            (doto (db/sync local {:filter sut/user-doc? :live false :remote-url (.-name ^js remote)})
              (.on "complete" resolve)
              (.on "error" reject))))))


(defn- ^:async ids
  [db]
  (let [{rows :rows} (await (db/all-docs db))]
    (set (map :id rows))))


(deftest design-documents-stay-behind-on-sync
  (async-testing "a sync pass carries user documents both ways and no _design/ document either way"
    ;; The remote is opened bare: the fixture would give it the design
    ;; documents of its own, and the test is about which ones arrive.
    (db-fixtures/with-test-db
      local-name
      (^:async fn
       [local]
       (let [remote (db/use remote-name)]
         (await (db/insert local {:_id "vocab:hund" :type "vocab" :value "Hund"}))
         (await (db/insert remote {:_id "_design/remote-only" :views {}}))
         (await (db/insert remote {:_id "vocab:katze" :type "vocab" :value "Katze"}))
         (await (sync-pass! local remote))
         (let [local-ids  (await (ids local))
               remote-ids (await (ids remote))]
           (is (contains? local-ids "_design/reviews-by-word"))
           (is (contains? local-ids "vocab:katze"))
           (is (not (contains? local-ids "_design/remote-only")))
           (is (contains? remote-ids "vocab:hund"))
           (is (empty? (filter #(re-find #"^_design/" %) (disj remote-ids "_design/remote-only"))))))))))


(deftest the-reviews-view-answers-per-word
  (async-testing "reviews-by-word rows carry [created-at retained] keyed by word id"
    (db-fixtures/with-test-db
      local-name
      (^:async fn
       [local]
       (await (sut/prepare-user-db! local))
       (await (db/insert local
                         {:type "review" :word-id "vocab:a" :retained true :created-at "2024-01-01T00:00:00.000Z"}))
       (await (db/insert local
                         {:type "review" :word-id "vocab:a" :retained false :created-at "2024-01-02T00:00:00.000Z"}))
       (await (db/insert local
                         {:type "review" :word-id "vocab:b" :retained true :created-at "2024-01-03T00:00:00.000Z"}))
       (await (db/insert local {:type "vocab" :_id "vocab:a" :value "a"}))
       (let [{rows :rows} (await (db/query local sut/reviews-by-word-view {:keys ["vocab:a"]}))]
         (is (= #{["vocab:a" ["2024-01-01T00:00:00.000Z" true]]
                  ["vocab:a" ["2024-01-02T00:00:00.000Z" false]]}
                (set (map (juxt :key :value) rows)))))))))

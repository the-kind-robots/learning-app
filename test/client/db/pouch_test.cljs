(ns client.db.pouch-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.reviews :as reviews]
   [adapters.words :as words]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.schemas :as schemas]
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


(deftest a-schema-gives-its-database-indexes-and-views
  (async-testing "prepare! installs every declared index and view once, and a changed map replaces the stored one"
    (db-fixtures/with-test-db
      local-name
      (^:async fn
       [local]
       (let [names   (fn ^:async f []
                       (let [{:keys [indexes]} (js->clj (await (.getIndexes ^js local)) :keywordize-keys true)]
                         (set (map :name indexes))))
             rev-of  (fn ^:async f [id] (:_rev (await (db/get local id))))
             changed (assoc-in (first schemas/all) [:views "vocab-preview" :map] "function (doc) { emit(doc._id); }")]
         (is (contains? (await (names)) "by-type"))
         (is (contains? (await (names)) "by-type-run-at-created-at"))
         (let [rev-1 (await (rev-of "_design/reviews-by-word"))]
           (await (sut/prepare! local schemas/all))
           (is (= rev-1 (await (rev-of "_design/reviews-by-word"))) "an unchanged view is not rewritten")
           (await (sut/prepare! local [changed]))
           (is (not= rev-1 (await (rev-of "_design/vocab-preview"))) "a changed map is")))))))


(deftest the-reviews-view-answers-per-word
  (async-testing "reviews-by-word rows carry [created-at retained] keyed by word id"
    (db-fixtures/with-test-db
      local-name
      (^:async fn
       [local]
       (let [dbs {:user/db local}]
         (await (sut/insert dbs
                            reviews/schema
                            {:word-id "vocab:a" :retained true :created-at "2024-01-01T00:00:00.000Z"}))
         (await (sut/insert dbs
                            reviews/schema
                            {:word-id "vocab:a" :retained false :created-at "2024-01-02T00:00:00.000Z"}))
         (await (sut/insert dbs
                            reviews/schema
                            {:word-id "vocab:b" :retained true :created-at "2024-01-03T00:00:00.000Z"}))
         (await (sut/insert dbs words/schema {:_id "vocab:a" :value "a"}))
         (let [by-word  (await (reviews/reviews-by-word dbs ["vocab:a"]))
               previews (await (words/previews dbs nil))]
           (is (= ["vocab:a"] (keys by-word)))
           (is (= #{{:word-id "vocab:a" :created-at "2024-01-01T00:00:00.000Z" :retained true}
                    {:word-id "vocab:a" :created-at "2024-01-02T00:00:00.000Z" :retained false}}
                  (set (by-word "vocab:a"))))
           (is (= [{:_id "vocab:a" :kind nil :translation nil :value "a"}] previews))
           (is (= [] (await (words/previews dbs ["vocab:none"]))))))))))

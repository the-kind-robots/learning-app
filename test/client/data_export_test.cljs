(ns client.data-export-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.data-export :as sut]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [client.support.db-fixtures :as db-fixtures]
   [db :as db]))


(def user-db-name (db-fixtures/db-name "client.data-export-test.user"))


(use-fixtures :each (db-fixtures/db-fixture user-db-name))


(def ^:private seed-docs
  [{:_id "vocab:hund" :type "vocab" :value "Hund" :translation ["собака"] :modified-at "2026-01-01"}
   {:_id "review:1" :type "review" :word-id "vocab:hund" :retained true}
   {:_id "collection:travel" :type "collection" :name "Travel"}
   {:_id "example:1" :type "example" :word-id "vocab:hund" :value "Der Hund schläft"}])


(defn- with-seeded-db
  "Calls (f dbs) against a user-db holding one document of every type we keep."
  [f]
  (db-fixtures/with-test-db
    user-db-name
    (^:async fn
     [db]
     (doseq [doc seed-docs]
       (await (db/insert db doc)))
     (await (f {:user/db db})))))


(defn- ^:async stored-docs
  "Every document in the database, by id, without the revisions."
  [dbs]
  (let [{rows :rows} (await (db/all-docs (:user/db dbs) {:include-docs true}))]
    (into {} (map (juxt :id (comp #(dissoc % :_rev) :doc))) rows)))


(deftest export-carries-every-document-not-a-list-of-known-types
  (async-testing "a collection is exported like anything else"
    (await
     (with-seeded-db
      (^:async fn
       [dbs]
       (let [{:keys [docs schema]} (await (sut/export-data! dbs))
             ids (set (map :_id docs))]
         (is (= 2 schema))
         (is (contains? ids "vocab:hund"))
         (is (contains? ids "review:1"))
         (is (contains? ids "collection:travel") "the type a hand-kept list forgot")
         (is (contains? ids "example:1"))
         (is (every? #(nil? (:_rev %)) docs) "revisions belong to a database, not to a backup")))))))


(deftest import-restores-what-export-produced
  (async-testing "round trip"
    (await
     (with-seeded-db
      (^:async fn
       [dbs]
       (let [payload (await (sut/export-data! dbs))
             before  (await (stored-docs dbs))]
         (doseq [id (keys before)]
           (await (db/remove (:user/db dbs) (await (db/get (:user/db dbs) id)))))
         (await (sut/import-data! dbs payload))
         (is (= before (await (stored-docs dbs))))))))))


(deftest importing-the-same-payload-twice-changes-nothing
  (async-testing "idempotent"
    (await
     (with-seeded-db
      (^:async fn
       [dbs]
       (let [payload (await (sut/export-data! dbs))]
         (await (sut/import-data! dbs payload))
         (let [after-first (await (stored-docs dbs))]
           (await (sut/import-data! dbs payload))
           (is (= after-first (await (stored-docs dbs)))))))))))


(deftest a-newer-vocab-wins-while-other-types-keep-what-is-there
  (async-testing "import dispatches on type"
    (await
     (with-seeded-db
      (^:async fn
       [dbs]
       (await (sut/import-data!
               dbs
               {:schema 2
                :docs   [{:_id         "vocab:hund"
                          :type        "vocab"
                          :value       "Hund"
                          :translation ["пёс"]
                          :modified-at "2026-06-01"}
                         {:_id "collection:travel" :type "collection" :name "Renamed"}]}))
       (let [docs (await (stored-docs dbs))]
         (is (= ["пёс"] (:translation (get docs "vocab:hund")))
             "vocab takes the later revision")
         (is (= "Travel" (:name (get docs "collection:travel")))
             "anything else is inserted only when absent")))))))


(deftest an-older-vocab-loses
  (async-testing "last write wins by :modified-at, not by arrival"
    (await
     (with-seeded-db
      (^:async fn
       [dbs]
       (await (sut/import-data!
               dbs
               {:schema 2
                :docs   [{:_id         "vocab:hund"
                          :type        "vocab"
                          :value       "Hund"
                          :translation ["старое"]
                          :modified-at "2025-01-01"}]}))
       (is (= ["собака"] (:translation (get (await (stored-docs dbs)) "vocab:hund")))))))))


(deftest a-payload-from-the-previous-format-still-imports
  (async-testing "schema 1 carried vocab and review under separate keys"
    (await
     (with-seeded-db
      (^:async fn
       [dbs]
       (await (sut/import-data!
               dbs
               {:schema 1
                :vocab  [{:_id "vocab:katze" :type "vocab" :value "Katze" :translation ["кошка"]}]
                :review [{:_id "review:2" :type "review" :word-id "vocab:katze"}]}))
       (let [docs (await (stored-docs dbs))]
         (is (contains? docs "vocab:katze"))
         (is (contains? docs "review:2"))))))))

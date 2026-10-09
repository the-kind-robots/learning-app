(ns client.db-migrations-test
  "The one-time moves of an old install's data, driven through `ensure-migrated!`
   and read back from the databases. The databases are the boundary."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.db-seed :as db-seed]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [db-migrations :as sut]))


(def local-db-name (db-fixtures/db-name "db-migrations-test.local"))


(def user-db-name (db-fixtures/db-name "db-migrations-test.user"))


(def device-db-name (db-fixtures/db-name "db-migrations-test.device"))


(defn- forget-the-run!
  "What a restart of the app does: the page no longer remembers it migrated."
  []
  (reset! @#'sut/migration-state {:status :not-started :promise nil}))


(use-fixtures
 :each
 (db-fixtures/db-fixture-multi [local-db-name user-db-name device-db-name])
 {:before forget-the-run!})


(defn- ^:async with-app-dbs
  "Calls `f` with the three databases an install has, which `db/use` hands out
   by name."
  [f]
  (await
   (db-fixtures/with-test-dbs
     [local-db-name user-db-name device-db-name]
     (^:async fn
      [[local-db user-db device-db]]
      (with-redefs [db/use #(case %
                              "local-db"  local-db
                              "user-db"   user-db
                              "device-db" device-db)]
        (await (f {:local-db local-db :user-db user-db :device-db device-db})))))))


(def ^:private more-than-a-page
  "Past pouchdb-find's default limit of 25, where a move that reads one page
   would silently leave documents behind."
  30)


(deftest an-old-local-db-is-split-by-who-owns-the-data
  (async-testing "the learner's data goes to user-db, the device's own to device-db; nothing is cut off at a page"
    (with-app-dbs
      (^:async fn
       [{:keys [local-db user-db device-db]}]
       (await (db-seed/insert-all! local-db (concat
                                     (for [i (range more-than-a-page)]
                                       {:_id (str "v" i) :type "vocab" :value (str "Wort " i)})
                                     [{:_id "r1" :type "review" :word-id "v1"}
                                      {:_id "t1" :type "task" :task-type "example-fetch" :data {}}
                                      {:_id "e1" :type "example" :word-id "v1"}
                                      {:_id "l1" :type "lesson" :trials []}])))
       (await (sut/ensure-migrated!))
       (is (= more-than-a-page (count (await (db-queries/fetch-by-type user-db "vocab")))))
       (is (= 1 (count (await (db-queries/fetch-by-type user-db "review")))))
       (doseq [doc-type ["task" "example" "lesson"]]
         (is (= 1 (count (await (db-queries/fetch-by-type device-db doc-type)))) doc-type))))))


(deftest a-task-queued-before-the-data-field-is-rewritten
  (async-testing "a legacy task carries its payload under :data; one that already does is left alone; every one is reached"
    (with-app-dbs
      (^:async fn
       [{:keys [device-db]}]
       (await (db-seed/insert-all! device-db (concat
                                      [{:_id "new" :type "task" :task-type "example-fetch" :data {:word-id "w"} :attempts 0}]
                                      (for [i (range more-than-a-page)]
                                        {:_id (str "old-" i) :type "task" :task-type "example-fetch"
                                         :word-id (str "w" i) :attempts 0}))))
       (await (sut/ensure-migrated!))
       (let [tasks (into {} (map (juxt :_id identity)) (await (db-queries/fetch-by-type device-db "task")))]
         (is (= (inc more-than-a-page) (count tasks)))
         (is (= {:word-id "w"} (:data (tasks "new"))))
         (is (= {:word-id "w7"} (:data (tasks "old-7"))))
         (is (not-any? :word-id (vals tasks))))))))


(deftest a-migration-that-ran-does-not-run-again
  (async-testing "after a restart the old local-db is not copied a second time"
    (with-app-dbs
      (^:async fn
       [{:keys [local-db user-db]}]
       (await (db/insert local-db {:_id "v1" :type "vocab" :value "der Hund"}))
       (await (sut/ensure-migrated!))
       (await (db/insert local-db {:_id "v2" :type "vocab" :value "die Katze"}))
       (forget-the-run!)
       (await (sut/ensure-migrated!))
       (is (= ["v1"] (mapv :_id (await (db-queries/fetch-by-type user-db "vocab")))))))))


(deftest callers-asking-at-once-wait-for-the-same-migration
  (async-testing "concurrent calls share one run"
    (with-app-dbs
      (^:async fn
       [_]
       (let [p1 (sut/ensure-migrated!)
             p2 (sut/ensure-migrated!)]
         (is (identical? p1 p2))
         (is (true? (await p1))))))))


(deftest a-migration-that-failed-is-tried-again-on-the-next-ask
  (async-testing "a transient database failure rejects once, then the data moves"
    (with-app-dbs
      (^:async fn
       [{:keys [local-db user-db]}]
       (await (db/insert local-db {:_id "v1" :type "vocab" :value "der Hund"}))
       (let [use-db db/use
             calls  (atom 0)]
         (with-redefs [db/use (fn [name]
                                (if (= 1 (swap! calls inc))
                                  (throw (ex-info "Transient error" {}))
                                  (use-db name)))]
           (is (= "rejected" (try (await (sut/ensure-migrated!)) (catch :default _ "rejected"))))
           (is (true? (await (sut/ensure-migrated!))))
           (is (= ["v1"] (mapv :_id (await (db-queries/fetch-by-type user-db "vocab")))))))))))

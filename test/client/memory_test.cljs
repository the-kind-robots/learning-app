(ns client.memory-test
  "The learner's data in memory as a projection of the local databases
   (ADR-0016): loaded, following the feed, taking this app's writes only once
   PouchDB accepted them."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.memory :as sut]
   [adapters.reviews :as reviews]
   [adapters.words :as words]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-seed :as db-seed]
   [cljs.test :refer-macros [deftest is testing use-fixtures]]
   [db :as db]
   [db.pouch :as pouch]))


(def user-db-name (db-fixtures/db-name "client.memory-test.user"))


(def device-db-name (db-fixtures/db-name "client.memory-test.device"))


(use-fixtures :each (db-fixtures/db-fixture-multi [user-db-name device-db-name]))


(defn- with-test-dbs
  [f]
  (db-fixtures/with-test-dbs
   [user-db-name device-db-name]
   (fn [[user-db device-db]]
     (f {:dbs/on-written (atom nil)
         :device/db      device-db
         :user/db        user-db}))))


(defn- until
  "Resolves once `pred` holds, polling; rejects after a second."
  ([pred]
   (until pred 100))
  ([pred tries]
   (cond
     (pred)        (js/Promise.resolve true)
     (zero? tries) (js/Promise.reject (ex-info "condition never held" {}))
     :else         (js/Promise. (fn [resolve]
                                  (js/setTimeout #(resolve (until pred (dec tries))) 10))))))


(defn- started
  "Starts memory over `dbs` into an atom. Returns [memory ready? stop]."
  [dbs]
  (let [memory (atom sut/empty-memory)
        ready? (atom false)
        stop   (sut/start! dbs
                           (fn [f & [ready]]
                             (swap! memory f)
                             (when ready (reset! ready? true))))]
    [memory ready? stop]))


(def ^:private hund
  {:_id "vocab:der hund" :type "vocab" :value "der Hund" :translation [{:lang "ru" :value "пёс"}]})


(deftest a-document-is-projected-once-per-revision
  (let [doc     (assoc hund :_rev "1-a")
        once    (sut/with-doc sut/empty-memory doc)
        twice   (sut/with-doc once doc)
        deleted (sut/with-doc once {:_deleted true :_id "vocab:der hund" :_rev "2-b"})]
    (is (= "der Hund" (get-in once [:words "vocab:der hund" :value])))
    (is (= "der hund\nпёс" (get-in once [:words "vocab:der hund" :search]))
        "the search text is normalised once, value and translations a line each")
    (is (identical? once twice) "the revision memory holds changes nothing")
    (is (nil? (get-in deleted [:words "vocab:der hund"])) "a deletion removes the word")
    (is (nil? (get-in once [:words "vocab:der hund" :_rev])) "no storage name reaches the entity")))


(deftest a-review-tombstone-leaves-its-word
  (testing "a deleted review carries no word id; memory's own record says which word it was"
    (let [review {:_id        "r1"
                  :_rev       "1-a"
                  :type       "review"
                  :word-id    "vocab:der hund"
                  :created-at "2026-01-01T00:00:00Z"
                  :retained   true}
          held   (sut/with-doc sut/empty-memory review)
          gone   (sut/with-doc held {:_deleted true :_id "r1" :_rev "2-b"})]
      (is (= {"r1" {:created-at "2026-01-01T00:00:00Z" :retained true :word-id "vocab:der hund"}}
             (get-in held [:reviews-by-word "vocab:der hund"])))
      (is (empty? (:reviews gone)))
      (is (empty? (:reviews-by-word gone))))))


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
      (let [[memory ready? stop] (started dbs)]
        (await (until #(deref ready?)))
        (is (= ["vocab:der hund"] (keys (:words @memory))))
        (is (= 1 (count (get-in @memory [:reviews-by-word "vocab:der hund"]))))
        (is (= ["example-1"] (keys (:examples @memory))))
        (is (empty? (filter #(re-find #"^_design" %) (keys (:words @memory)))))
        ;; Written past `db.pouch`, as a replication or another tab writes.
        (await (db/insert (:user/db dbs)
                          {:_id         "vocab:die katze"
                           :type        "vocab"
                           :value       "die Katze"
                           :translation [{:lang "ru" :value "кошка"}]}))
        (await (until #(get-in @memory [:words "vocab:die katze"])))
        (let [{doc :_rev} (await (db/get (:user/db dbs) "vocab:die katze"))]
          (await (db/remove (:user/db dbs) {:_id "vocab:die katze" :_rev doc})))
        (await (until #(nil? (get-in @memory [:words "vocab:die katze"]))))
        (is (nil? (get-in @memory [:words "vocab:die katze"])))
        (stop))))))


(deftest an-own-write-is-in-memory-when-the-writer-resumes
  (async-testing "write-through: memory holds the document once PouchDB accepted it"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [[memory ready? stop] (started dbs)]
        (await (until #(deref ready?)))
        (await (pouch/insert dbs words/schema (dissoc hund :type)))
        (is (= "der Hund" (get-in @memory [:words "vocab:der hund" :value]))
            "no feed round trip: the write's own promise is enough")
        (stop))))))


(deftest a-refused-write-leaves-memory-untouched
  (async-testing "a conflict writes nothing, and memory takes nothing"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (db/insert (:user/db dbs) hund))
      (let [[memory ready? stop] (started dbs)]
        (await (until #(deref ready?)))
        (let [before @memory
              failed (try
                       (await (pouch/insert dbs words/schema (assoc (dissoc hund :type) :value "der Pudel")))
                       false
                       (catch :default _ true))]
          (is failed "the premise: no revision, so PouchDB refuses it")
          (is (= before @memory)))
        (stop))))))


(deftest a-bulk-write-reports-only-what-it-wrote
  (async-testing "the documents of a bulk write each arrive at their new revision"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [[memory ready? stop] (started dbs)]
        (await (until #(deref ready?)))
        (await (pouch/bulk-docs dbs
                                reviews/schema
                                [{:_id "r1" :type "review" :word-id "w" :retained true :created-at "2026-01-01"}
                                 {:_id "r2" :type "review" :word-id "w" :retained false :created-at "2026-01-02"}]))
        (is (= #{"r1" "r2"} (set (keys (get-in @memory [:reviews-by-word "w"])))))
        (stop))))))

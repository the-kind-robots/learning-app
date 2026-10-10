(ns client.db.pouch-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [browser :as browser]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.wait :as wait]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [db.pouch :as sut]
   [userdb :as userdb]))


(def local-name (db-fixtures/db-name "client.db.pouch-test.local"))


(def remote-name (db-fixtures/db-name "client.db.pouch-test.remote"))


(def account-id 7)


(def account-db-name
  "Where `sync-once!` looks for the account's copy: the origin, `/db/`, and the
   name db-per-user gives the account's database. Pointing the origin at the
   test directory makes that a local database this test can seed."
  (str "target/pouch/db/" (userdb/db-name account-id)))


(use-fixtures :each (db-fixtures/db-fixture-multi [local-name remote-name account-db-name]))


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
         (await (db/insert local {:_id "_design/local-only" :views {}}))
         (await (db/insert remote {:_id "_design/remote-only" :views {}}))
         (await (db/insert remote {:_id "vocab:katze" :type "vocab" :value "Katze"}))
         (await (sync-pass! local remote))
         (let [local-ids  (await (ids local))
               remote-ids (await (ids remote))]
           (is (contains? local-ids "_design/local-only"))
           (is (contains? local-ids "vocab:katze"))
           (is (not (contains? local-ids "_design/remote-only")))
           (is (contains? remote-ids "vocab:hund"))
           (is (not (contains? remote-ids "_design/local-only")))))))))


(deftest a-revision-the-pull-wrote-is-recognised-on-the-feed-and-a-local-write-is-not
  (async-testing "GH-319: what the pull wrote is recognisable on the feed; a local write is not"
    (db-fixtures/with-test-db
      local-name
      (^:async fn
       [local]
       (.mkdirSync (js/require "fs") "target/pouch/db" #js {:recursive true})
       (let [account-db (db/use account-db-name)
             seen       (atom [])
             unwatch    (sut/on-change {:user/db local}
                                       :user/db
                                       #(swap! seen conj (sut/change-revision %)))]
         (await (db/insert account-db {:_id "vocab:katze" :type "vocab" :value "Katze"}))
         (await (db/insert account-db {:_id "review-1" :type "review" :word-id "vocab:katze"}))
         (set! (.-location js/globalThis) #js {:origin "target/pouch"})
         (try
           (let [{:keys [pulled-revs]} (await (sut/sync-once! {:user/db local} :user/db account-id))]
             (await (wait/until #(<= 2 (count @seen))))
             (is (= 2 (count pulled-revs)))
             (is (= pulled-revs (set @seen))
                 "every feed event of the pull is a revision the pass reports")
             (reset! seen [])
             (await (db/insert local {:_id "vocab:hund" :type "vocab" :value "Hund"}))
             (await (wait/until #(= 1 (count @seen))))
             (is (= 1 (count @seen)))
             (is (not-any? pulled-revs @seen) "a local write is none of them"))
           (finally
            (unwatch)
            (js/Reflect.deleteProperty js/globalThis "location"))))))))


(deftest a-followed-feed-brings-every-change-in-the-order-stored
  (async-testing "the documents stored after `since` arrive in the order they were stored"
    (db-fixtures/with-test-db
      local-name
      (^:async fn
       [local]
       (await (db/insert local {:_id "before" :type "x"}))
       (let [dbs     {:user/db local}
             batches (atom [])
             unwatch (sut/follow-changes dbs
                                         :user/db
                                         (:seq (await (sut/feed-position dbs :user/db)))
                                         (fn [docs _position] (swap! batches conj (mapv :_id docs))))]
         (try
           (await (db/bulk-docs local [{:_id "a" :type "x"} {:_id "b" :type "x"}]))
           (await (wait/until #(= 2 (count (apply concat @batches)))))
           (await (db/insert local {:_id "c" :type "x"}))
           (await (wait/until #(= 3 (count (apply concat @batches)))))
           (is (= ["a" "b" "c"] (apply concat @batches)) "every change, in the order stored, nothing before `since`")
           (finally
            ((:stop! unwatch)))))))))


(deftest a-catch-up-answered-after-the-feed-hands-over-nothing-older
  (async-testing "a catch-up read before the feed brought a newer revision, answered after it, brings nothing"
    (db-fixtures/with-test-db
      local-name
      (^:async fn
       [local]
       (let [dbs      {:user/db local}
             since    (:seq (await (sut/feed-position dbs :user/db)))
             _ (await (db/insert local {:_id "a" :type "x" :n 1}))
             ;; What a catch-up reads now, answered only later.
             stale    (await (sut/read-changes dbs :user/db since nil))
             handed   (atom [])
             original sut/read-changes
             feed     (sut/follow-changes dbs
                                          :user/db
                                          since
                                          (fn [docs position] (swap! handed conj [(mapv :n docs) position])))]
         (try
           (await (wait/until #(= 1 (count @handed))))
           (let [a (await (db/get local "a"))]
             (await (db/insert local (assoc a :n 2))))
           (await (wait/until #(= 2 (count @handed))))
           (set! sut/read-changes (fn [& _] (js/Promise.resolve stale)))
           (await ((:catch-up! feed)))
           (is (= [[1] [2]] (mapv first @handed)) "the stale answer hands nothing over")
           (is (apply < (map (comp :seq second) @handed)) "each batch comes with a later position")
           (finally
            (set! sut/read-changes original)
            ((:stop! feed)))))))))


(deftest a-read-goes-past-one-page
  (async-testing "read-docs pages through every document, and through an id range"
    (db-fixtures/with-test-db
      local-name
      (^:async fn
       [local]
       (await (db/bulk-docs local
                            (vec (for [i (range 2345)]
                                   {:_id (str (if (< i 1500) "a:" "b:") (+ 10000 i)) :type "x"}))))
       (let [dbs {:user/db local}]
         ;; Design documents come too; memory keeps no document of their type.
         (is (= 2345 (count (filter #(= "x" (:type %)) (await (sut/read-docs dbs :user/db {}))))))
         (is (= 1500 (count (await (sut/read-docs dbs :user/db {:end "a:\ufff0" :start "a:"}))))))))))


(deftest a-document-deleted-between-pages-loses-no-other
  (async-testing "the last document of a page is deleted before the next page is read; the next one is still read"
    (db-fixtures/with-test-db
      local-name
      (^:async fn
       [local]
       (await (db/bulk-docs local (vec (for [i (range 1005)] {:_id (str "a:" (+ 10000 i)) :type "x"}))))
       (let [dbs      {:user/db local}
             yield    browser/yield
             ;; The 1000th id, the last of the first page.
             boundary "a:10999"]
         (set! browser/yield
               (fn ^:async between []
                 (await (db/remove local (await (db/get local boundary))))
                 (await (yield))))
         (try
           (let [ids (set (map :_id (await (sut/read-docs dbs :user/db {:end "a:\ufff0" :start "a:"}))))]
             (is (contains? ids "a:11000") "the first document after the boundary")
             (is (= 1004 (count (disj ids boundary)))))
           (finally
            (set! browser/yield yield))))))))


(deftest a-refused-write-is-a-conflict-and-nothing-else-is
  (async-testing "the rejection of a put over a stale revision is what conflict? recognises"
    (db-fixtures/with-test-db
      local-name
      (^:async fn
       [local]
       (await (db/insert local {:_id "doc" :type "x"}))
       (let [refusal (fn ^:async f [doc]
                       (try
                         (await (db/insert local doc))
                         nil
                         (catch :default err err)))]
         (is (db/conflict? (await (refusal {:_id "doc" :type "x"}))) "a put over a stored document with no revision")
         (is (not (db/conflict? (await (refusal {:_id "_bad" :type "x"})))) "a put refused for another reason"))))))

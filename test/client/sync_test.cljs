(ns client.sync-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.identity :as identity]
   [client.support.db-fixtures :as db-fixtures]
   [cljs.test :refer-macros [deftest is testing use-fixtures]]
   [db :as db]
   [db.pouch :as pouch]
   [sync :as sut]))


(def user-db-name (db-fixtures/db-name "client.sync-test.user"))


(def device-db-name (db-fixtures/db-name "client.sync-test.device"))


(use-fixtures :each (db-fixtures/db-fixture-multi [user-db-name device-db-name]))


;; Revisions of one document in the same generation and without a shared parent
;; — what replication leaves behind when the same word was written on two
;; devices. PouchDB serves whichever leaf has the higher revision hash, so
;; "1-bbb..." is the one a plain read returns and "1-aaa..." is the conflict.
;;
;; The two words below put the last write on opposite sides of that pick: on
;; Hund the newest revision is the hidden one, on Katze it is the served one.
;; One word alone would not pin the rule down — with two revisions, "take the
;; newest" and "take whichever comes second" agree, and a merge that simply
;; kept the last revision it saw would pass.
(def ^:private written-on-the-phone
  {:_id         "vocab:hund"
   :_rev        "1-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
   :modified-at "2026-06-01"
   :translation [{:lang "ru" :value "собака"}]
   :type        "vocab"
   :value       "Hund"})


(def ^:private written-on-the-laptop
  {:_id         "vocab:hund"
   :_rev        "1-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
   :modified-at "2026-01-01"
   :translation [{:lang "ru" :value "пёс"}]
   :type        "vocab"
   :value       "Hund"})


(def ^:private katze-written-first
  {:_id         "vocab:katze"
   :_rev        "1-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
   :modified-at "2026-01-01"
   :translation [{:lang "ru" :value "кот"}]
   :type        "vocab"
   :value       "Katze"})


(def ^:private katze-written-last
  {:_id         "vocab:katze"
   :_rev        "1-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
   :modified-at "2026-06-01"
   :translation [{:lang "ru" :value "кошка"}]
   :type        "vocab"
   :value       "Katze"})


;; Conflicting leaves of a word whose id carries a character well above the
;; ASCII range — the words are read as a key range, and an id sorting past its
;; end would never be looked at.
(def ^:private umlaut-written-first
  {:_id         "vocab:überwältigend"
   :_rev        "1-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
   :modified-at "2026-01-01"
   :translation [{:lang "ru" :value "потрясающий"}]
   :type        "vocab"
   :value       "überwältigend"})


(def ^:private umlaut-written-last
  {:_id         "vocab:überwältigend"
   :_rev        "1-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
   :modified-at "2026-06-01"
   :translation [{:lang "ru" :value "ошеломительный"}]
   :type        "vocab"
   :value       "überwältigend"})


(defn- leaf!
  "Writes a revision under the `_rev` it carries instead of on top of what is
   stored, which is how a sibling leaf — a conflict — can be made by hand."
  [user-db doc]
  (.bulkDocs ^js user-db (db/clj->couch [doc]) #js {:new_edits false}))


(defn- with-conflict
  "Calls (f user-db) against a database holding `docs` as conflicting leaves."
  [docs f]
  (db-fixtures/with-test-db
    user-db-name
    (^:async fn
     [user-db]
     (doseq [doc docs]
       (await (leaf! user-db doc)))
     (await (f user-db)))))


(defn- ^:async conflicts-of
  [user-db id]
  (let [{rows :rows} (await (db/all-docs user-db {:include-docs true :conflicts true}))]
    (:_conflicts (some #(when (= id (:id %)) (:doc %)) rows))))


(deftest the-last-write-wins-over-the-revision-pouchdb-serves
  (async-testing "a conflict is resolved by :modified-at, whichever leaf carries it"
    (await
     (with-conflict
      [written-on-the-phone written-on-the-laptop katze-written-first katze-written-last]
      (^:async fn
       [user-db]
       (is (= "2026-01-01" (:modified-at (await (db/get user-db "vocab:hund"))))
           "before resolution the database serves the leaf with the higher hash")
       (await (sut/resolve-vocab-conflicts! user-db))
       (is (= "2026-06-01" (:modified-at (await (db/get user-db "vocab:hund"))))
           "the newest revision was the hidden one")
       (is (= "2026-06-01" (:modified-at (await (db/get user-db "vocab:katze"))))
           "and here it was the served one"))))))


(deftest no-translation-is-lost-to-the-losing-revision
  (async-testing "translations are unioned across every revision"
    (await
     (with-conflict
      [written-on-the-phone written-on-the-laptop]
      (^:async fn
       [user-db]
       (await (sut/resolve-vocab-conflicts! user-db))
       (is (= #{"собака" "пёс"}
              (into #{} (map :value) (:translation (await (db/get user-db "vocab:hund")))))))))))


(deftest the-losing-revisions-are-gone-afterwards
  (async-testing "resolution deletes the leaves it merged, so the conflict does not come back"
    (await
     (with-conflict
      [written-on-the-phone written-on-the-laptop]
      (^:async fn
       [user-db]
       (is (seq (await (conflicts-of user-db "vocab:hund"))))
       (await (sut/resolve-vocab-conflicts! user-db))
       (is (empty? (await (conflicts-of user-db "vocab:hund")))))))))


(deftest a-word-outside-the-ascii-range-is-still-reached
  (async-testing "the key range covers the whole prefix, not the letters it was written with"
    (await
     (with-conflict
      [umlaut-written-first umlaut-written-last]
      (^:async fn
       [user-db]
       (await (sut/resolve-vocab-conflicts! user-db))
       (is (empty? (await (conflicts-of user-db "vocab:überwältigend"))))
       (is (= "2026-06-01"
              (:modified-at (await (db/get user-db "vocab:überwältigend"))))))))))


(deftest a-conflict-in-another-type-is-left-alone
  (async-testing "only vocabulary merges — reviews are union-for-free, so a pass must not touch them"
    (await
     (with-conflict
      [{:_id "review:1" :_rev "1-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" :retained true :type "review"}
       {:_id "review:1" :_rev "1-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" :retained false :type "review"}]
      (^:async fn
       [user-db]
       (await (sut/resolve-vocab-conflicts! user-db))
       (is (seq (await (conflicts-of user-db "review:1")))))))))


(deftest a-document-without-conflicts-is-not-rewritten
  (async-testing "an untouched word keeps its revision, so a pass pushes nothing"
    (await
     (db-fixtures/with-test-db
       user-db-name
       (^:async fn
        [user-db]
        (await (db/insert user-db (dissoc written-on-the-phone :_rev)))
        (let [before (:_rev (await (db/get user-db "vocab:hund")))]
          (await (sut/resolve-vocab-conflicts! user-db))
          (is (= before (:_rev (await (db/get user-db "vocab:hund")))))))))))


(def ^:private dated
  {:_id         "vocab:hase"
   :_rev        "1-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
   :modified-at "2026-06-01"
   :translation [{:lang "ru" :value "заяц"}]
   :type        "vocab"
   :value       "Hase"})


(def ^:private undated
  (-> dated
      (assoc :_rev        "1-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
             :translation [{:lang "ru" :value "кролик"}])
      (dissoc :modified-at)))


(deftest a-revision-without-a-date-loses-to-one-that-has-it
  (async-testing "an undated revision cannot be the last write"
    (await
     (with-conflict
      [dated undated]
      (^:async fn
       [user-db]
       (await (sut/resolve-vocab-conflicts! user-db))
       (is (= "2026-06-01" (:modified-at (await (db/get user-db "vocab:hase"))))))))))


(deftest neither-revision-carries-a-date
  (async-testing "the conflict still goes away, and with both translations"
    (await
     (with-conflict
      [(dissoc dated :modified-at) undated]
      (^:async fn
       [user-db]
       (await (sut/resolve-vocab-conflicts! user-db))
       (is (empty? (await (conflicts-of user-db "vocab:hase"))))
       (is (= #{"заяц" "кролик"}
              (into #{} (map :value) (:translation (await (db/get user-db "vocab:hase")))))))))))


(defn- ^:async pass-heard-by
  "Runs one pass with `listener` subscribed, and unsubscribes afterwards."
  [listener pass]
  (let [unsubscribe (sut/on-pass! listener)]
    (try
      (await
       (db-fixtures/with-test-db
         user-db-name
         (^:async fn
          [user-db]
          (with-redefs [pouch/sync-once! (fn [_ _] (js/Promise.resolve pass))]
            (await (sut/sync-once! {:user/db user-db} "account"))))))
      (finally
       (unsubscribe)))))


(deftest a-completed-pass-tells-whoever-is-listening-what-it-brought
  (async-testing "the engine publishes; what listens is none of its business"
    (let [heard (atom [])
          hear  #(swap! heard conj %)]
      (is (= {:pulled 1 :pulled-ids ["vocab:hund"] :pushed 0}
             (await (pass-heard-by hear {:pulled 1 :pulled-ids ["vocab:hund"] :pushed 0}))))
      (is (= [{:pulled 1 :pulled-ids ["vocab:hund"] :pushed 0}] @heard)
          "the ids the pull wrote reach the listener")
      (await (pass-heard-by hear {:pulled 0 :pulled-ids [] :pushed 2}))
      (is (= 2 (count @heard)) "a push-only pass still announces, with nothing in it")
      (await (pass-heard-by hear nil))
      (is (= 2 (count @heard)) "a failed pass announces nothing"))))


(deftest a-listener-that-unsubscribes-is-not-called-again
  (async-testing "the subscription hands back the way out"
    (let [heard (atom 0)]
      (await (pass-heard-by (fn [_] (swap! heard inc)) {:pulled 0 :pulled-ids [] :pushed 1}))
      (is (= 1 @heard))
      (await
       (db-fixtures/with-test-db
         user-db-name
         (^:async fn
          [user-db]
          (with-redefs [pouch/sync-once! (fn [_ _]
                                           (js/Promise.resolve {:pulled 0 :pulled-ids [] :pushed 1}))]
            (await (sut/sync-once! {:user/db user-db} "account"))))))
      (is (= 1 @heard) "nobody is listening any more"))))


;;
;; Adopting a key that belongs to another account
;;


(defn- ^:async device-doc-ids
  [device-db]
  (let [{rows :rows} (await (db/all-docs device-db))]
    (set (map :id rows))))


(defn- ^:async seed-device-db!
  "What a device holds after a while on one account: examples it fetched, a
   fetch still queued, one that was dead-lettered, and the two documents that
   belong to the device rather than to the account."
  [device-db]
  (await (db/insert device-db {:_id "example-hund" :type "example" :word-id "vocab:hund"}))
  (await (db/insert device-db {:_id "task-queued" :type "task" :task-type "example-fetch"
                               :data {:word-id "vocab:katze"}}))
  (await (db/insert device-db {:_id "task-dead" :type "task" :task-type "example-fetch"
                               :status "failed" :data {:word-id "vocab:maus"}}))
  (await (db/insert device-db {:_id "identity:local" :type "identity" :user-id 1 :token "old"}))
  (await (db/insert device-db {:_id "migration:001" :type "migration"})))


(defn- ^:async adopt-key!
  "Runs the incoming-credential path for `#key=...`, with the databases it
   opens by name pointed at the test ones and the network call answered with
   `account-id`."
  [device-db user-db account-id]
  (set! (.-window js/globalThis) #js {:location #js {:hash "#key=token-of-the-other"}})
  (try
    (with-redefs [db/use                  (fn [name] (if (= "device-db" name) device-db user-db))
                  identity/load-identity! (fn [] (js/Promise.resolve {:id 1 :token "old"}))
                  identity/account-id!    (fn [_] (js/Promise.resolve account-id))
                  identity/save-identity! (fn [_] (js/Promise.resolve nil))]
      (await (sut/check-incoming-auth! nil)))
    (finally
     (js/Reflect.deleteProperty js/globalThis "window"))))


(deftest a-key-of-another-account-takes-the-old-account-s-examples-with-it
  (async-testing "examples and queued fetches are the account's; the identity is the device's"
    (await
     (db-fixtures/with-test-dbs
      [user-db-name device-db-name]
      (^:async fn
       [[user-db device-db]]
       (await (seed-device-db! device-db))
       (await (adopt-key! device-db user-db 2))
       (let [ids (await (device-doc-ids device-db))]
         (is (not (contains? ids "example-hund"))
             "content-addressed ids would show the next account this sentence")
         (is (not (contains? ids "task-queued")))
         (is (not (contains? ids "task-dead")))
         (testing "and what belongs to the device stays"
           (is (contains? ids "identity:local"))
           (is (contains? ids "migration:001")))))))))


(deftest a-key-of-the-same-account-keeps-everything
  (async-testing "the same account on another device is a merge, not a change of hands"
    (await
     (db-fixtures/with-test-dbs
      [user-db-name device-db-name]
      (^:async fn
       [[user-db device-db]]
       (await (seed-device-db! device-db))
       (await (adopt-key! device-db user-db 1))
       (let [ids (await (device-doc-ids device-db))]
         (is (contains? ids "example-hund"))
         (is (contains? ids "task-queued"))))))))


(def ^:private a-completed-pass
  {:pulled 1 :pulled-ids ["vocab:hund"] :pushed 0})


(deftest a-pass-does-not-wait-for-what-it-announces
  (async-testing "the screen redraws and the throttle starts when replication is done"
    (let [finished (atom false)
          listener (fn [_] (js/setTimeout #(reset! finished true) 0) nil)]
      (is (= a-completed-pass (await (pass-heard-by listener a-completed-pass))))
      (is (false? @finished) "the pass was over before the work it started"))))


(deftest a-listener-that-fails-does-not-fail-the-pass
  (async-testing "a device that cannot read what it is missing still replicated"
    (is (= a-completed-pass
           (await (pass-heard-by (fn [_] (throw (js/Error. "no view"))) a-completed-pass))))))

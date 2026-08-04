(ns client.sync-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [client.support.db-fixtures :as db-fixtures]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [sync :as sut]))


(def user-db-name (db-fixtures/db-name "client.sync-test.user"))


(use-fixtures :each (db-fixtures/db-fixture user-db-name))


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

(ns client.collection-conflicts-test
  "Two devices, each with a database of its own, edit one collection before
   they sync. Replication between the databases makes the conflicts; the
   resolver of `sync` settles them."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.learner.documents :as documents]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.learner :as learner]
   [client.support.replication :as replication]
   [client.support.time :as time]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [db.pouch :as pouch]
   [adapters.learner.sync :as sut]))


(def phone-name (db-fixtures/db-name "client.collection-conflicts-test.phone"))


(def laptop-name (db-fixtures/db-name "client.collection-conflicts-test.laptop"))


(def device-name (db-fixtures/db-name "client.collection-conflicts-test.device"))


(use-fixtures :each (db-fixtures/db-fixture-multi [phone-name laptop-name device-name]))


(def ^:private clock
  {:clock/now-iso time/now-iso
   :clock/now-ms  time/now-ms})


(def ^:private kurs
  {:_id "c1" :type "collection" :name "Kurs" :word-ids [] :created-at "2026-01-01"})


(defn- ^:async with-devices
  "Calls `f` with the databases of two devices, `{:phone :laptop}`, each as
   `{:user/db db}`, and the device database of the phone. Both hold `kurs`."
  [f]
  (await
   (db-fixtures/with-test-dbs
    [phone-name laptop-name device-name]
    (^:async fn
     [[phone laptop device]]
     (await (db/insert phone kurs))
     (await (replication/replicated! phone laptop))
     (await (f {:laptop {:user/db laptop}
                :phone  {:device/db device :user/db phone}}))))))


(defn- ^:async add-word!
  "Writes `word-id` into the collection `c1` of `dbs`'s database, over the winner."
  [dbs word-id]
  (let [stored (await (db/get (:user/db dbs) "c1"))]
    (await (db/insert (:user/db dbs) (update stored :word-ids conj word-id)))))


(defn- ^:async rename!
  "Gives the collection `c1` of `dbs`'s database `new-name`, renamed at `at`."
  [dbs new-name at]
  (let [stored (await (db/get (:user/db dbs) "c1"))]
    (await (db/insert (:user/db dbs) (assoc stored :name new-name :renamed-at at)))))


(defn- ^:async pass!
  "One sync pass of the device `dbs` against `other`: pull, push, and the
   resolution of what the pull brought."
  [dbs other]
  (await (replication/replicated! (:user/db other) (:user/db dbs)))
  (await (replication/replicated! (:user/db dbs) (:user/db other)))
  (await (sut/resolve-collection-conflicts! dbs ["c1"])))


(defn- ^:async settled!
  "Passes of both devices until nothing is left to bring home."
  [{:keys [laptop phone]}]
  (doseq [_ (range 2)]
    (await (pass! phone laptop))
    (await (pass! laptop phone))))


(defn- ^:async live-count
  [dbs]
  (count (remove :_deleted (await (pouch/doc-versions dbs documents/collection-schema "c1")))))


(deftest two-devices-adding-different-words-offline-end-with-both-words
  (async-testing "both words are in the collection on both devices, on one live version"
    (with-devices
     (^:async fn
      [{:keys [laptop phone] :as devices}]
      (await (add-word! phone "vocab:hund"))
      (await (add-word! laptop "vocab:katze"))
      (await (settled! devices))
      (doseq [dbs [phone laptop]]
        (is (= #{"vocab:hund" "vocab:katze"} (set (:word-ids (await (db/get (:user/db dbs) "c1"))))))
        (is (= 1 (await (live-count dbs)))))))))


(deftest a-collection-renamed-on-two-devices-takes-the-name-changed-last
  (async-testing "the rename with the later renamed-at wins on both devices"
    (with-devices
     (^:async fn
      [{:keys [laptop phone] :as devices}]
      (await (rename! phone "Haus" "2026-02-01"))
      (await (rename! laptop "Tiere" "2026-02-02"))
      (await (settled! devices))
      (doseq [dbs [phone laptop]]
        (is (= "Tiere" (:name (await (db/get (:user/db dbs) "c1"))))))))))


(deftest a-rename-and-a-later-add-word-on-another-device-keep-the-rename-and-both-words
  (async-testing "the name is the renamed one on both devices, and both words are in"
    (with-devices
      (^:async fn
       [{:keys [laptop phone] :as devices}]
       (await (rename! phone "Haus" "2026-02-01"))
       (await (add-word! laptop "vocab:katze"))
       (await (add-word! phone "vocab:hund"))
       (await (settled! devices))
       (doseq [dbs [phone laptop]]
         (is (= "Haus" (:name (await (db/get (:user/db dbs) "c1")))))
         (is (= #{"vocab:hund" "vocab:katze"} (set (:word-ids (await (db/get (:user/db dbs) "c1")))))))))))


(deftest a-change-written-to-the-version-that-lost-is-kept
  (async-testing "a word put on the version no longer served joins the merge"
    (with-devices
     (^:async fn
      [{:keys [laptop phone]}]
      (await (add-word! phone "vocab:hund"))
      (await (add-word! laptop "vocab:katze"))
      (await (replication/replicated! (:user/db laptop) (:user/db phone)))
      (let [served (:_rev (await (db/get (:user/db phone) "c1")))
            lost   (->> (await (pouch/doc-versions phone documents/collection-schema "c1"))
                        (remove #(= served (:_rev %)))
                        first)]
        (await (db/insert (:user/db phone) (update lost :word-ids conj "vocab:maus"))))
      (await (sut/resolve-collection-conflicts! phone ["c1"]))
      (is (= #{"vocab:hund" "vocab:katze" "vocab:maus"}
             (set (:word-ids (await (db/get (:user/db phone) "c1"))))))
      (is (= 1 (await (live-count phone))))))))


(deftest a-merged-collection-edited-again-is-not-taken-for-deleted
  (async-testing "the versions a merge removed leave no deletion behind"
    (with-devices
     (^:async fn
      [{:keys [laptop phone] :as devices}]
      (await (add-word! phone "vocab:hund"))
      (await (add-word! laptop "vocab:katze"))
      (await (settled! devices))
      (await (add-word! laptop "vocab:maus"))
      (await (settled! devices))
      (doseq [dbs [phone laptop]]
        (is (= #{"vocab:hund" "vocab:katze" "vocab:maus"}
               (set (:word-ids (await (db/get (:user/db dbs) "c1"))))))
        (is (= 1 (await (live-count dbs)))))))))


(deftest a-deleted-theme-does-not-come-back-from-a-sibling-version
  (async-testing "deleting a conflicted collection removes both versions, here and on the other device"
    (with-devices
     (^:async fn
      [{:keys [laptop phone]}]
      (await (add-word! phone "vocab:hund"))
      (await (add-word! laptop "vocab:katze"))
      (await (replication/replicated! (:user/db laptop) (:user/db phone)))
      (await (learner/with-learner
              phone
              clock
              (^:async fn
               [{:keys [learner]}]
               (await ((:learner/change! learner) [[:entity/collection "c1" (constantly :entity/deleted)]])))))
      (is (nil? (await (db/get (:user/db phone) "c1"))))
      (is (zero? (await (live-count phone))))
      (await (replication/replicated! (:user/db phone) (:user/db laptop)))
      (await (sut/resolve-collection-conflicts! laptop ["c1"]))
      (is (nil? (await (db/get (:user/db laptop) "c1"))))))))


(deftest a-collection-deleted-on-one-device-and-edited-on-another-comes-back-with-the-edit
  (async-testing "the live version wins on both devices"
    (with-devices
     (^:async fn
      [{:keys [laptop phone] :as devices}]
      (await (add-word! laptop "vocab:katze"))
      (await (learner/with-learner
              phone
              clock
              (^:async fn
               [{:keys [learner]}]
               (await ((:learner/change! learner) [[:entity/collection "c1" (constantly :entity/deleted)]])))))
      (await (settled! devices))
      (doseq [dbs [phone laptop]]
        (is (= ["vocab:katze"] (:word-ids (await (db/get (:user/db dbs) "c1")))))
        (is (= 1 (await (live-count dbs)))))))))


(defn- ^:async pass-pulling!
  "Runs `sync/sync-once!` on `dbs` with a replication that pulled `pulled-ids`, and counts the
   reads of a collection's versions. Resolves with the count."
  [dbs pulled-ids]
  (let [reads (atom 0)
        versions pouch/doc-versions]
    (with-redefs [pouch/sync-once! (fn [_ _ _] (js/Promise.resolve {:pulled 1 :pulled-ids pulled-ids :pulled-revs #{} :pushed 0}))
                  pouch/doc-versions     (fn [& args] (swap! reads inc) (apply versions args))]
      (await (sut/sync-once! dbs "account")))
    @reads))


(deftest a-pull-that-brings-a-conflicted-collection-resolves-it
  (async-testing "the collection the pull names has one live version afterwards"
    (with-devices
      (^:async fn
       [{:keys [laptop phone]}]
       (await (add-word! phone "vocab:hund"))
       (await (add-word! laptop "vocab:katze"))
       (await (replication/replicated! (:user/db laptop) (:user/db phone)))
       (is (= 1 (await (pass-pulling! phone {"collection" #{"c1"}}))))
       (is (= 1 (await (live-count phone))))
       (is (= #{"vocab:hund" "vocab:katze"} (set (:word-ids (await (db/get (:user/db phone) "c1"))))))))))


(deftest a-pull-that-brings-only-words-reads-no-collection
  (async-testing "no versions are read when the pull names no collection"
    (with-devices
      (^:async fn
       [{:keys [phone]}]
       (is (zero? (await (pass-pulling! phone {"vocab" #{"vocab:hund"}}))))))))

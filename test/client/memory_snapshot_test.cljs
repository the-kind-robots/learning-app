(ns client.memory-snapshot-test
  "A start from a snapshot of memory (ADR-0018): the snapshot is written
   once memory is loaded and when the page goes to the background, a start
   takes it and catches up from its feed position, and a snapshot that
   fails a check is deleted and every document is read."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.learner.loader :as loader]
   [adapters.learner.memory :as memory]
   [adapters.learner.snapshot :as snapshot]
   [client.support.caches :as caches]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-seed :as db-seed]
   [client.support.document :as document]
   [client.support.learner :as learner]
   [client.support.wait :as wait]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [db.pouch :as pouch]))


(def user-db-name (db-fixtures/db-name "client.memory-snapshot-test.user"))


(def device-db-name (db-fixtures/db-name "client.memory-snapshot-test.device"))


(def other-user-db-name
  "A user-db that stands for the first one after it lost its last writes."
  (db-fixtures/db-name "client.memory-snapshot-test.other.user"))


(use-fixtures :each (db-fixtures/db-fixture-multi [user-db-name device-db-name other-user-db-name]))


(defn- with-dbs
  "Calls `f` with `{:user/db :device/db :other/db}`: user-db, device-db, and
   a second user-db that starts empty."
  [f]
  (db-fixtures/with-test-dbs
   [user-db-name device-db-name other-user-db-name]
   (fn [[user-db device-db other-user-db]]
     (f {:device/db device-db :other/db other-user-db :user/db user-db}))))


(defn- projection
  "What memory holds, as plain data that compares by value: every entry,
   and the words in list order."
  [memory]
  {:entries (set (memory/entries memory))
   :words   (mapv :id (memory/words memory))})


(def ^:private haus
  {:_id "vocab:das haus" :translation [{:lang "ru" :value "дом"}] :type "vocab" :value "das Haus"})


(defn- ^:async seeded!
  "Seeds words with a review each, a collection, and an example."
  [dbs]
  (await (db-seed/seed-vocabulary! (:user/db dbs)
                                   [{:_id "vocab:der hund" :translation "пёс" :value "der Hund"}
                                    {:_id "vocab:die katze" :translation "кошка" :value "die Katze"}]))
  (await (db/insert (:user/db dbs) {:_id "coll-tiere" :type "collection" :name "Tiere" :word-ids ["vocab:der hund"]}))
  (await (db-seed/seed-examples! (:user/db dbs)
                                 [{:_id         "example-1"
                                   :translation "Пёс спит"
                                   :value       "Der Hund schläft."
                                   :word        "der Hund"
                                   :word-id     "vocab:der hund"}])))


(defn- full-read
  "The memory a full read of user-db `user` gives."
  [user]
  (db-seed/memory-of user))


(defn- ^:async reads-during
  "Calls `f` and resolves with `[result reads]`: what `f` resolved with, and
   what it read meanwhile: `:full`, how many times every document of a
   database was read, and `:since`, for each database the sequence the
   first read of its change log started after."
  [f]
  (let [reads        (atom {:full 0 :since {}})
        read-docs    pouch/read-docs
        read-changes pouch/read-changes]
    (set! pouch/read-docs (fn [& args] (swap! reads update :full inc) (apply read-docs args)))
    (set! pouch/read-changes
          (fn [dbs db-key since limit]
            (swap! reads update-in [:since db-key] #(if (some? %) % since))
            (read-changes dbs db-key since limit)))
    (try
      [(await (f)) @reads]
      (finally
       (set! pouch/read-docs read-docs)
       (set! pouch/read-changes read-changes)))))


(defn- ^:async session!
  "Starts memory over `dbs`, waits until the snapshot written after the load
   is stored (`puts` counts one more), stops, and resolves with the memory
   that was handed over and what the start read (`reads-during`)."
  [dbs puts]
  (let [before @puts
        [{:keys [stop store]} reads] (await (reads-during #(learner/started dbs {})))]
    (await (wait/until #(> @puts before)))
    (stop)
    {:memory (:learner/memory @store) :reads reads}))


(defn- stored
  "The stored snapshot, `{:header :body}`. The Cache API in these tests
   holds no other entry."
  [entries]
  (snapshot/parsed (first (es6-iterator-seq (.values entries)))))


(defn- restore!
  "Stores `snapshot`, `{:header :body}`, in place of the stored one."
  [entries snapshot]
  (.set entries (first (es6-iterator-seq (.keys entries))) (snapshot/joined snapshot)))


(defn- stored-position
  [entries]
  (:position (:header (stored entries))))


(defn- ^:async lost-tail!
  "Makes `(:other/db dbs)` user-db as it would stand after it lost its last
   `n` changes: every change before them, under the same sequences, and the
   same marker. Resolves with the changes it lost."
  [dbs n]
  (let [changes (await (pouch/read-changes dbs :user/db 0 nil))
        other   (:other/db dbs)]
    (doseq [{:keys [doc]} (drop-last n changes)]
      (await (.bulkDocs ^js other (db/clj->couch [doc]) #js {:new_edits false})))
    (await (db/insert other {:_id pouch/marker-id :marker (await (pouch/marker dbs :user/db))}))
    (take-last n changes)))


(defn- ^:async pulled!
  "Writes `doc`, at its own revision, into `db`, as a replication does."
  [db doc]
  (await (.bulkDocs ^js db (db/clj->couch [doc]) #js {:new_edits false})))


(def ^:private format-docs
  "One document of each kept type, a document of a type memory does not
   keep, and a deletion."
  [{:_id         "vocab:der hund"
    :_rev        "1-a"
    :created-at  "2026-01-01T00:00:00.000Z"
    :modified-at "2026-01-02T00:00:00.000Z"
    :translation [{:lang "ru" :value "пёс"}]
    :type        "vocab"
    :value       "der Hund"}
   {:_id         "review-1"
    :_rev        "1-b"
    :created-at  "2026-01-03T00:00:00.000Z"
    :retained    true
    :translation "пёс"
    :type        "review"
    :word-id     "vocab:der hund"}
   {:_id "coll-tiere" :_rev "1-c" :created-at "2026-01-01" :name "Tiere" :type "collection"}
   {:_id           "example:vocab:der hund:coll-tiere:0123456789ab"
    :_rev          "1-d"
    :collection-id "coll-tiere"
    :structure     []
    :translation   "Пёс спит"
    :type          "example"
    :value         "Der Hund schläft."
    :word          "der Hund"
    :word-id       "vocab:der hund"}
   {:_id "task-1" :_rev "1-e" :type "task"}
   {:_id "vocab:weg" :_rev "2-f" :_deleted true}])


(def ^:private format-entries
  "What a snapshot of `format-docs` holds in this format version, by id."
  [{:entity {:id "coll-tiere" :created-at "2026-01-01" :name "Tiere" :word-ids []}
    :kind   :collection
    :rev    "1-c"}
   {:entity {:id            "example:vocab:der hund:coll-tiere:0123456789ab"
             :collection-id "coll-tiere"
             :structure     []
             :translation   "Пёс спит"
             :value         "Der Hund schläft."
             :word          "der Hund"
             :word-id       "vocab:der hund"}
    :kind   :example
    :rev    "1-d"}
   {:entity {:id "review-1" :created-at "2026-01-03T00:00:00.000Z" :retained true :word-id "vocab:der hund"}
    :kind   :review
    :rev    "1-b"}
   {:entity {:id          "vocab:der hund"
             :created-at  "2026-01-01T00:00:00.000Z"
             :modified-at "2026-01-02T00:00:00.000Z"
             :translation [{:lang "ru" :value "пёс"}]
             :value       "der Hund"}
    :kind   :word
    :rev    "1-a"}])


(deftest the-snapshot-format-is-the-one-its-version-names
  (let [memory  (memory/with-docs memory/empty-memory format-docs)
        entries (snapshot/decoded (snapshot/parsed (snapshot/encode memory nil)))]
    (is (= format-entries (sort-by (comp :id :entity) entries))
        (str "What memory takes from a document changed. Change adapters.learner.snapshot/format-version, "
             "so that snapshots of the old format are dropped, and update format-entries here."))))


(deftest a-repeat-start-takes-the-snapshot-and-what-was-stored-since
  (async-testing "documents written before the start: no document is read in full, and memory equals a full read"
    (caches/with-cache-api
     (^:async fn
      [{:keys [entries puts]}]
      (await
       (with-dbs
        (^:async fn
         [dbs]
         (await (seeded! dbs))
         (let [first-start (await (session! dbs puts))]
           (is (= 1 (get-in first-start [:reads :full])) "the first start reads user-db in full, and device-db not at all")
           (is (= (memory/position (:memory first-start)) (stored-position entries))
               "the snapshot carries the position of the memory it holds"))
         (let [position (stored-position entries)]
           ;; Changed past the app, as a replication or another tab does.
           (await (db/insert (:user/db dbs) haus))
           (await (db/remove (:user/db dbs) (await (db/get (:user/db dbs) "vocab:die katze"))))
           (let [tiere (await (db/get (:user/db dbs) "coll-tiere"))]
             (await (db/insert (:user/db dbs) (assoc tiere :name "Tiere!"))))
           (let [[{:keys [stop store]} reads] (await (reads-during #(learner/started dbs {})))
                 restored (:learner/memory @store)]
             (stop)
             (is (zero? (:full reads)) "no document is read in full")
             (is (= {:user/db (:seq position)} (:since reads)) "the change log is read from the stored position")
             (is (= (projection (await (full-read (:user/db dbs)))) (projection restored))
                 "memory equals a full read")
             (is (= "Tiere!" (:name (memory/collection restored "coll-tiere"))))
             (is (nil? (memory/word restored "vocab:die katze"))))))))))))


(deftest a-start-with-nothing-new-writes-no-snapshot
  (async-testing "memory's position equals the snapshot's: the writer does not write"
    (caches/with-cache-api
     (^:async fn
      [{:keys [puts]}]
      (await
       (with-dbs
        (^:async fn
         [dbs]
         (await (seeded! dbs))
         (await (session! dbs puts))
         (let [asked  (atom 0)
               marker pouch/marker]
           (try
             (let [{:keys [stop]} (await (learner/started dbs {}))]
               ;; The writer asks for the markers as soon as it decides to
               ;; write, in the task after the hand-over; the task this test
               ;; waits for comes after that one.
               (set! pouch/marker (fn [& args] (swap! asked inc) (apply marker args)))
               (await (wait/settled))
               (stop))
             (finally
              (set! pouch/marker marker)))
           (is (zero? @asked) "the writer decided not to write")
           (is (= 1 @puts))))))))))


(defn- ^:async dropped-on
  "Seeds, starts once to store a snapshot, spoils it with `spoil!`, and
   starts again. Checks that the second start read every document, that
   memory equals a full read, and that a new snapshot replaced the spoiled
   one."
  [label spoil!]
  (await
   (caches/with-cache-api
    (^:async fn
     [{:keys [entries puts]}]
     (await
      (with-dbs
       (^:async fn
        [dbs]
        (await (seeded! dbs))
        (await (session! dbs puts))
        (await (spoil! dbs entries))
        (let [{:keys [memory reads]} (await (session! dbs puts))]
          (is (= 1 (:full reads)) (str label ": user-db is read in full"))
          (is (= (projection (await (full-read (:user/db dbs)))) (projection memory))
              (str label ": memory equals a full read"))
          (is (= snapshot/format-version (:version (:header (stored entries))))
              (str label ": a new snapshot is written"))))))))))


(deftest a-snapshot-of-another-format-version-is-dropped
  (async-testing "another version: a full read"
    (dropped-on "version" (fn [_ entries] (restore! entries (assoc-in (stored entries) [:header :version] 0))))))


(deftest a-damaged-snapshot-is-dropped
  (async-testing "the body does not match the checksum: a full read"
    (dropped-on "checksum" (fn [_ entries] (restore! entries (update (stored entries) :body str " "))))))


(deftest a-snapshot-of-another-database-is-dropped
  (async-testing "user-db's marker differs from the stored one: a full read"
    (dropped-on "marker"
                (^:async fn
                 [dbs _]
                 (let [local (await (db/get (:user/db dbs) pouch/marker-id))]
                   (await (db/insert (:user/db dbs) (assoc local :marker "another"))))))))


(deftest a-snapshot-ahead-of-its-database-is-dropped
  (async-testing "a stored position past user-db's feed: a full read"
    (dropped-on "position"
                (fn [_ entries]
                  (restore! entries
                            (assoc-in (stored entries) [:header :position :seq] 1000000))))))


(defn- ^:async after-lost-tail
  "Seeds, stores a snapshot, makes the other user-db stand for user-db after
   it lost its last `n` changes, lets `written!` write to it before the
   start, as `check-incoming-auth!` or a replication could, and starts on
   it. Resolves with `{:memory :reads :full-read}`: the memory handed over,
   what the start read (`reads-during`), and what a full read gives."
  [n written!]
  (await
   (caches/with-cache-api
    (^:async fn
     [{:keys [puts]}]
     (await
      (with-dbs
       (^:async fn
        [dbs]
        (await (seeded! dbs))
        (await (session! dbs puts))
        (await (written! (:other/db dbs) (await (lost-tail! dbs n))))
        (let [lost (assoc dbs :user/db (:other/db dbs))
              [{:keys [stop store]} reads] (await (reads-during #(learner/started lost {})))]
          (stop)
          {:full-read (await (full-read (:other/db dbs)))
           :memory    (:learner/memory @store)
           :reads     reads}))))))))


(deftest a-write-before-the-start-over-a-lost-change-drops-the-snapshot
  (async-testing "user-db lost its last change, and another document was written under its sequence"
    (let [{:keys [full-read memory reads]}
          (await (after-lost-tail 1
                                  (fn [other _]
                                    (db/insert other {:_id "vocab:der fremde" :type "vocab" :value "der Fremde"}))))]
      (is (= 1 (:full reads)) "user-db is read in full")
      (is (= (projection full-read) (projection memory)) "memory equals a full read"))))


(deftest a-pull-before-the-start-that-brings-the-lost-change-back-keeps-the-snapshot
  (async-testing "user-db lost its last change, and a replication brought the same revision back"
    (let [{:keys [full-read memory reads]}
          (await (after-lost-tail 1 (fn [other [{:keys [doc]}]] (pulled! other doc))))]
      (is (zero? (:full reads)) "the snapshot is taken")
      (is (= (projection full-read) (projection memory)) "memory equals a full read"))))


(deftest a-refused-snapshot-is-deleted-by-the-check
  (async-testing "the check deletes a snapshot it refuses, before memory is read"
    (caches/with-cache-api
     (^:async fn
      [{:keys [entries puts]}]
      (await
       (with-dbs
        (^:async fn
         [dbs]
         (await (seeded! dbs))
         (await (session! dbs puts))
         (restore! entries (assoc-in (stored entries) [:header :position :seq] 1000000))
         (is (nil? (await (loader/checked-snapshot dbs))))
         (is (zero? (.-size entries)) "the snapshot is gone"))))))))


(deftest a-snapshot-is-written-when-the-page-goes-to-the-background
  (async-testing "a change, then the page hidden: the snapshot holds the change and its position"
    (document/with-document
     (^:async fn
      [document]
      (await
       (caches/with-cache-api
        (^:async fn
         [{:keys [entries puts]}]
         (await
          (with-dbs
           (^:async fn
            [dbs]
            (await (seeded! dbs))
            (let [{:keys [stop store]} (await (learner/started dbs {}))]
              (await (wait/until #(= 1 @puts)))
              (await (db/insert (:user/db dbs) haus))
              (await (wait/until #(memory/word (:learner/memory @store) "vocab:das haus")))
              (aset document "visibilityState" "hidden")
              (.dispatchEvent document (js/Event. "visibilitychange"))
              (await (wait/until #(= 2 @puts)))
              (stop)
              (is (some #(= "vocab:das haus" (get-in % [:entity :id])) (snapshot/decoded (stored entries))))
              (is (= (memory/position (:learner/memory @store)) (stored-position entries))))))))))))))

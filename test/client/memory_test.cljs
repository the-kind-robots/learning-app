(ns client.memory-test
  "The learner's data in memory as a projection of the local databases
   (ADR-0016): loaded, following the feed, taking this app's writes only once
   PouchDB accepted them."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.memory :as sut]
   [adapters.memory-loader :as loader]
   [adapters.reviews :as reviews]
   [adapters.words :as words]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-seed :as db-seed]
   [cljs.test :refer-macros [deftest is testing use-fixtures]]
   [db :as db]
   [db.pouch :as pouch]
   [domain.retention :as retention]))


(def user-db-name (db-fixtures/db-name "client.memory-test.user"))


(def device-db-name (db-fixtures/db-name "client.memory-test.device"))


(use-fixtures :each (db-fixtures/db-fixture-multi [user-db-name device-db-name]))


(defn- with-test-dbs
  [f]
  (db-fixtures/with-test-dbs
   [user-db-name device-db-name]
   (fn [[user-db device-db]]
     (f {:dbs/write-listener (atom nil)
         :device/db      device-db
         :user/db        user-db}))))


(defn- word
  "The word `id` in `memory`."
  [memory id]
  (some #(when (= id (:id %)) %) (vals (:words memory))))


(defn- review-ids
  [memory word-id]
  (some->> (get-in memory [:slot-of word-id]) (nth (:cards memory)) :reviews :ids vec))


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
  "Starts memory over `dbs`, with a dispatch that does what the three memory
   effects do to an atom. Returns [memory readiness stop], `stop` a promise
   of the function that stops following."
  [dbs]
  (let [memory    (atom sut/empty-memory)
        readiness (atom nil)
        dispatch  (fn [[[effect docs]]]
                    (swap! memory sut/with-docs docs)
                    (case effect
                      :effect/memory-loaded-basic (reset! readiness :basic)
                      :effect/memory-loaded-full  (reset! readiness :full)
                      nil))]
    [memory readiness (loader/start! dbs dispatch)]))


(defn- loaded?
  [readiness]
  #(= :full @readiness))


(def ^:private hund
  {:_id "vocab:der hund" :type "vocab" :value "der Hund" :translation [{:lang "ru" :value "пёс"}]})


(deftest a-document-is-projected-once-per-revision
  (let [doc     (assoc hund :_rev "1-a")
        once    (sut/with-docs sut/empty-memory [doc])
        twice   (sut/with-docs once [doc])
        deleted (sut/with-docs once [{:_deleted true :_id "vocab:der hund" :_rev "2-b"}])]
    (is (= "der Hund" (:value (word once "vocab:der hund"))))
    (is (= "der hund\nпёс" (:search (word once "vocab:der hund")))
        "the search text is normalised once, value and translations a line each")
    (is (identical? once twice) "the revision memory holds changes nothing")
    (is (nil? (word deleted "vocab:der hund")) "a deletion removes the word")
    (is (nil? (:_rev (word once "vocab:der hund"))) "no storage name reaches the entity")))


(deftest a-review-tombstone-leaves-its-word
  (testing "a deleted review carries no word id; memory's own record says which word it was"
    (let [review {:_id        "r1"
                  :_rev       "1-a"
                  :type       "review"
                  :word-id    "vocab:der hund"
                  :created-at "2026-01-01T00:00:00Z"
                  :retained   true}
          held   (sut/with-docs sut/empty-memory [review])
          gone   (sut/with-docs held [{:_deleted true :_id "r1" :_rev "2-b"}])]
      (is (= ["r1"] (review-ids held "vocab:der hund")))
      (is (empty? (review-ids gone "vocab:der hund"))))))


(deftest same-second-reviews-give-one-retention-whatever-their-order
  (testing "reviews tied on time are taken in id order, not arrival order"
    (let [word {:_id "vocab:w" :_rev "1-a" :type "vocab" :value "w" :translation []}
          reviews
          [{:_id "r1" :_rev "1-a" :type "review" :word-id "vocab:w" :created-at "2026-01-01T00:00:00Z" :retained true}
           {:_id "r2" :_rev "1-a" :type "review" :word-id "vocab:w" :created-at "2026-01-01T00:00:00Z" :retained false}
           {:_id "r0" :_rev "1-a" :type "review" :word-id "vocab:w" :created-at "2025-12-01T00:00:00Z" :retained true}]
          state (fn [docs]
                  (let [memory (sut/with-docs sut/empty-memory docs)]
                    (retention/urgency (:reviews (nth (:cards memory) (get-in memory [:slot-of "vocab:w"]))) 1790000000000)))]
      (is (= (state (cons word reviews))
             (state (cons word (reverse reviews))))))))


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
        (await (until (loaded? ready?)))
        (is (= ["vocab:der hund"] (map :id (vals (:words @memory)))))
        (is (= 1 (count (review-ids @memory "vocab:der hund"))))
        (is (= ["example-1"] (keys (:examples @memory))))
        ;; Written past `db.pouch`, as a replication or another tab writes.
        (await (db/insert (:user/db dbs)
                          {:_id         "vocab:die katze"
                           :type        "vocab"
                           :value       "die Katze"
                           :translation [{:lang "ru" :value "кошка"}]}))
        (await (until #(word @memory "vocab:die katze")))
        (let [{doc :_rev} (await (db/get (:user/db dbs) "vocab:die katze"))]
          (await (db/remove (:user/db dbs) {:_id "vocab:die katze" :_rev doc})))
        (await (until #(nil? (word @memory "vocab:die katze"))))
        (is (nil? (word @memory "vocab:die katze")))
        ((await stop)))))))


(deftest an-own-write-is-in-memory-when-the-writer-resumes
  (async-testing "write-through: memory holds the document once PouchDB accepted it"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [[memory ready? stop] (started dbs)]
        (await (until (loaded? ready?)))
        (await (pouch/insert dbs words/schema (dissoc hund :type)))
        (is (= "der Hund" (:value (word @memory "vocab:der hund")))
            "no feed round trip: the write's own promise is enough")
        ((await stop)))))))


(deftest a-refused-write-leaves-memory-untouched
  (async-testing "a conflict writes nothing, and memory takes nothing"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (db/insert (:user/db dbs) hund))
      (let [[memory ready? stop] (started dbs)]
        (await (until (loaded? ready?)))
        (let [before @memory
              failed (try
                       (await (pouch/insert dbs words/schema (assoc (dissoc hund :type) :value "der Pudel")))
                       false
                       (catch :default _ true))]
          (is failed "the premise: no revision, so PouchDB refuses it")
          (is (= before @memory)))
        ((await stop)))))))


(deftest a-bulk-write-reports-only-what-it-wrote
  (async-testing "the documents of a bulk write each arrive at their new revision"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [[memory ready? stop] (started dbs)]
        (await (until (loaded? ready?)))
        (await (pouch/bulk-docs dbs
                                reviews/schema
                                [{:_id "r1" :type "review" :word-id "w" :retained true :created-at "2026-01-01"}
                                 {:_id "r2" :type "review" :word-id "w" :retained false :created-at "2026-01-02"}]))
        (is (= ["r1" "r2"] (review-ids @memory "w")))
        ((await stop)))))))


(deftest the-words-and-collections-come-first
  (async-testing "the basic load carries what opening the app and adding a word need, and nothing else"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "vocab:der hund" :value "der Hund" :translation "пёс"}]))
      (await (db/insert (:user/db dbs) {:type "collection" :name "Tiere" :word-ids ["vocab:der hund"]}))
      (let [effects (atom [])
            ;; Design documents come with the rest, as documents of no type
            ;; memory holds.
            stop    (loader/start! dbs (fn [[[effect docs]]] (swap! effects conj [effect (set (keep :type docs))])))]
        (await (until #(= 2 (count @effects))))
        (is (= [[:effect/memory-loaded-basic #{"vocab" "collection"}]
                [:effect/memory-loaded-full #{"review" "collection"}]]
               @effects)
            "the rest does not read the words again")
        ((await stop)))))))

(ns client.learner-test
  "The learner's data through its port (`adapters.learner`): each write reads
   the document it changes from PouchDB by id and writes PouchDB, and memory
   takes the write from the change feed (ADR-0016)."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.learner :as adapter]
   [adapters.learner.documents :as documents]
   [adapters.learner.loader :as loader]
   [adapters.learner.memory :as memory]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.db-seed :as db-seed]
   [client.support.document :as document]
   [client.support.learner :as learner]
   [client.support.time :as time]
   [client.support.wait :as wait]
   [cljs.test :refer-macros [deftest is testing use-fixtures]]
   [clojure.string :as str]
   [db :as db]
   [db.pouch :as pouch]
   [use-cases.examples :as examples]
   [use-cases.vocabulary :as vocabulary]))


(def user-db-name (db-fixtures/db-name "client.learner-test.user"))


(def device-db-name (db-fixtures/db-name "client.learner-test.device"))


(use-fixtures :each (db-fixtures/db-fixture-multi [user-db-name device-db-name]))


(def ^:private clock
  {:clock/now-iso time/now-iso
   :clock/now-ms  time/now-ms})


(defn- with-dbs
  [f]
  (db-fixtures/with-test-dbs
   [user-db-name device-db-name]
   (fn [[user-db device-db]]
     (f {:device/db device-db :user/db user-db}))))


(defn- ^:async with-learner
  "Seeds `docs` into user-db, where every type of the learner's data lives,
   then calls `f` with the learner port over the databases, the
   store that keeps memory, and the function that stops following the
   feeds."
  [docs f]
  (await
   (with-dbs
    (^:async fn
     [dbs]
     (doseq [doc docs]
       (await (db/insert (:user/db dbs) doc)))
     (await (learner/with-learner dbs clock #(f (assoc % :dbs dbs))))))))


(def ^:private hund
  {:_id         "vocab:der hund"
   :type        "vocab"
   :value       "der Hund"
   :translation [{:lang "ru" :value "пёс"}]
   :created-at  "2026-01-01T00:00:00.000Z"})


(defn- stored-word
  "The word `id` that the memory in `store` has."
  [store id]
  (memory/word (:learner/memory @store) id))


(deftest an-own-write-is-in-memory-when-it-resolves
  (async-testing "the write comes back through the change feed before the writer resumes"
    (with-learner
     [hund]
     (^:async fn
      [{:keys [learner store]}]
      (await ((:learner/update-word! learner) "vocab:der hund" #(assoc % :value "der Hund!")))
      (is (= "der Hund!" (:value (stored-word store "vocab:der hund"))))
      (is (= "2026-01-01T00:00:00.000Z" (:created-at (stored-word store "vocab:der hund")))
          "the stored creation stays")
      (testing "and a second write lands on the first"
        (await ((:learner/update-word! learner) "vocab:der hund" #(assoc % :value "der Hund!!")))
        (is (= "der Hund!!" (:value (stored-word store "vocab:der hund")))))))))


(deftest a-write-is-made-to-the-stored-version
  (async-testing "a newer revision written past the learner is what the change is made to"
    (with-learner
     [hund]
     (^:async fn
      [{:keys [dbs learner store]}]
      (let [stored (await (db/get (:user/db dbs) "vocab:der hund"))]
        (await (db/insert (:user/db dbs) (assoc stored :value "der Pudel"))))
      (let [written (await ((:learner/update-word! learner) "vocab:der hund" #(update % :value str "!")))]
        (is (= "der Pudel!" (:value written) (:value (await (db/get (:user/db dbs) "vocab:der hund")))))
        (is (= "der Pudel!" (:value (stored-word store "vocab:der hund"))))
        ;; The feed's batch with "der Pudel", read before the write, comes
        ;; after it and changes nothing.
        (await (wait/settled))
        (is (= "der Pudel!" (:value (stored-word store "vocab:der hund")))
            "the older revision does not come back"))))))


(deftest a-write-goes-to-the-winner-of-a-conflict
  (async-testing "a pull leaves two branches, and the change lands on the winner"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       ;; Two branches of one word, as replication leaves them: this
       ;; device's at generation 1, the pulled one at generation 3, which
       ;; wins.
       (await (.bulkDocs ^js (:user/db dbs)
                         (clj->js [(assoc hund :_rev "1-a")
                                   (assoc hund
                                          :_rev       "3-c"
                                          :value      "der Pudel"
                                          :_revisions {:ids ["c" "b" "z"] :start 3})])
                         #js {:new_edits false}))
       ;; The premise: PouchDB takes a write on the losing leaf without a
       ;; conflict, and the winner stays as it was.
       (await (db/insert (:user/db dbs) (assoc hund :_rev "1-a" :value "der Dackel")))
       (is (= "der Pudel" (:value (await (db/get (:user/db dbs) "vocab:der hund")))))
       (await
        (learner/with-learner dbs
                              clock
                              (^:async fn
                               [{:keys [learner]}]
                               (await ((:learner/update-word! learner) "vocab:der hund" #(update % :value str "!")))
                               (is (= "der Pudel!" (:value (await (db/get (:user/db dbs) "vocab:der hund")))))))))))))


(def ^:private tiere
  {:_id "coll-tiere" :type "collection" :name "Tiere" :word-ids ["vocab:der hund"] :created-at "2026-01-01"})


(deftest a-collection-changes
  (async-testing "a word joins, leaves, and the name changes, each written to the stored collection"
    (with-learner
     [hund tiere]
     (^:async fn
      [{:keys [dbs learner store]}]
      (let [stored (fn ^:async f [] (await (db/get (:user/db dbs) "coll-tiere")))
            rev-1  (:_rev (await (stored)))]
        (await ((:learner/add-to-collection! learner) "vocab:der hund" "coll-tiere"))
        (is (= rev-1 (:_rev (await (stored)))) "a word the collection lists is not written again")
        (await ((:learner/add-to-collection! learner) "vocab:die katze" "coll-tiere"))
        (is (= ["vocab:der hund" "vocab:die katze"] (:word-ids (await (stored)))))
        (await ((:learner/remove-from-collection! learner) "vocab:der hund" "coll-tiere"))
        (is (= ["vocab:die katze"] (:word-ids (await (stored)))))
        (await ((:learner/rename-collection! learner) "coll-tiere" "Haustiere"))
        (is (= {:id "coll-tiere" :created-at "2026-01-01" :name "Haustiere" :word-ids ["vocab:die katze"]}
               (get-in @store [:learner/memory :collections "coll-tiere"]))
            "memory has each change when the write resolves"))))))


(deftest a-rename-keeps-a-change-made-elsewhere
  (async-testing "a collection written past the learner keeps that change when the learner renames it at once"
    (with-learner
     [hund tiere]
     (^:async fn
      [{:keys [dbs learner]}]
      (let [stored (await (db/get (:user/db dbs) "coll-tiere"))]
        (await (db/insert (:user/db dbs) (update stored :word-ids conj "vocab:die katze"))))
      (await ((:learner/rename-collection! learner) "coll-tiere" "Haustiere"))
      (is (= {:name "Haustiere" :word-ids ["vocab:der hund" "vocab:die katze"]}
             (select-keys (await (db/get (:user/db dbs) "coll-tiere")) [:name :word-ids])))))))


(def ^:private review
  {:_id "review-1" :type "review" :word-id "vocab:der hund" :retained true :created-at "2026-01-02T00:00:00.000Z"})


(def ^:private example
  {:_id           "example-1"
   :type          "example"
   :word-id       "vocab:der hund"
   :collection-id "coll-tiere"
   :value         "Satz"
   :translation   "фраза"})


(deftest a-word-is-deleted-with-its-place-in-collections
  (async-testing "the word and its place in every collection go in one write; its reviews and examples stay"
    (with-learner
     [hund review tiere example {:_id "vocab:die katze" :type "vocab" :value "die Katze"}]
     (^:async fn
      [{:keys [dbs learner store]}]
      ;; Another device put the word in a second collection just now.
      (await (db/insert (:user/db dbs) {:_id "coll-haus" :type "collection" :name "Haus" :word-ids ["vocab:der hund"]}))
      (is (true? (await ((:learner/delete-word! learner) "vocab:der hund"))))
      (is (= {"coll-haus" [] "coll-tiere" []}
             (into {} (map (juxt :_id :word-ids)) (await (db-queries/fetch-by-type (:user/db dbs) "collection"))))
          "no collection lists the word, the one memory had not seen included")
      (is (= ["vocab:die katze"] (map :_id (await (db-queries/fetch-by-type (:user/db dbs) "vocab")))))
      (is (= ["review-1"] (map :_id (await (db-queries/fetch-by-type (:user/db dbs) "review")))))
      (is (= ["example-1"] (map :_id (await (db-queries/fetch-by-type (:user/db dbs) "example")))))
      (is (nil? (stored-word store "vocab:der hund")))
      (is (= [] (get-in @store [:learner/memory :collections "coll-tiere" :word-ids])))
      (is (nil? (await ((:learner/delete-word! learner) "vocab:der hund")))
          "a word PouchDB does not have is nothing to delete")))))


(deftest a-word-added-again-has-its-old-history
  (async-testing "after a delete, the word added again has the reviews it had, no new one, and no second example"
    (with-learner
     [hund review example]
     (^:async fn
      [{:keys [dbs learner store]}]
      (await ((:learner/delete-word! learner) "vocab:der hund"))
      (let [requests     (atom [])
            capabilities {:learner (assoc learner
                                          :learner/active-collection (constantly nil)
                                          :learner/request-examples! #(swap! requests into %))}
            {:keys [created?]} (await (vocabulary/add! capabilities "der Hund" "пёс" :word))
            memory       (:learner/memory @store)]
        (is (true? created?))
        (is (= ["review-1"] (map :_id (await (db-queries/fetch-by-type (:user/db dbs) "review"))))
            "no initial review is written")
        (is (= ["review-1"] (array-seq (:ids (:reviews (first (memory/collection-cards memory nil))))))
            "the card a lesson draws has the old review alone, so retention comes from it")
        (is (empty? @requests) "the word has an example in «Всё подряд» already"))))))


(deftest a-collection-is-deleted-alone
  (async-testing
    "the collection goes; its words and examples stay; a collection made again does not show them, «Всё подряд» does"
    (with-learner
     [hund tiere example (assoc example :_id "example-2" :collection-id "coll-other")]
     (^:async fn
      [{:keys [dbs learner store]}]
      (await ((:learner/delete-collection! learner) "coll-tiere"))
      (is (empty? (await (db-queries/fetch-by-type (:user/db dbs) "collection"))))
      (is (nil? (get-in @store [:learner/memory :collections "coll-tiere"])))
      (is (= 1 (count (await (db-queries/fetch-by-type (:user/db dbs) "vocab")))))
      (is (= #{"example-1" "example-2"} (set (map :_id (await (db-queries/fetch-by-type (:user/db dbs) "example"))))))
      (let [{:keys [id]} (await ((:learner/create-collection! learner) "Tiere"))
            memory       (:learner/memory @store)]
        (is (empty? (examples/visible-in (memory/examples-of memory ["vocab:der hund"]) id))
            "the made-again collection has a new id, and the old examples do not show in it")
        (is (= #{"example-1" "example-2"}
               (set (map :id (examples/visible-in (memory/examples-of memory ["vocab:der hund"]) nil))))
            "«Всё подряд» shows them like any other example"))))))


(deftest a-pull-and-an-add-of-the-same-word-keep-both-translations
  (async-testing "the add is made to the pulled revision, whether or not memory has it yet"
    (with-learner
     [hund]
     (^:async fn
      [{:keys [dbs learner]}]
      (let [stored (await (db/get (:user/db dbs) "vocab:der hund"))]
        (await (db/insert (:user/db dbs) (update stored :translation conj {:lang "ru" :value "собака"}))))
      (let [added (await ((:learner/add-word! learner)
                          {:id "vocab:der hund" :translation [{:lang "ru" :value "дворняга"}] :value "der Hund"}))]
        (is (false? (:created? added)))
        (is (= ["пёс" "собака" "дворняга"]
               (mapv :value (:translation (await (db/get (:user/db dbs) "vocab:der hund")))))))))))


(deftest a-word-re-created-on-another-device-keeps-its-reviews
  (async-testing "a word deleted here and made again elsewhere keeps the reviews of both"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (await (db/insert (:user/db dbs) hund))
       (await (db/insert (:user/db dbs) review))
       (await
        (learner/with-learner dbs clock (fn [{:keys [learner]}] ((:learner/delete-word! learner) "vocab:der hund"))))
       ;; Another device made the word again, with a review, and a pull
       ;; brought both.
       (await (db/insert (:user/db dbs) (dissoc hund :_rev)))
       (await (db/insert (:user/db dbs) (assoc review :_id "review-2")))
       (dotimes [_ 2]
         (await (learner/with-learner dbs clock (fn [_] (js/Promise.resolve nil)))))
       (is (= #{"review-1" "review-2"} (set (map :_id (await (db-queries/fetch-by-type (:user/db dbs) "review"))))))
       (is (= 1 (count (await (db-queries/fetch-by-type (:user/db dbs) "vocab"))))))))))


(deftest a-change-stored-during-the-load-is-in-memory-when-it-is-handed-over
  (async-testing "a collection deleted while memory loads is gone from memory when the load hands it over"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (await (db/insert (:user/db dbs) hund))
       (await (db/insert (:user/db dbs) tiere))
       (let [store    (atom {})
             dispatch (learner/store-dispatch store)
             read     pouch/read-docs]
         ;; The deletion lands after user-db is read and before memory is
         ;; handed over.
         (set! pouch/read-docs
               (fn ^:async read-docs [dbs db-key range]
                 (let [docs (await (read dbs db-key range))]
                   (when (= :user/db db-key)
                     (await (db/remove (:user/db dbs) (await (db/get (:user/db dbs) "coll-tiere")))))
                   docs)))
         (try
           (let [stop (await (loader/start! dbs store dispatch))]
             (set! pouch/read-docs read)
             (is (true? (:learner/loaded? @store)))
             (is (nil? (memory/collection (:learner/memory @store) "coll-tiere")))
             (is (some? (stored-word store "vocab:der hund")))
             (stop))
           (finally
            (set! pouch/read-docs read)))))))))


(defn- failing-once
  "`f`, except that its first call rejects as a locked database would."
  [f]
  (let [failed? (atom false)]
    (fn [& args]
      (if @failed?
        (apply f args)
        (do (reset! failed? true)
            (js/Promise.reject (js/Error. "locked")))))))


(deftest a-failed-read-at-start-asks-for-a-reload-and-is-not-tried-again
  (async-testing "a step of the read fails once: the store is marked unreadable, and nothing reads again"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (await (db/insert (:user/db dbs) hund))
       (loop [steps
              [{:step "the feed positions" :original pouch/feed-position :stub! #(set! pouch/feed-position %)}
               {:step "what was stored meanwhile" :original pouch/read-changes :stub! #(set! pouch/read-changes %)}]]
         (when-let [[{:keys [step original stub!]} & more] (seq steps)]
           (let [store (atom {})]
             ;; A second try would succeed, so a read that tried again would
             ;; hand memory over.
             (stub! (failing-once original))
             (try
               (let [stop (await (loader/start! dbs store (learner/store-dispatch store)))]
                 (is (nil? stop) step)
                 (is (true? (:learner/unreadable? @store)) step)
                 (is (not (:learner/loaded? @store)) step)
                 (is (nil? (stored-word store "vocab:der hund")) step))
               (finally
                (stub! original))))
           (recur more))))))))


(defn- catch-up-only-feeds
  "A stand-in for `db.pouch/follow-changes` whose live feed reports
   nothing, so a change reaches memory only through a catch-up: it reads
   the change log and hands on what it read."
  [dbs db-key since f]
  (let [handed-seq (volatile! since)]
    {:catch-up! (fn ^:async catch-up! []
                  (when-let [changes (seq (await (pouch/read-changes dbs db-key @handed-seq nil)))]
                    (let [position (:position (last changes))]
                      (vreset! handed-seq (:seq position))
                      (f (mapv :doc changes) position))))
     :stop!     (fn [])}))


(defn- ^:async with-catch-up-only-feeds
  "Calls `f` with the change feeds replaced by `catch-up-only-feeds` for
   the time of the promise `f` returns."
  [f]
  (let [follow pouch/follow-changes]
    (set! pouch/follow-changes catch-up-only-feeds)
    (try
      (await (f))
      (finally
       (set! pouch/follow-changes follow)))))


(deftest a-delete-refused-twice-leaves-the-word-as-it-was
  (async-testing "PouchDB refuses the word's deletion twice: the delete fails, and the word is in its collection again"
    (with-learner
     [hund tiere]
     (^:async fn
      [{:keys [dbs learner]}]
      (let [bulk pouch/bulk-docs]
        ;; Every write of the word's deletion is refused.
        (set! pouch/bulk-docs (fn [dbs schema docs] (bulk dbs schema (remove :_deleted docs))))
        (try
          (is (false? (await ((:learner/delete-word! learner) "vocab:der hund"))))
          (is (some? (await (db/get (:user/db dbs) "vocab:der hund"))) "the word lives")
          (is (= ["vocab:der hund"] (:word-ids (await (db/get (:user/db dbs) "coll-tiere"))))
              "and is in its collection again")
          (finally
           (set! pouch/bulk-docs bulk))))))))


(deftest a-change-the-feed-dropped-is-caught-when-the-page-is-shown-again
  (async-testing "a write no feed reported is in memory once the page becomes visible"
    (document/with-document
     (fn [document]
       (with-catch-up-only-feeds
        (fn []
          (with-learner
           []
           (^:async fn
            [{:keys [dbs store]}]
            (await (db/insert (:user/db dbs) hund))
            (await (wait/settled))
            (is (nil? (stored-word store "vocab:der hund")) "the premise: nothing brought it")
            (.dispatchEvent document (js/Event. "visibilitychange"))
            (await (wait/until #(stored-word store "vocab:der hund")))
            (is (some? (stored-word store "vocab:der hund")))))))))))


;;
;; Examples an earlier build kept in device-db move to user-db (#528)
;;


(def ^:private kept-on-device
  "Examples as an earlier build kept them in device-db, under generated ids:
   two different ones of one pair, the second of them twice, one of the
   pair in Tiere, one of a word deleted since, and one of a collection
   deleted since."
  [{:_id "1A2B" :translation "перевод" :value "älter" :word "der Hund" :word-id "vocab:der hund"}
   {:_id "9F8E" :translation "перевод" :value "neuer" :word "der Hund" :word-id "vocab:der hund"}
   {:_id "9F8F" :translation "перевод" :value "neuer" :word "der Hund" :word-id "vocab:der hund"}
   {:_id           "5C5C"
    :collection-id "coll-tiere"
    :translation   "перевод"
    :value         "Tiere"
    :word          "der Hund"
    :word-id       "vocab:der hund"}
   {:_id "6D6D" :translation "перевод" :value "fremd" :word "die Katze" :word-id "vocab:die katze"}
   {:_id           "7E7E"
    :collection-id "coll-gone"
    :translation   "перевод"
    :value         "gelöscht"
    :word          "der Hund"
    :word-id       "vocab:der hund"}])


(defn- ^:async seed-device!
  "Seeds `examples` into device-db, beside a queued fetch and the device's
   identity."
  [dbs examples]
  (await (db-seed/seed-examples! (:device/db dbs) examples))
  (await (db/insert (:device/db dbs) {:_id "task:example-fetch:vocab:die katze:" :task-type "example-fetch" :type "task"}))
  (await (db/insert (:device/db dbs) {:_id "identity" :type "identity"})))


(defn- ^:async stored-by-value
  "The example documents of `db`, by their sentence."
  [db]
  (into {} (map (juxt :value identity)) (await (db-queries/fetch-examples db))))


(defn- adapter
  "The learner adapter over `dbs` and `store`, as the port runs it."
  [dbs store]
  {:clock clock :dbs dbs :store store})


(defn- ^:async with-insert-all
  "Calls `f` while `db.pouch/insert-all-if-absent` is `stub`, which is
   handed the real one, and puts the real one back after."
  [stub f]
  (let [real pouch/insert-all-if-absent]
    (set! pouch/insert-all-if-absent (stub real))
    (try
      (await (f))
      (finally
        (set! pouch/insert-all-if-absent real)))))


(deftest the-device-s-examples-move-to-user-db
  (async-testing "every distinct example moves, those of deleted words and collections included; device-db keeps no example"
    (with-learner
     [hund tiere]
     (^:async fn
      [{:keys [dbs store]}]
      (await (seed-device! dbs kept-on-device))
      (is (= 6 (await (adapter/move-device-examples! (adapter dbs store)))) "every device-db example is deleted")
      (let [moved (await (stored-by-value (:user/db dbs)))]
        (is (= #{"älter" "neuer" "Tiere" "fremd" "gelöscht"} (set (keys moved)))
            "both examples of one pair are kept, the same one twice is one, and a deleted word's or collection's are kept too")
        (is (= (documents/example-doc "vocab:der hund" "der Hund" "coll-tiere" (moved "Tiere"))
               (dissoc (moved "Tiere") :_rev))
            "a moved example is the document a fetch of it would have written"))
      (is (empty? (await (db-queries/fetch-examples (:device/db dbs)))) "device-db holds no example")
      (is (= #{"task:example-fetch:vocab:die katze:" "identity"}
             (set (remove #(str/starts-with? % "_design/") (map :id (:rows (await (db/all-docs (:device/db dbs))))))))
          "the queue and the identity stay")
      (is (= #{"älter" "neuer" "Tiere" "gelöscht"}
             (set (map :value (memory/examples-of (:learner/memory @store) ["vocab:der hund"]))))
          "memory has the moved examples when the move resolves")))))


(deftest a-move-run-again-writes-nothing
  (async-testing "a second move finds nothing to move and leaves user-db as it was"
    (with-learner
     [hund tiere]
     (^:async fn
      [{:keys [dbs store]}]
      (await (seed-device! dbs kept-on-device))
      (await (adapter/move-device-examples! (adapter dbs store)))
      (let [before (await (stored-by-value (:user/db dbs)))]
        (is (zero? (await (adapter/move-device-examples! (adapter dbs store)))))
        (is (= before (await (stored-by-value (:user/db dbs)))) "no revision changed"))))))


(deftest an-interrupted-move-finishes-on-the-next-run
  (async-testing "one example was written and no device-db copy was deleted: the next run finishes"
    (with-learner
     [hund tiere]
     (^:async fn
      [{:keys [dbs store]}]
      (await (seed-device! dbs kept-on-device))
      ;; What the interrupted run wrote before it stopped.
      (let [doc     (documents/example-doc "vocab:der hund" "der Hund" nil (second kept-on-device))
            written (:rev (await (db/insert (:user/db dbs) doc)))]
        (is (= 6 (await (adapter/move-device-examples! (adapter dbs store)))))
        (is (empty? (await (db-queries/fetch-examples (:device/db dbs)))))
        (is (= written (:_rev (await (db/get (:user/db dbs) (:_id doc)))))
            "the example written before is not written again")
        (is (= #{"älter" "neuer" "Tiere" "fremd" "gelöscht"} (set (keys (await (stored-by-value (:user/db dbs))))))
            "the examples the interrupted run had not written are moved now"))))))


(deftest an-example-not-written-stays-on-the-device
  (async-testing "user-db refuses the write: the device-db example stays for the next run"
    (with-learner
     [hund]
     (^:async fn
      [{:keys [dbs store]}]
      (await (seed-device! dbs [(second kept-on-device)]))
      (await (with-insert-all
              (fn [_real] (fn [& _] (js/Promise.reject (js/Error. "quota"))))
              (^:async fn []
               (try
                 (await (adapter/move-device-examples! (adapter dbs store)))
                 (is false "the move rejects")
                 (catch :default err
                   (is (= "quota" (ex-message err))))))))
      (is (= ["9F8E"] (map :_id (await (db-queries/fetch-examples (:device/db dbs))))))
      (is (= 1 (await (adapter/move-device-examples! (adapter dbs store)))) "the next run moves it")))))


(deftest a-device-with-many-examples-moves-them-all
  (async-testing "more examples than one page: every one reaches user-db, none stays behind"
    (with-learner
     [hund]
     (^:async fn
      [{:keys [dbs store]}]
      (await (db-seed/seed-examples! (:device/db dbs)
                                     (mapv #(assoc (second kept-on-device) :_id (str "ID" (+ 1000 %)) :value (str "Satz " %))
                                           (range 501))))
      (is (= 501 (await (adapter/move-device-examples! (adapter dbs store)))))
      (is (= 501 (count (:docs (await (db/find-all (:user/db dbs) {:selector {:type "example"}}))))))
      (is (empty? (await (db-queries/fetch-examples (:device/db dbs)))))))))


(deftest a-failed-move-is-run-again
  (async-testing "the move fails once; the port's move runs it again and resolves once it succeeds"
    (with-learner
     [hund]
     (^:async fn
      [{:keys [dbs learner]}]
      (await (seed-device! dbs [(second kept-on-device)]))
      (let [attempts (atom 0)]
        (await (with-insert-all
                (fn [real]
                  (fn [& args]
                    (if (= 1 (swap! attempts inc))
                      (js/Promise.reject (js/Error. "quota"))
                      (apply real args))))
                #((:learner/examples-moved learner))))
        (is (= 2 @attempts) "the second run succeeded")
        (is (identical? ((:learner/examples-moved learner)) ((:learner/examples-moved learner)))
            "one move per port, whoever asks")
        (is (empty? (await (db-queries/fetch-examples (:device/db dbs)))))
        (is (= ["neuer"] (map :value (await (db-queries/fetch-examples (:user/db dbs)))))))))))

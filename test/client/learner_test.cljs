(ns client.learner-test
  "The learner's data through its port (`adapters.learner`): each write reads
   the document it changes from PouchDB by id and writes PouchDB, and memory
   takes the write from the change feed (ADR-0016)."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.learner.loader :as loader]
   [adapters.learner.memory :as memory]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.learner :as learner]
   [client.support.time :as time]
   [client.support.wait :as wait]
   [cljs.test :refer-macros [deftest is testing use-fixtures]]
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
  "Seeds `docs` into their databases — an `:example` goes to device-db, any
   other to user-db — then calls `f` with the learner port over them, the
   store that keeps memory, and the function that stops following the
   feeds."
  [docs f]
  (await
   (with-dbs
    (^:async fn
     [dbs]
     (doseq [doc docs]
       (await (db/insert ((if (= "example" (:type doc)) :device/db :user/db) dbs) doc)))
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
        (is (= "der Pudel!" (:value (stored-word store "vocab:der hund")))))))))


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
      (is (= ["example-1"] (map :_id (await (db-queries/fetch-by-type (:device/db dbs) "example")))))
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
      (is (= #{"example-1" "example-2"} (set (map :_id (await (db-queries/fetch-by-type (:device/db dbs) "example"))))))
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


(deftest no-write-waits-for-another
  (async-testing "a write PouchDB never answers does not hold the next one"
    (with-learner
     [hund]
     (^:async fn
      [{:keys [dbs learner]}]
      (let [insert pouch/insert]
        ;; PouchDB never answers a write of der Hund.
        (set! pouch/insert
              (fn [dbs schema doc]
                (if (= "vocab:der hund" (:_id doc))
                  (js/Promise. (fn [_ _]))
                  (insert dbs schema doc))))
        (try
          (let [update! (:learner/update-word! learner)
                change  #(assoc % :value "der Hund!")
                ;; Function and change bound first: a call with a keyword
                ;; lookup or a literal in it compiles to an awaited
                ;; expression, and this write never resolves.
                _ (update! "vocab:der hund" change)
                added   (await ((:learner/add-word! learner)
                                {:id "vocab:die katze" :translation [{:lang "ru" :value "кошка"}] :value "die Katze"}))]
            (is (true? (:created? added)))
            (is (= "die Katze" (:value (await (db/get (:user/db dbs) "vocab:die katze"))))))
          (finally
           (set! pouch/insert insert))))))))


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


(deftest a-failed-read-at-start-says-so-and-is-tried-again
  (async-testing "the first read fails: the splash says so, and the load goes on once the read succeeds"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (await (db/insert (:user/db dbs) hund))
       (let [store   (atom {})
             seq-of  pouch/update-seq
             failed? (atom false)]
         (set! pouch/update-seq
               (fn [dbs db-key]
                 (if @failed?
                   (seq-of dbs db-key)
                   (do (reset! failed? true)
                       (js/Promise.reject (js/Error. "locked"))))))
         (try
           (let [stop (await (loader/start! dbs store (learner/store-dispatch store)))]
             (is (true? (:learner/read-failed? @store)))
             (is (true? (:learner/loaded? @store)))
             (is (some? (stored-word store "vocab:der hund")))
             (stop))
           (finally
            (set! pouch/update-seq seq-of)))))))))


(deftest a-write-does-not-wait-for-the-feed
  (async-testing "with the change feeds stopped, a write resolves and memory has it"
    (with-learner
     [hund]
     (^:async fn
      [{:keys [learner stop store]}]
      (stop)
      (await ((:learner/update-word! learner) "vocab:der hund" #(assoc % :value "der Hund!")))
      (is (= "der Hund!" (:value (stored-word store "vocab:der hund"))))))))


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
    (let [document (doto (js/EventTarget.) (aset "visibilityState" "visible"))
          follow   pouch/follow-changes]
      ;; A page with a document, and change feeds that report nothing; only
      ;; catching up reads.
      (set! (.-document js/globalThis) document)
      (set! pouch/follow-changes
            (fn [dbs db-key since f]
              (let [position (volatile! since)]
                {:catch-up! (fn ^:async catch-up! []
                              (let [{:keys [docs] read-to :position} (await (pouch/read-changes dbs db-key @position))]
                                (vreset! position read-to)
                                (when (seq docs) (f docs))))
                 :stop!     (fn [])})))
      (try
        (await
         (with-learner
          []
          (^:async fn
           [{:keys [dbs store]}]
           (await (db/insert (:user/db dbs) hund))
           (await (wait/settled))
           (is (nil? (stored-word store "vocab:der hund")) "the premise: nothing brought it")
           (.dispatchEvent document (js/Event. "visibilitychange"))
           (await (wait/until #(stored-word store "vocab:der hund")))
           (is (some? (stored-word store "vocab:der hund"))))))
        (finally
          (set! pouch/follow-changes follow)
          (js/Reflect.deleteProperty js/globalThis "document"))))))


(deftest stopping-stops-following
  (async-testing "after the stop function runs, a change no longer reaches memory"
    (with-learner
     []
     (^:async fn
      [{:keys [dbs stop store]}]
      (stop)
      (await (db/insert (:user/db dbs) hund))
      (await (wait/settled))
      (await (js/Promise. (fn [resolve] (js/setTimeout resolve 50))))
      (is (nil? (stored-word store "vocab:der hund")))))))

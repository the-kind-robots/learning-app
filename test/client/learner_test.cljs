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
   [client.support.document :as document]
   [client.support.learner :as learner]
   [client.support.time :as time]
   [client.support.wait :as wait]
   [cljs.test :refer-macros [deftest is testing use-fixtures]]
   [db :as db]
   [db.pouch :as pouch]
   [domain.examples :as examples]
   [use-cases.collections :as collections]
   [use-cases.vocabulary :as vocabulary]))


(def user-db-name (db-fixtures/db-name "client.learner-test.user"))


(def device-db-name (db-fixtures/db-name "client.learner-test.device"))


(use-fixtures :each (db-fixtures/db-fixture-multi [user-db-name device-db-name]))


(def ^:private clock
  {:clock/now-iso time/now-iso
   :clock/now-ms  time/now-ms})


(defn- capabilities
  "What the use cases are handed over `learner`, with `collection-id` the
   active collection, or none."
  [learner collection-id]
  {:world   {:world/clock clock}
   :learner (assoc learner
                   :learner/active-collection
                   #(some->> collection-id (memory/collection ((:learner/memory learner)))))})


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
      (await ((:learner/change! learner) :entity/word "vocab:der hund" #(assoc % :value "der Hund!")))
      (is (= "der Hund!" (:value (stored-word store "vocab:der hund"))))
      (is (= "2026-01-01T00:00:00.000Z" (:created-at (stored-word store "vocab:der hund")))
          "the stored creation stays")
      (testing "and a second write lands on the first"
        (await ((:learner/change! learner) :entity/word "vocab:der hund" #(assoc % :value "der Hund!!")))
        (is (= "der Hund!!" (:value (stored-word store "vocab:der hund")))))))))


(deftest a-write-is-made-to-the-stored-version
  (async-testing "a newer revision written past the learner is what the change is made to"
    (with-learner
     [hund]
     (^:async fn
      [{:keys [dbs learner store]}]
      (let [stored (await (db/get (:user/db dbs) "vocab:der hund"))]
        (await (db/insert (:user/db dbs) (assoc stored :value "der Pudel"))))
      (let [written (await ((:learner/change! learner) :entity/word "vocab:der hund" #(update % :value str "!")))]
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
                               (await ((:learner/change! learner) :entity/word "vocab:der hund" #(update % :value str "!")))
                               (is (= "der Pudel!" (:value (await (db/get (:user/db dbs) "vocab:der hund")))))))))))))


(def ^:private tiere
  {:_id "coll-tiere" :type "collection" :name "Tiere" :word-ids ["vocab:der hund"] :created-at "2026-01-01"})


(deftest a-collection-changes
  (async-testing "a word joins, leaves, and the name changes, each written to the stored collection"
    (with-learner
     [hund tiere]
     (^:async fn
      [{:keys [dbs learner store]}]
      (let [caps   (capabilities learner "coll-tiere")
            stored (fn ^:async f [] (await (db/get (:user/db dbs) "coll-tiere")))
            rev-1  (:_rev (await (stored)))]
        (await (vocabulary/add! caps "der Hund" "пёс" :word nil))
        (is (= rev-1 (:_rev (await (stored)))) "a word the collection lists is not written again")
        (await (vocabulary/add! caps "die Katze" "кошка" :word nil))
        (is (= ["vocab:der hund" "vocab:die katze"] (:word-ids (await (stored)))))
        (is (= ["coll-tiere"] (map :id ((:learner/collections-of learner) (:learner/memory @store) "vocab:die katze")))
            "memory answers which collections name a word")
        (await (vocabulary/remove-from-active! caps "vocab:der hund"))
        (is (= ["vocab:die katze"] (:word-ids (await (stored)))))
        (is (= [] ((:learner/collections-of learner) (:learner/memory @store) "vocab:der hund")))
        (await (collections/rename-active! caps "Haustiere"))
        (is (= {:id "coll-tiere" :created-at "2026-01-01" :name "Haustiere" :word-ids ["vocab:die katze"]}
               (get-in @store [:learner/memory :collections "coll-tiere"]))
            "memory has each change when the write resolves"))))))


(deftest a-rename-to-the-same-name-writes-nothing
  (async-testing "the stored revision stays, and a later add-word keeps the time of the rename"
    (with-learner
     [hund tiere]
     (^:async fn
      [{:keys [dbs learner]}]
      (let [caps   (capabilities learner "coll-tiere")
            stored (fn ^:async f [] (await (db/get (:user/db dbs) "coll-tiere")))
            rev-1  (:_rev (await (stored)))]
        (await (collections/rename-active! caps "Tiere"))
        (is (= rev-1 (:_rev (await (stored)))))
        (await (collections/rename-active! caps "Haustiere"))
        (let [renamed-at (:renamed-at (await (stored)))]
          (is (some? renamed-at))
          (await (vocabulary/add! caps "die Katze" "кошка" :word nil))
          (is (= renamed-at (:renamed-at (await (stored)))))))))))


(deftest a-rename-keeps-a-change-made-elsewhere
  (async-testing "a collection written past the learner keeps that change when the learner renames it at once"
    (with-learner
     [hund tiere]
     (^:async fn
      [{:keys [dbs learner]}]
      (let [stored (await (db/get (:user/db dbs) "coll-tiere"))]
        (await (db/insert (:user/db dbs) (update stored :word-ids conj "vocab:die katze"))))
      (await (collections/rename-active! (capabilities learner "coll-tiere") "Haustiere"))
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
      (is (true? (await (vocabulary/delete! (capabilities learner nil) "vocab:der hund"))))
      (is (= {"coll-haus" [] "coll-tiere" []}
             (into {} (map (juxt :_id :word-ids)) (await (db-queries/fetch-by-type (:user/db dbs) "collection"))))
          "no collection lists the word, the one memory had not seen included")
      (is (= ["vocab:die katze"] (map :_id (await (db-queries/fetch-by-type (:user/db dbs) "vocab")))))
      (is (= ["review-1"] (map :_id (await (db-queries/fetch-by-type (:user/db dbs) "review")))))
      (is (= ["example-1"] (map :_id (await (db-queries/fetch-by-type (:user/db dbs) "example")))))
      (is (nil? (stored-word store "vocab:der hund")))
      (is (= [] (get-in @store [:learner/memory :collections "coll-tiere" :word-ids])))
      (is (nil? (await (vocabulary/delete! (capabilities learner nil) "vocab:der hund")))
          "a word PouchDB does not have is nothing to delete")))))


(deftest a-word-added-again-has-its-old-history
  (async-testing "after a delete, the word added again has the reviews it had, no new one, and no second example"
    (with-learner
     [hund review example]
     (^:async fn
      [{:keys [dbs learner store]}]
      (await (vocabulary/delete! (capabilities learner nil) "vocab:der hund"))
      (let [capabilities {:learner (assoc learner :learner/active-collection (constantly nil))}
            {:keys [created?]} (await (vocabulary/add! capabilities "der Hund" "пёс" :word nil))
            memory       (:learner/memory @store)]
        (is (true? created?))
        (is (= ["review-1"] (map :_id (await (db-queries/fetch-by-type (:user/db dbs) "review"))))
            "no initial review is written")
        (is (= ["review-1"] (array-seq (:ids (:reviews (first (memory/collection-cards memory nil))))))
            "the card a lesson draws has the old review alone, so retention comes from it")
        (is (empty? (learner/missing-in memory))
            "the word has an example in «Всё подряд» already"))))))


(deftest a-collection-is-deleted-alone
  (async-testing
    "the collection goes; its words and examples stay; a collection made again does not show them, «Всё подряд» does"
    (with-learner
     [hund tiere example (assoc example :_id "example-2" :collection-id "coll-other")]
     (^:async fn
      [{:keys [dbs learner store]}]
      (await (collections/delete! (capabilities learner nil) "coll-tiere"))
      (is (empty? (await (db-queries/fetch-by-type (:user/db dbs) "collection"))))
      (is (nil? (get-in @store [:learner/memory :collections "coll-tiere"])))
      (is (= 1 (count (await (db-queries/fetch-by-type (:user/db dbs) "vocab")))))
      (is (= #{"example-1" "example-2"} (set (map :_id (await (db-queries/fetch-by-type (:user/db dbs) "example"))))))
      (let [{:keys [id]} (await (collections/create! (capabilities learner nil) "Tiere"))
            memory       (:learner/memory @store)]
        (is (empty? (examples/visible-in id (memory/examples-of memory ["vocab:der hund"])))
            "the made-again collection has a new id, and the old examples do not show in it")
        (is (= #{"example-1" "example-2"}
               (set (map :id (examples/visible-in nil (memory/examples-of memory ["vocab:der hund"])))))
            "«Всё подряд» shows them like any other example"))))))


(deftest a-pull-and-an-add-of-the-same-word-keep-both-translations
  (async-testing "the add is made to the pulled revision, whether or not memory has it yet"
    (with-learner
     [hund]
     (^:async fn
      [{:keys [dbs learner]}]
      (let [stored (await (db/get (:user/db dbs) "vocab:der hund"))]
        (await (db/insert (:user/db dbs) (update stored :translation conj {:lang "ru" :value "собака"}))))
      (let [added (await (vocabulary/add! (capabilities learner nil) "der Hund" "дворняга" :word nil))]
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
        (learner/with-learner dbs clock (fn [{:keys [learner]}] (vocabulary/delete! (capabilities learner nil) "vocab:der hund"))))
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
       (let [store (atom {})
             read  pouch/read-docs]
         ;; The deletion lands after user-db is read and before memory is
         ;; handed over.
         (set! pouch/read-docs
               (fn ^:async read-docs [dbs db-key range]
                 (let [docs (await (read dbs db-key range))]
                   (when (= :user/db db-key)
                     (await (db/remove (:user/db dbs) (await (db/get (:user/db dbs) "coll-tiere")))))
                   docs)))
         (try
           (let [stop (await (loader/start! dbs store))]
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
               (let [stop (await (loader/start! dbs store))]
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
          (is (false? (await (vocabulary/delete! (capabilities learner nil) "vocab:der hund"))))
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
;; A fetched example is saved (#523)
;;


(def ^:private bellt
  {:structure [] :translation "Пёс лает." :value "Der Hund bellt."})


(deftest an-example-is-saved
  (async-testing
    "under the id its content gives it; memory has it when the save resolves; one held already is kept"
    (with-learner
     [hund]
     (^:async fn
      [{:keys [dbs learner store]}]
      (let [held   (documents/example-doc "vocab:der hund" "der Hund" nil bellt)
            _ (await (db/insert (:user/db dbs) held))
            before (:_rev (await (db/get (:user/db dbs) (:_id held))))
            save!  (partial (:learner/insert! learner) :entity/example)]
        (is (true? (await (save! {:collection-id nil :example bellt :word "der Hund" :word-id "vocab:der hund"})))
            "user-db holds the one held already")
        (is (true? (await (save! {:collection-id "coll-tiere"
                                  :example       {:structure [] :translation "Пёс спит." :value "Der Hund schläft."}
                                  :word          "der Hund"
                                  :word-id       "vocab:der hund"})))
            "user-db holds the new one")
        (is (= before (:_rev (await (db/get (:user/db dbs) (:_id held))))) "the one held already is kept")
        (is (= #{"Der Hund bellt." "Der Hund schläft."}
               (set (map :value (memory/examples-of (:learner/memory @store) ["vocab:der hund"])))))
        (is (= #{nil "coll-tiere"}
               (set (map :collection-id (await (db-queries/fetch-examples (:user/db dbs))))))))))))


(deftest a-refused-example-is-not-held
  (async-testing "the write of the example fails: the save resolves false"
    (with-learner
     [hund]
     (^:async fn
      [{:keys [learner]}]
      (let [example {:collection-id nil :example bellt :word "der Hund" :word-id "vocab:der hund"}
            real    pouch/insert-all-if-absent]
        (set! pouch/insert-all-if-absent (fn [& _] (js/Promise.resolve #{})))
        (try
          (is (false? (await ((partial (:learner/insert! learner) :entity/example) example))))
          (finally
           (set! pouch/insert-all-if-absent real))))))))


(defn- fired
  "An atom that turns true once the learner's `changed-since` `memory`
   resolves. It is not an async function, so the promise is not awaited."
  [learner memory]
  (let [done (atom false)]
    (.then ((:learner/changed-since learner) memory nil) #(reset! done true))
    done))


(deftest changed-since-resolves-when-words-or-collections-differ
  (async-testing "at once when they already differ; on a word or a collection; not on a review; the watch goes"
    (with-learner
     []
     (^:async fn
      [{:keys [learner store]}]
      (let [watching #(count (.-watches ^js store))
            memory   (:learner/memory @store)
            already  (fired learner {:collections nil :words nil})
            by-word  (fired learner memory)]
        (await (wait/settled))
        (is @already "a snapshot that already differs")
        (is (not @by-word))
        (is (pos? (watching)) "watching until it fires")
        (await (vocabulary/add! (capabilities learner nil) "Igel" "ёж" :word nil))
        (await (wait/settled))
        (is @by-word "a word")
        (let [by-review (fired learner (:learner/memory @store))
              watched   (watching)]
          (await (vocabulary/add-review (capabilities learner nil) "vocab:igel" true "ёж"))
          (await (wait/settled))
          (is (not @by-review) "a review")
          (await (collections/create! (capabilities learner nil) "Tiere"))
          (await (wait/settled))
          (is @by-review "a collection")
          (is (< (watching) watched) "the watch is removed after firing")))))))


(deftest changed-since-leaves-no-watch-when-the-signal-has-aborted
  (async-testing "an already aborted signal never leaves a watch on the store"
    (with-learner
     []
     (^:async fn
      [{:keys [learner store]}]
      (let [controller (js/AbortController.)
            before     (count (.-watches ^js store))]
        (.abort controller)
        ((:learner/changed-since learner) (:learner/memory @store) (.-signal controller))
        (is (= before (count (.-watches ^js store))) "no watch remains"))))))


;;
;; The learner is initialized once memory is loaded and the first pass is done
;;


(defn- sleeping
  "A world whose clock sleeps until `wake!`, with the milliseconds it was asked
   to sleep and whether its signal aborted."
  []
  (let [wake    (atom nil)
        slept   (atom nil)
        aborted (atom false)]
    {:aborted? aborted
     :slept    slept
     :wake!    #(@wake nil)
     :world    {:world/clock {:clock/sleep (fn [ms ^js signal]
                                             (reset! slept ms)
                                             (js/Promise.
                                              (fn [resolve]
                                                (reset! wake resolve)
                                                (.addEventListener signal "abort" #(reset! aborted true)))))}}}))


(defn- ^:async settled
  "Whether `promise` has resolved by the time the event loop has turned."
  [promise]
  (let [done (atom false)]
    (.then promise #(reset! done true))
    (await (wait/settled))
    @done))


(deftest initialized-waits-for-memory-and-the-first-pass-and-drops-the-timer
  (async-testing "memory loaded, first pass pending: not yet; the pass completes: initialized, the minute's sleep cancelled"
    (let [store                   (atom {:learner/memory memory/empty-memory})
          {:keys [aborted? slept world]} (sleeping)
          [first-pass pass-done!] (let [resolve (atom nil)
                                        promise (js/Promise. #(reset! resolve %))]
                                    [promise @resolve])
          initialized             (adapter/initialize {:store store :world world} first-pass (js/AbortController.))]
      (is (false? (await (settled initialized))) "memory is not loaded")
      (swap! store assoc :learner/loaded? true)
      (is (false? (await (settled initialized))) "the first pass is pending")
      (is (= 60000 @slept) "a minute is the longest it waits")
      (pass-done! nil)
      (is (true? (await (settled initialized))))
      (is (true? @aborted?) "the sleep is cancelled when the pass won"))))


(deftest initialized-stops-waiting-for-a-first-pass-after-a-minute
  (async-testing "a first pass that never comes: initialized when the minute is up"
    (let [store                          (atom {:learner/memory memory/empty-memory :learner/loaded? true})
          {:keys [wake! world]}          (sleeping)
          initialized                    (adapter/initialize {:store store :world world} (js/Promise. (fn [_])) (js/AbortController.))]
      (is (false? (await (settled initialized))))
      (wake!)
      (is (true? (await (settled initialized)))))))

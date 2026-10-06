(ns client.examples-backfill-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.learner.documents :as documents]
   [adapters.learner.memory :as memory]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.db-seed :as db-seed]
   [client.support.learner :as learner]
   [client.support.replication :as replication]
   [client.support.time :as time]
   [client.support.wait :as wait]
   [cljs.test :refer-macros [deftest is testing use-fixtures]]
   [clojure.string :as str]
   [db :as db]
   [domain.vocabulary :as vocabulary]
   [ports.learner :as ports]
   [sync :as sync]
   [tasks :as tasks]
   [use-cases.examples :as sut]
   [use-cases.vocabulary :as vocabulary-use-case]
   [utils :as utils]))


(def device-db-name (db-fixtures/db-name "client.examples-backfill-test.device"))


(def user-db-name (db-fixtures/db-name "client.examples-backfill-test.user"))


(def other-device-db-name (db-fixtures/db-name "client.examples-backfill-test.other-device.user"))


(use-fixtures :each (db-fixtures/db-fixture-multi [device-db-name user-db-name other-device-db-name]))


;;
;; The rule, without a database
;;


(defn- missing
  "What `missing-examples` names, given the entries and what the device has
   for them; anything the test leaves out is empty."
  [data]
  (sut/missing-examples (merge {:collections [] :entries [] :examples []} data)))


(def ^:private hund
  {:id "vocab:hund" :translation [{:lang "ru" :value "собака"}] :value "Hund"})


(def ^:private katze
  {:id "vocab:katze" :translation [{:lang "ru" :value "кошка"}] :value "Katze"})


(deftest a-word-that-arrived-without-its-example-is-missing-one
  (testing "a word in no collection and with no example"
    (is (= [{:word hund}] (missing {:entries [hund]}))))
  (testing "and nothing is missing once any example covers it — the main card is a union"
    (is (empty? (missing {:entries  [hund]
                          :examples [{:word-id "vocab:hund" :collection-id "coll-1"}]})))))


(deftest a-collection-is-missing-an-example-of-its-own
  (let [collections [{:id "coll-1" :name "Tiere" :word-ids ["vocab:hund"]}]]
    (testing "a word in a collection whose example came from elsewhere"
      (is (= [{:collection-id "coll-1" :collection-name "Tiere" :word hund}]
             (missing {:collections collections
                       :entries     [hund]
                       :examples    [{:word-id "vocab:hund" :collection-id nil}]}))))
    (testing "and nothing once that collection has its own"
      (is (empty? (missing {:collections collections
                            :entries     [hund]
                            :examples    [{:word-id "vocab:hund" :collection-id "coll-1"}]}))))
    (testing "a collection naming a word this device does not have"
      (is (= [{:word hund}]
             (missing {:collections [{:id "coll-1" :name "Tiere" :word-ids ["vocab:gone"]}]
                       :entries     [hund]}))
          "nothing is missing for a word there is nothing to fetch for"))))


(deftest a-word-is-missing-one-example-per-card-it-appears-on
  (testing "two collections holding the same word each want their own"
    (is (= [{:collection-id "coll-1" :collection-name "Tiere" :word katze}
            {:collection-id "coll-2" :collection-name "Haus" :word katze}]
           (missing {:collections [{:id "coll-1" :name "Tiere" :word-ids ["vocab:katze"]}
                                   {:id "coll-2" :name "Haus" :word-ids ["vocab:katze"]}]
                     :entries     [katze]})))))


(deftest every-missing-pair-is-named-however-many-there-are
  (let [words (mapv (fn [n] {:id (str "vocab:" n) :translation [] :value (str "Wort" n)})
                    (range 500))]
    (is (= 500 (count (missing {:entries words})))
        "the queue paces the fetching; naming what is missing is not where to hold back")))


(deftest what-a-read-sees-depends-on-the-collection-it-is-scoped-to
  (let [untethered {:word-id "vocab:hund" :collection-id nil}
        themed     {:word-id "vocab:hund" :collection-id "coll-1"}
        examples   [untethered themed]]
    (is (= [themed] (sut/visible-in examples "coll-1"))
        "a theme sees what was generated in it, and nothing else")
    (is (= examples (sut/visible-in examples nil))
        "a read outside every theme sees all of them")
    (is (empty? (sut/visible-in [untethered] "coll-2"))
        "and a theme that generated none of them sees none")))


;;
;; The same rule against the databases the app uses
;;


(def ^:private clock
  {:clock/now-iso time/now-iso :clock/now-ms time/now-ms})


(defn- ^:async capabilities
  "What the use cases are handed: the learner port over the test databases,
   the main card active, and no account. It resolves once memory has what
   the test seeded."
  [dbs]
  (await (learner/caught-up dbs (::store dbs)))
  {:capabilities/sync sync/no-account
   :learner (assoc (::learner dbs) :learner/active-collection (fn [] nil))})


(defn- ^:async capabilities-adding-into
  "Capabilities for `use-cases.vocabulary/add!` with `collection-id` the open
   card — the port reads the stored choice from localStorage, which a node
   test has not.
   It resolves once memory has what the test seeded, as it does by the time
   a learner adds a word."
  [dbs collection-id]
  (assoc-in (await (capabilities dbs))
   [:learner :learner/active-collection]
   (fn [] (get-in ((:learner/memory (::learner dbs))) [:collections collection-id]))))


(defn- with-dbs
  "Calls `f` with the test databases, under which `::learner` is the
   learner port over them."
  [f]
  (db-fixtures/with-test-dbs
   [device-db-name user-db-name]
   (fn [[device-db user-db]]
     (let [dbs {:device/db device-db :user/db user-db}]
       (learner/with-learner dbs clock #(f (assoc dbs ::learner (:learner %) ::store (:store %))))))))


(defn ^:async queued-tasks
  "What the queued fetches carry, which is what each request is built from.
   Read without a page limit — `find` stops at 25, which a vocabulary's worth
   of fetches is well past."
  [dbs]
  (let [{docs :docs} (await (db/find-all (:device/db dbs) {:selector {:type "task"}}))]
    (mapv :data docs)))


(defn ^:async queued-fetches
  "The `[word-id collection-id]` pairs the device now has tasks for."
  [dbs]
  (into #{} (map (juxt :word-id :collection-id)) (await (queued-tasks dbs))))


(defn ^:async seed-collection!
  [dbs collection-id name word-ids]
  (await (db/insert (:user/db dbs)
                    {:_id        collection-id
                     :type       "collection"
                     :name       name
                     :word-ids   word-ids
                     :created-at time/test-now-iso})))


(deftest replicated-words-get-tasks-and-the-ones-with-examples-do-not
  (async-testing "a full pass queues exactly the words that have no example"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs)
                                        [{:value "Hund" :translation "собака"}
                                         {:value "Katze" :translation "кошка"}]))
       (await (db-seed/seed-examples! (:user/db dbs)
                                      [{:_id         "example-hund"
                                        :word-id     (vocabulary/vocab-id "Hund")
                                        :word        "Hund"
                                        :value       "Der Hund bellt."
                                        :translation "Собака лает."}]))
       (is (= 1 (await (sut/request-all-missing! (await (capabilities dbs))))))
       (is (= #{[(vocabulary/vocab-id "Katze") nil]}
              (await (queued-fetches dbs))))
       (testing "a second pass names the same pair and writes nothing new"
         (is (= 1 (await (sut/request-all-missing! (await (capabilities dbs))))))
         (is (= 1 (count (await (queued-tasks dbs))))
             "one pair is one task id, so the second write is refused")))))))


(deftest a-start-queues-every-missing-pair-of-a-whole-vocabulary
  (async-testing "no cap: a device catching up asks for as many as it is missing"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (let [words (mapv (fn [n] {:value (str "Wort" n) :translation (str "слово" n)})
                         (range 120))]
         (await (db-seed/seed-vocabulary! (:user/db dbs) words))
         (is (= 120 (await (sut/request-all-missing! (await (capabilities dbs))))))
         (is (= 120 (count (await (queued-fetches dbs))))
             "the old twenty per pass would have left a hundred behind")
         (testing "and a second start writes none of them again"
           (is (= 120 (await (sut/request-all-missing! (await (capabilities dbs))))))
           (is (= 120 (count (await (queued-tasks dbs))))))))))))


(deftest a-task-carries-what-the-fetch-needs
  (async-testing "the queued task names the word, its Russian glosses and the collection"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs) [{:value "Bank" :translation "скамейка"}]))
       (await (seed-collection! dbs "coll-park" "Park" [(vocabulary/vocab-id "Bank")]))
       (await (sut/request-all-missing! (await (capabilities dbs))))
       (let [{[task] :docs} (await (db/find (:device/db dbs) {:selector {:type "task"}}))]
         (is (= "example-fetch" (:task-type task)))
         (is (= {:collection-id "coll-park"
                 :collection-name "Park"
                 :translations  ["скамейка"]
                 :word          "Bank"
                 :word-id       (vocabulary/vocab-id "Bank")}
                (:data task)))))))))


(deftest an-empty-vocabulary-queues-nothing
  (async-testing "nothing to back-fill, and no query against an empty id list"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (is (zero? (await (sut/request-all-missing! (await (capabilities dbs))))))
       (is (empty? (await (queued-fetches dbs)))))))))


(deftest a-pass-that-throws-is-contained
  (async-testing "a broken repository is logged, not raised at the replication pass"
    (await
     ((^:async fn
       []
       (is (zero? (await (sut/request-all-missing!
                          {:learner {:learner/loaded (fn [] (js/Promise.resolve nil))
                                     :learner/memory (fn [] (throw (js/Error. "no memory")))}})))))))))


(deftest a-pass-queues-for-what-it-brought-and-leaves-the-rest-of-the-vocabulary-alone
  (async-testing "only the pulled word is considered, though another word is missing one too"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs)
                                        [{:value "Hund" :translation "собака"}
                                         {:value "Katze" :translation "кошка"}]))
       (is (= 1 (await (sut/request-missing-for! (await (capabilities dbs)) [(vocabulary/vocab-id "Katze")]))))
       (is (= #{[(vocabulary/vocab-id "Katze") nil]}
              (await (queued-fetches dbs)))
           "Hund is missing one as well, and this pass did not bring it"))))))


(deftest a-theme-that-arrived-is-unfolded-into-the-entries-it-names
  (async-testing "an entry themed on another device gets that theme's example now"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs)
                                        [{:value "Hund" :translation "собака"}
                                         {:value "Katze" :translation "кошка"}]))
       (await (seed-collection! dbs "coll-tiere" "Tiere" [(vocabulary/vocab-id "Hund")]))
       (is (= 1 (await (sut/request-missing-for! (await (capabilities dbs)) ["coll-tiere"]))))
       (is (= #{[(vocabulary/vocab-id "Hund") "coll-tiere"]}
              (await (queued-fetches dbs)))
           "Katze is missing one too, and no document of this pass names it"))))))


;;
;; A phrase is a vocabulary entry, and asks for an example like a word (#371)
;;


(def ^:private phrase-id (vocabulary/vocab-id "auf jeden Fall"))


(defn- ^:async seed-phrase!
  [dbs]
  (await (db-seed/seed-vocabulary! (:user/db dbs)
                                   [{:kind        "phrase"
                                     :translation "во всяком случае"
                                     :value       "auf jeden Fall"}])))


(deftest a-phrase-is-missing-its-example-like-a-word
  (testing "the rule reads the entry, and a phrase is an entry with a kind"
    (is (= [{:word {:id          phrase-id
                    :kind        "phrase"
                    :translation [{:lang "ru" :value "во всяком случае"}]
                    :value       "auf jeden Fall"}}]
           (missing {:entries [{:id          phrase-id
                                :kind        "phrase"
                                :translation [{:lang "ru" :value "во всяком случае"}]
                                :value       "auf jeden Fall"}]})))))


(deftest a-phrase-that-arrived-by-replication-is-asked-for-with-its-glosses
  (async-testing "the pass queues the phrase's fetch, carrying the Russian it was entered with"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (await (seed-phrase! dbs))
       (is (= 1 (await (sut/request-missing-for! (await (capabilities dbs)) [phrase-id]))))
       (is (= [{:collection-id nil
                :collection-name nil
                :translations  ["во всяком случае"]
                :word          "auf jeden Fall"
                :word-id       phrase-id}]
              (await (queued-tasks dbs)))
           "empty glosses would leave the generated sentence to guess the sense"))))))


(deftest a-phrase-that-has-its-example-is-asked-for-nothing
  (async-testing "an example already here answers the phrase's main card"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (await (seed-phrase! dbs))
       (await (db-seed/seed-examples! (:user/db dbs)
                                      [{:_id         "example-auf-jeden-fall"
                                        :word-id     phrase-id
                                        :word        "auf jeden Fall"
                                        :value       "Ich komme auf jeden Fall mit."
                                        :translation "Я обязательно пойду вместе."}]))
       (is (zero? (await (sut/request-missing-for! (await (capabilities dbs)) [phrase-id]))))
       (is (empty? (await (queued-fetches dbs)))))))))


(deftest a-phrase-in-a-theme-wants-that-theme-s-own-example
  (async-testing "the strict rule holds for a phrase: the main card's example does not answer a theme"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (await (seed-phrase! dbs))
       (await (db-seed/seed-examples! (:user/db dbs)
                                      [{:_id         "example-auf-jeden-fall"
                                        :word-id     phrase-id
                                        :word        "auf jeden Fall"
                                        :value       "Ich komme auf jeden Fall mit."
                                        :translation "Я обязательно пойду вместе."}]))
       (await (seed-collection! dbs "coll-reise" "Поездка" [phrase-id]))
       (is (= 1 (await (sut/request-missing-for! (await (capabilities dbs)) [phrase-id]))))
       (is (= [{:collection-id "coll-reise"
                :collection-name "Поездка"
                :translations  ["во всяком случае"]
                :word          "auf jeden Fall"
                :word-id       phrase-id}]
              (await (queued-tasks dbs))))
       (testing "and nothing once that theme has its own"
         (await (db-seed/seed-examples! (:user/db dbs)
                                        [{:_id           "example-auf-jeden-fall-reise"
                                          :collection-id "coll-reise"
                                          :word-id       phrase-id
                                          :word          "auf jeden Fall"
                                          :value         "Wir fahren auf jeden Fall nach Berlin."
                                          :translation   "Мы обязательно поедем в Берлин."}]))
         (is (zero? (await (sut/request-missing-for! (await (capabilities dbs)) [phrase-id])))
             "the pair is answered, and the queued task is the only one there is")))))))


(deftest one-pair-is-one-task-however-many-ask-for-it
  (async-testing "a pass queued the fetch; asking again for the same word in the same theme writes nothing"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs) [{:value "Hund" :translation "собака"}]))
       (await (seed-collection! dbs "coll-tiere" "Tiere" [(vocabulary/vocab-id "Hund")]))
       (is (= 1
              (await (sut/request-missing-for! (await (capabilities dbs))
                                               [(vocabulary/vocab-id "Hund")]))))
       (await (sut/request-example-if-missing! (await (capabilities dbs))
                                               {:id (vocabulary/vocab-id "Hund") :value "Hund"}
                                               {:id "coll-tiere" :name "Tiere"}))
       ;; Counted as documents, not as pairs: two tasks for one pair are two
       ;; generations and two example documents, and a set of pairs hides that.
       (is (= 1 (count (await (queued-tasks dbs))))
           "one pair is one task id"))))))


(deftest adding-a-word-to-a-theme-asks-the-same-question
  (async-testing "the duplicate path and a pass agree on what is missing"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs) [{:value "Hund" :translation "собака"}]))
       (await (db-seed/seed-examples! (:user/db dbs)
                                      [{:_id         "example-hund"
                                        :word-id     (vocabulary/vocab-id "Hund")
                                        :word        "Hund"
                                        :value       "Der Hund bellt."
                                        :translation "Собака лает."}]))
       (await (seed-collection! dbs "coll-tiere" "Tiere" []))
       (is (= {:created? false :word-id (vocabulary/vocab-id "Hund")}
              (await (vocabulary-use-case/add! (await (capabilities-adding-into dbs "coll-tiere"))
                                               "Hund"
                                               "пёс"
                                               :word)))
           "a word already stored is not new")
       (await (wait/until #(.then (queued-fetches dbs) seq)))
       (is (= #{[(vocabulary/vocab-id "Hund") "coll-tiere"]}
              (await (queued-fetches dbs)))
           "the main card's example does not answer a named collection")
       (testing "and the same word in a theme that already has its example asks for nothing"
         (await (db-seed/seed-examples! (:user/db dbs)
                                        [{:_id           "example-hund-tiere"
                                          :collection-id "coll-tiere"
                                          :word-id       (vocabulary/vocab-id "Hund")
                                          :word          "Hund"
                                          :value         "Der Hund schläft."
                                          :translation   "Собака спит."}]))
         (let [requested (atom [])
               recording (assoc-in (await (capabilities dbs))
                          [:learner :learner/request-examples!]
                          (fn [requests]
                            (swap! requested into requests)
                            (js/Promise.resolve nil)))]
           (await (sut/request-example-if-missing! recording
                                                   {:id (vocabulary/vocab-id "Hund") :value "Hund"}
                                                   {:id "coll-tiere" :name "Tiere"}))
           (is (empty? @requested) "no fetch is asked for a pair that is answered"))))))))


;;
;; What a pass costs, counted in the calls it makes
;;


(def ^:private two-words
  [{:_id "vocab:hund" :_rev "1-a" :type "vocab" :value "Hund"}
   {:_id "vocab:katze" :_rev "1-a" :type "vocab" :value "Katze"}])


(def ^:private an-account
  "The sync capability of a device with an account, as far as the backfill
   reads it."
  {:sync/account-id       "account"
   :sync/push-interval-ms 3000})


(defn- recording-capabilities
  "A device with an account whose memory holds two words and no example,
   and whose move, holding of the fetches, catch-up, cancelling and
   requests record each time they are asked. A request records what it was
   asked to queue and the delay it was asked to queue it with."
  [asked]
  (let [recorded (fn [step] (fn [& _] (swap! asked conj step) (js/Promise.resolve nil)))]
    {:capabilities/sync an-account
     :learner           (assoc ports/reads
                               :learner/cancel-answered-fetches! (fn [ids]
                                                                   (swap! asked conj [:cancel ids])
                                                                   (js/Promise.resolve 0))
                               :learner/catch-up!                (recorded :catch-up)
                               :learner/examples-moved           (recorded :moved)
                               :learner/hold-fetches-until!      (recorded :held)
                               :learner/memory                   (fn [] (memory/with-docs memory/empty-memory two-words))
                               :learner/request-examples!        (fn [requests]
                                                                   (swap! asked
                                                                     conj
                                                                     [(mapv (comp :id :word) requests)
                                                                      (:delay-ms (first requests))])
                                                                   (js/Promise.resolve nil)))}))


(deftest a-start-reads-the-whole-vocabulary-and-a-push-only-pass-reads-nothing
  (async-testing "the full read belongs to the start, after the first pass; a pass reads what it brought"
    (await
     ((^:async fn
       []
       (let [asked       (atom [])
             after-pass! (sut/start! (recording-capabilities asked))]
         (await (wait/settled))
         (is (= [:moved :held] @asked) "the fetches are held, and nothing counts before the first pass")
         (is (nil? (after-pass! {:pulled 0 :pushed 3 :pulled-ids []}))
             "the hook answers at once, whatever it leaves running")
         (await (wait/until #(= 3 (count @asked))))
         (is (= [["vocab:hund" "vocab:katze"] nil] (peek @asked))
             "the first pass, though it pulled nothing, lets the start ask over the whole vocabulary, due now")
         (after-pass! {:pulled 1 :pushed 0 :pulled-ids ["vocab:hund"]})
         (await (wait/until #(= 5 (count @asked))))
         (is (= [:catch-up [["vocab:hund"] 6000]] (subvec @asked 3))
             "a pass that pulled catches memory up, asks only about what it brought, and its fetches wait two push windows")
         (after-pass! {:pulled 1 :pushed 0 :pulled-examples #{"example:vocab:hund::0123456789ab"} :pulled-ids []})
         (await (wait/until #(= 6 (count @asked))))
         (is (= [:cancel #{"example:vocab:hund::0123456789ab"}] (peek @asked))
             "a pass that brought examples cancels the fetches they answer")))))))


(deftest a-pass-waits-until-the-backfill-is-ready
  (async-testing "nothing counts before the move is done, and the first pass is what makes a device with an account ready"
    (await
     ((^:async fn
       []
       (let [asked        (atom [])
             release-move (atom nil)
             capabilities (assoc-in (recording-capabilities asked)
                           [:learner :learner/examples-moved]
                           (fn []
                             (swap! asked conj :moved)
                             (js/Promise. (fn [resolve] (reset! release-move resolve)))))
             after-pass!  (sut/start! capabilities)]
         (after-pass! {:pulled 1 :pushed 0 :pulled-ids ["vocab:hund"]})
         (await (wait/settled))
         (is (= [:moved :held] @asked) "a pass has completed, and nothing counts while the move runs")
         (@release-move nil)
         (await (wait/until #(= 2 (count (filter vector? @asked)))))
         (is (= #{["vocab:hund" "vocab:katze"] ["vocab:hund"]} (set (map first (filter vector? @asked))))
             "both count once the move is done")))))))


(deftest a-first-pass-before-the-backfill-listens-makes-it-ready
  (async-testing "sync tells a late listener with {} that a pass has happened; no second pass is waited for"
    (await
     ((^:async fn
       []
       (let [asked    (atom [])
             listener (sut/start! (recording-capabilities asked))]
         (listener {})
         (await (wait/until #(= 3 (count @asked))))
         (is (= [["vocab:hund" "vocab:katze"] nil] (peek @asked)) "the start counts")))))))


(deftest a-device-without-an-account-is-ready-once-the-move-is-done
  (async-testing "no pass will come, so none is waited for"
    (await
     ((^:async fn
       []
       (let [asked (atom [])]
         (sut/start! (assoc (recording-capabilities asked) :capabilities/sync sync/no-account))
         (await (wait/until #(= 3 (count @asked))))
         (is (= [["vocab:hund" "vocab:katze"] nil] (peek @asked)))))))))


(deftest an-example-one-pass-behind-its-word-is-not-fetched
  (async-testing
    "a pass brings a word alone and its fetch waits; the pass that brings the example cancels it"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs) [{:value "Hund" :translation "собака"}]))
       (let [capabilities   (assoc (await (capabilities dbs)) :capabilities/sync an-account)
             hund-id        (vocabulary/vocab-id "Hund")
             requested      (atom [])
             original-fetch js/fetch]
         (is (= 1 (await (sut/request-missing-for! capabilities [hund-id]))))
         (let [{[task] :docs} (await (db/find (:device/db dbs) {:selector {:type "task"}}))
               example        (documents/example-doc hund-id "Hund" nil {:translation "Собака лает." :value "Der Hund bellt."})]
           (is (= (utils/ms->iso (+ (time/now-ms) 6000)) (:run-at task))
               "the fetch is due two push windows after the pass")
           (await (db/insert (:user/db dbs) example))
           (is (= 1 (await ((get-in capabilities [:learner :learner/cancel-answered-fetches!]) [(:_id example)]))))
           (is (empty? (await (queued-fetches dbs))) "the pass that brought the example cancelled the fetch")
           (set! js/fetch (fn [url] (swap! requested conj url) (js/Promise.reject (js/Error. "no network"))))
           (try
             (is (true? (await (tasks/execute-task task {:clock clock :dbs dbs}))))
             (finally
              (set! js/fetch original-fetch)))
           (is (empty? @requested) "had the fetch run anyway, it would have found its pair answered"))))))))


(deftest a-fetch-asks-the-backfill-s-question
  (testing "the ids a fetch looks for are the examples `visible-in` sees, in a theme and outside every theme"
    ;; Collection ids as the app makes them, with a colon of their own, one
    ;; of them the start of another.
    (let [examples [{:collection-id "collection:1-87155332" :word-id "vocab:hund"}
                    {:collection-id "collection:1-871553329" :word-id "vocab:hund"}
                    {:collection-id nil :word-id "vocab:hund"}
                    {:collection-id nil :word-id "vocab:hund x"}
                    {:collection-id "collection:1-87155332" :word-id "vocab:hund x"}]
          id-of    (fn [{:keys [collection-id word-id] :as example}]
                     (:_id (documents/example-doc word-id "w" collection-id (assoc example :translation "t" :value (str word-id collection-id)))))]
      (doseq [collection-id [nil "collection:1-87155332" "collection:1-871553329" "collection:3-1"]]
        (let [prefix (documents/example-id-prefix "vocab:hund" collection-id)]
          (is (= (set (map id-of (sut/visible-in (filter #(= "vocab:hund" (:word-id %)) examples) collection-id)))
                 (set (filter #(str/starts-with? % prefix) (map id-of examples))))
              (str "collection " (pr-str collection-id))))))))


(deftest a-theme-a-pass-deleted-asks-for-nothing
  (async-testing "memory has not taken the deletion when the pass is announced; the catch-up takes it"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs) [{:value "Hund" :translation "собака"}]))
       (await (seed-collection! dbs "coll-tiere" "Tiere" [(vocabulary/vocab-id "Hund")]))
       (let [capabilities (await (capabilities dbs))]
         (await (db/remove (:user/db dbs) (await (db/get (:user/db dbs) "coll-tiere"))))
         (is (zero? (await (sut/request-missing-for! capabilities ["coll-tiere"]))))
         (is (empty? (await (queued-fetches dbs))))))))))


;;
;; Examples replicate with the words they belong to (#528)
;;


(deftest an-example-a-pass-brings-answers-its-pair
  (async-testing "a word and its example arrive in one pass: nothing is queued, though memory had neither"
    (await
     (db-fixtures/with-test-db
       other-device-db-name
       (^:async fn
        [other-device]
        (await
         (with-dbs
          (^:async fn
           [dbs]
           ;; Another device of the account added two words and fetched one
           ;; example.
           (await (db-seed/seed-vocabulary! other-device
                                            [{:value "Hund" :translation "собака"}
                                             {:value "Katze" :translation "кошка"}]))
           (await (db-seed/seed-examples! other-device
                                          [{:_id         (str "example:" (vocabulary/vocab-id "Hund") "::0123456789ab")
                                            :word-id     (vocabulary/vocab-id "Hund")
                                            :word        "Hund"
                                            :value       "Der Hund bellt."
                                            :translation "Собака лает."}]))
           (is (= 5 (await (replication/replicated! other-device (:user/db dbs)))))
           ;; Announced straight after the pass, before memory took anything
           ;; of it: the backfill catches memory up itself.
           (is (= 1
                  (await (sut/request-missing-for!
                          {:capabilities/sync sync/no-account
                           :learner (assoc (::learner dbs) :learner/active-collection (fn [] nil))}
                          [(vocabulary/vocab-id "Hund") (vocabulary/vocab-id "Katze")]))))
           (is (= #{[(vocabulary/vocab-id "Katze") nil]} (await (queued-fetches dbs)))
               "only the word that came without an example is queued")))))))))


(deftest a-start-counts-after-the-device-s-examples-moved
  (async-testing "an example an earlier build kept in device-db answers its pair at start"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs)
                                        [{:value "Hund" :translation "собака"}
                                         {:value "Katze" :translation "кошка"}]))
       (await (db-seed/seed-examples! (:device/db dbs)
                                      [{:_id         "3F2B9C1A"
                                        :word-id     (vocabulary/vocab-id "Hund")
                                        :word        "Hund"
                                        :value       "Der Hund bellt."
                                        :translation "Собака лает."}]))
       (let [requested    (atom nil)
             capabilities (assoc-in (await (capabilities dbs))
                           [:learner :learner/request-examples!]
                           (fn [requests]
                             (reset! requested (mapv (comp :id :word) requests))
                             (js/Promise.resolve nil)))]
         (sut/start! capabilities)
         (await (wait/until #(some? @requested)))
         (is (= [(vocabulary/vocab-id "Katze")] @requested)
             "the moved example answers Hund; only Katze is asked for")
         (is (empty? (await (db-queries/fetch-examples (:device/db dbs))))
             "device-db holds no example")))))))

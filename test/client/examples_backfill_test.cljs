(ns client.examples-backfill-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-seed :as db-seed]
   [client.support.time :as time]
   [cljs.test :refer-macros [deftest is testing use-fixtures]]
   [db :as db]
   [domain.vocabulary :as vocabulary]
   [ports.collections :as collections-port]
   [ports.examples :as examples-port]
   [ports.reviews :as reviews-port]
   [ports.words :as words-port]
   [use-cases.examples :as sut]
   [use-cases.vocabulary :as vocabulary-use-case]))


(def device-db-name (db-fixtures/db-name "client.examples-backfill-test.device"))


(def user-db-name (db-fixtures/db-name "client.examples-backfill-test.user"))


(use-fixtures :each (db-fixtures/db-fixture-multi [device-db-name user-db-name]))


;;
;; The rule, without a database
;;


(defn- missing
  "What `missing-examples` names, given the entries and what the device holds
   for them; anything the test leaves out is empty."
  [held]
  (sut/missing-examples (merge {:collections [] :entries [] :examples []} held)))


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
    (testing "a collection naming a word this device does not hold"
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


(defn- deps
  [dbs]
  {:clock {:clock/now-iso time/now-iso :clock/now-ms time/now-ms}
   :db    dbs})


(defn- capabilities
  [dbs]
  {:collections (collections-port/start! (deps dbs))
   :examples    (examples-port/start! (deps dbs))
   :words       (words-port/start! (deps dbs))})


(defn- capabilities-adding-into
  "Capabilities for `use-cases.vocabulary/add!` with `collection-id` the open
   card — the port reads that from localStorage, which a node test has not."
  [dbs collection-id]
  (-> (capabilities dbs)
      (assoc :reviews (reviews-port/start! (deps dbs)))
      (assoc-in [:collections :collections/active-id] (fn [] collection-id))))


(defn- with-dbs
  [f]
  (db-fixtures/with-test-dbs
   [device-db-name user-db-name]
   (fn [[device-db user-db]]
     (f {:device/db device-db :user/db user-db}))))


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
       (await (db-seed/seed-examples! (:device/db dbs)
                                      [{:_id         "example-hund"
                                        :word-id     (vocabulary/vocab-id "Hund")
                                        :word        "Hund"
                                        :value       "Der Hund bellt."
                                        :translation "Собака лает."}]))
       (is (= 1 (await (sut/request-all-missing! (capabilities dbs)))))
       (is (= #{[(vocabulary/vocab-id "Katze") nil]}
              (await (queued-fetches dbs))))
       (testing "a second pass names the same pair and writes nothing new"
         (is (= 1 (await (sut/request-all-missing! (capabilities dbs)))))
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
         (is (= 120 (await (sut/request-all-missing! (capabilities dbs)))))
         (is (= 120 (count (await (queued-fetches dbs))))
             "the old twenty per pass would have left a hundred behind")
         (testing "and a second start writes none of them again"
           (is (= 120 (await (sut/request-all-missing! (capabilities dbs)))))
           (is (= 120 (count (await (queued-tasks dbs))))))))))))


(deftest a-task-carries-what-the-fetch-needs
  (async-testing "the queued task names the word, its Russian glosses and the collection"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs) [{:value "Bank" :translation "скамейка"}]))
       (await (seed-collection! dbs "coll-park" "Park" [(vocabulary/vocab-id "Bank")]))
       (await (sut/request-all-missing! (capabilities dbs)))
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
       (is (zero? (await (sut/request-all-missing! (capabilities dbs)))))
       (is (empty? (await (queued-fetches dbs)))))))))


(deftest a-pass-that-throws-is-contained
  (async-testing "a broken repository is logged, not raised at the replication pass"
    (await
     ((^:async fn
       []
       (is (zero? (await (sut/request-all-missing!
                          {:collections {:collections/list (fn [] (throw (js/Error. "no view")))}})))))))))


(deftest a-pass-queues-for-what-it-brought-and-leaves-the-rest-of-the-vocabulary-alone
  (async-testing "only the pulled word is considered, though another word is missing one too"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs)
                                        [{:value "Hund" :translation "собака"}
                                         {:value "Katze" :translation "кошка"}]))
       (is (= 1 (await (sut/request-missing-for! (capabilities dbs) [(vocabulary/vocab-id "Katze")]))))
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
       (is (= 1 (await (sut/request-missing-for! (capabilities dbs) ["coll-tiere"]))))
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
       (is (= 1 (await (sut/request-missing-for! (capabilities dbs) [phrase-id]))))
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
       (await (db-seed/seed-examples! (:device/db dbs)
                                      [{:_id         "example-auf-jeden-fall"
                                        :word-id     phrase-id
                                        :word        "auf jeden Fall"
                                        :value       "Ich komme auf jeden Fall mit."
                                        :translation "Я обязательно пойду вместе."}]))
       (is (zero? (await (sut/request-missing-for! (capabilities dbs) [phrase-id]))))
       (is (empty? (await (queued-fetches dbs)))))))))


(deftest a-phrase-in-a-theme-wants-that-theme-s-own-example
  (async-testing "the strict rule holds for a phrase: the main card's example does not answer a theme"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (await (seed-phrase! dbs))
       (await (db-seed/seed-examples! (:device/db dbs)
                                      [{:_id         "example-auf-jeden-fall"
                                        :word-id     phrase-id
                                        :word        "auf jeden Fall"
                                        :value       "Ich komme auf jeden Fall mit."
                                        :translation "Я обязательно пойду вместе."}]))
       (await (seed-collection! dbs "coll-reise" "Поездка" [phrase-id]))
       (is (= 1 (await (sut/request-missing-for! (capabilities dbs) [phrase-id]))))
       (is (= [{:collection-id "coll-reise"
                :collection-name "Поездка"
                :translations  ["во всяком случае"]
                :word          "auf jeden Fall"
                :word-id       phrase-id}]
              (await (queued-tasks dbs))))
       (testing "and nothing once that theme has its own"
         (await (db-seed/seed-examples! (:device/db dbs)
                                        [{:_id           "example-auf-jeden-fall-reise"
                                          :collection-id "coll-reise"
                                          :word-id       phrase-id
                                          :word          "auf jeden Fall"
                                          :value         "Wir fahren auf jeden Fall nach Berlin."
                                          :translation   "Мы обязательно поедем в Берлин."}]))
         (is (zero? (await (sut/request-missing-for! (capabilities dbs) [phrase-id])))
             "the pair is answered, and the queued task is the only one there is")))))))


(deftest one-pair-is-one-task-however-many-ask-for-it
  (async-testing "a pass queued the fetch; adding the same word to the same theme writes nothing"
    (await
     (with-dbs
      (^:async fn
       [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs) [{:value "Hund" :translation "собака"}]))
       (await (seed-collection! dbs "coll-tiere" "Tiere" [(vocabulary/vocab-id "Hund")]))
       (is (= 1
              (await (sut/request-missing-for! (capabilities dbs)
                                               [(vocabulary/vocab-id "Hund")]))))
       (await (vocabulary-use-case/add! (capabilities-adding-into dbs "coll-tiere")
                                        "Hund"
                                        "пёс"
                                        :word))
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
       (await (db-seed/seed-examples! (:device/db dbs)
                                      [{:_id         "example-hund"
                                        :word-id     (vocabulary/vocab-id "Hund")
                                        :word        "Hund"
                                        :value       "Der Hund bellt."
                                        :translation "Собака лает."}]))
       (await (seed-collection! dbs "coll-tiere" "Tiere" []))
       (await (vocabulary-use-case/add! (capabilities-adding-into dbs "coll-tiere")
                                        "Hund"
                                        "пёс"
                                        :word))
       (is (= #{[(vocabulary/vocab-id "Hund") "coll-tiere"]}
              (await (queued-fetches dbs)))
           "the main card's example does not answer a named collection")
       (testing "and the same word added to a theme that already has its example asks for nothing"
         (await (db-seed/seed-examples! (:device/db dbs)
                                        [{:_id           "example-hund-tiere"
                                          :collection-id "coll-tiere"
                                          :word-id       (vocabulary/vocab-id "Hund")
                                          :word          "Hund"
                                          :value         "Der Hund schläft."
                                          :translation   "Собака спит."}]))
         (await (vocabulary-use-case/add! (capabilities-adding-into dbs "coll-tiere")
                                          "Hund"
                                          "пёс"
                                          :word))
         (is (= 1 (count (await (queued-fetches dbs))))
             "no second task for a pair that is answered")))))))


;;
;; What a pass costs, counted in the calls it makes
;;


(defn- recording-capabilities
  "Ports that answer with nothing and record what they were asked."
  [asked]
  {:collections {:collections/list (fn []
                                     (swap! asked conj :collections)
                                     (js/Promise.resolve []))}
   :examples    {:examples/list     (fn [_] (js/Promise.resolve []))
                 :examples/request! (fn [_] (js/Promise.resolve nil))}
   :words       {:words/previews (fn [word-ids]
                                   (swap! asked conj [:previews word-ids])
                                   (js/Promise.resolve []))}})


(defn- settled
  "A promise resolving after the microtasks queued so far have run."
  []
  (js/Promise. (fn [resolve _] (js/setTimeout resolve 0))))


(deftest a-start-reads-the-whole-vocabulary-and-a-push-only-pass-reads-nothing
  (async-testing "the full pass belongs to the start, not to every replication pass"
    (await
     ((^:async fn
       []
       (let [asked       (atom [])
             after-pass! (sut/start! (recording-capabilities asked))]
         (await (settled))
         (is (= [:collections [:previews nil]] @asked)
             "starting asks over the whole vocabulary")
         (is (nil? (after-pass! {:pulled 0 :pushed 3 :pulled-ids []}))
             "the hook answers at once, whatever it leaves running")
         (await (settled))
         (is (= [:collections [:previews nil]] @asked)
             "a pass that pulled nothing reads nothing")
         (after-pass! {:pulled 1 :pushed 0 :pulled-ids ["vocab:hund"]})
         (await (settled))
         (is (= [:collections [:previews nil] :collections [:previews ["vocab:hund"]]] @asked)
             "a pass that pulled asks only about what it brought")))))))

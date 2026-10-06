(ns client.examples-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.example-fetch :as sut]
   [adapters.learner.documents :as documents]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.fetch-mocks :as fetch-mocks]
   [client.support.replication :as replication]
   [client.support.time :as time]
   [client.support.wait :as wait]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [db.pouch :as dbs]
   [tasks :as tasks]
   [utils :as utils]))


(def test-device-db-name (db-fixtures/db-name "client.examples-test"))


(def test-user-db-name (db-fixtures/db-name "client.examples-test-user"))


(use-fixtures :each (db-fixtures/db-fixture-multi [test-device-db-name test-user-db-name]))


(defn- test-clock
  []
  {:clock/now-iso time/now-iso
   :clock/now-ms  time/now-ms})


(defn- task-env
  "What the queue hands a fetch: the databases and the clock."
  [dbs]
  {:clock (test-clock)
   :dbs   dbs})


(defn- with-test-dbs
  [f]
  (db-fixtures/with-test-dbs
   [test-device-db-name test-user-db-name]
   (fn [[device-db user-db]]
     (f {:device/db device-db :user/db user-db}))))


(deftest fetch-one-returns-parsed-json-on-success
  (async-testing "`fetch-one` returns parsed JSON on success"
    (let [example        {:value "Ich habe einen Hund" :translation "I have a dog"}
          original-fetch js/fetch]
      (set! js/fetch (fetch-mocks/mock-fetch-success example))
      (try
        (let [result (await (sut/fetch-one "Hund" [] nil))]
          (is (= "Ich habe einen Hund" (:value result)))
          (is (= "I have a dog" (:translation result))))
        (finally
         (set! js/fetch original-fetch))))))


(deftest fetch-one-rejects-on-server-error
  (async-testing "`fetch-one` rejects on server error"
    (let [original-fetch js/fetch]
      (set! js/fetch
            (fetch-mocks/mock-fetch-error-with-body
             500
             {:error "Examples are temporarily unavailable"}))
      (try
        (try
          (await (sut/fetch-one "Hund" [] nil))
          (is false "Should have rejected")
          (catch :default error
            (is (= "Examples are temporarily unavailable" (ex-message error)))
            (is (= 500 (:status (ex-data error))))))
        (finally
         (set! js/fetch original-fetch))))))


(deftest fetch-one-includes-retry-after-ms-on-server-error
  (async-testing "`fetch-one` includes retry-after-ms when the server suggests a retry delay"
    (let [original-fetch js/fetch]
      (set! js/fetch
            (fetch-mocks/mock-fetch-error-with-body
             429
             {:error "Examples are temporarily unavailable"}
             {"Retry-After" "2"}))
      (try
        (try
          (await (sut/fetch-one "Hund" [] nil))
          (is false "Should have rejected")
          (catch :default error
            (is (= 429 (:status (ex-data error))))
            (is (= 2000 (:retry-after-ms (ex-data error))))))
        (finally
         (set! js/fetch original-fetch))))))


(deftest fetch-one-rejects-on-invalid-example-payload
  (async-testing "`fetch-one` rejects on invalid example payload"
    (let [original-fetch js/fetch]
      (set! js/fetch (fetch-mocks/mock-fetch-success {:value "Der Hund läuft"}))
      (try
        (try
          (await (sut/fetch-one "Hund" [] nil))
          (is false "Should have rejected")
          (catch :default error
            (is (= sut/invalid-response-message (ex-message error)))
            (is (= "Hund" (:word (ex-data error))))
            (is (= :invalid-response (:error-kind (ex-data error))))))
        (finally
         (set! js/fetch original-fetch))))))


(deftest fetch-one-rejects-on-invalid-json-success-response
  (async-testing "`fetch-one` rejects on invalid JSON in a success response"
    (let [original-fetch js/fetch]
      (set! js/fetch (fetch-mocks/mock-fetch-success-invalid-json))
      (try
        (try
          (await (sut/fetch-one "Hund" [] nil))
          (is false "Should have rejected")
          (catch :default error
            (is (= sut/invalid-response-message (ex-message error)))
            (is (= 502 (:status (ex-data error))))
            (is (= :invalid-json (:error-kind (ex-data error))))))
        (finally
         (set! js/fetch original-fetch))))))


(deftest fetch-one-rejects-on-network-error
  (async-testing "`fetch-one` rejects on network error"
    (let [original-fetch js/fetch]
      (set! js/fetch (fetch-mocks/mock-fetch-network-error))
      (try
        (try
          (await (sut/fetch-one "Hund" [] nil))
          (is false "Should have rejected")
          (catch :default error
            (is (= "Network error" (.-message error)))))
        (finally
         (set! js/fetch original-fetch))))))


(def ^:private schlaeft
  {:structure   [{:dictionaryForm "Hund" :usedForm "Hund" :wordIndex 1}]
   :translation "Пёс спит."
   :value       "Der Hund schläft."})


(def ^:private bellt
  {:structure [] :translation "Пёс лает." :value "Der Hund bellt."})


(deftest save-example-inserts-correct-document
  (async-testing "`save-example!` stores the example in user-db under an id its pair and its content make"
    (await
     (with-test-dbs
      (^:async fn
       [dbs]
       (await (sut/save-example! dbs "word-123" "Hund" nil bellt))
       (await (sut/save-example! dbs "word-123" "Hund" "coll-1" bellt))
       (let [docs  (await (db-queries/fetch-examples (:user/db dbs)))
             saved (first docs)]
         (is (= 2 (count docs)))
         (is (re-matches #"example:word-123::[0-9a-f]{12}" (:_id saved))
             "the id names the pair, and a hash of the example follows; a pair in no collection leaves its part empty")
         (is (re-matches #"example:word-123:coll-1:[0-9a-f]{12}" (:_id (second docs))))
         (is (= (subs (:_id (first docs)) 18) (subs (:_id (second docs)) 24))
             "one example has one hash, whatever its collection")
         (is (= "example" (:type saved)))
         (is (= "word-123" (:word-id saved)))
         (is (= "Hund" (:word saved)))
         (is (= "Der Hund bellt." (:value saved)))
         (is (not (contains? saved :created-at)) "nothing that depends on the device or the time")
         (is (empty? (await (db-queries/fetch-examples (:device/db dbs))))
             "device-db holds no example")))))))


(deftest an-example-s-id-and-revision-are-pinned
  (async-testing "a change to the hash, the key order or the body's fields changes these, and every device must agree on them"
    (await
     (with-test-dbs
      (^:async fn
       [dbs]
       (await (sut/save-example! dbs "vocab:hund" "Hund" "collection:1-87155332" schlaeft))
       (let [[stored] (await (db-queries/fetch-examples (:user/db dbs)))]
         (is (= "example:vocab:hund:collection:1-87155332:25b7d6a32783" (:_id stored)))
         (is (= "1-b8443dc633c55a5f1142d42e88eebfda" (:_rev stored)))))))))


(deftest save-example-keeps-every-distinct-example-and-one-of-each
  (async-testing "another example of a stored pair is kept beside it; the same example again writes nothing"
    (await
     (with-test-dbs
      (^:async fn
       [dbs]
       (await (sut/save-example! dbs "word-123" "Hund" "coll-1" schlaeft))
       (is (some? (await (sut/save-example! dbs "word-123" "Hund" "coll-1" bellt)))
           "a second example of the pair is written")
       (is (nil? (await (sut/save-example! dbs "word-123" "Hund" "coll-1" schlaeft)))
           "the same example again writes nothing")
       (let [docs (await (db-queries/fetch-examples (:user/db dbs)))]
         (is (= #{"Der Hund schläft." "Der Hund bellt."} (set (map :value docs))))
         (is (every? #(= "1" (first (.split (:_rev %) "-"))) docs) "no second revision")))))))


(deftest save-example-throws-on-missing-value
  (let [example {:translation "The dog"}]
    (is (thrown-with-msg? js/Error
                          #"missing required fields"
                          (sut/save-example! nil "word-123" "Hund" nil example)))))


(deftest save-example-throws-on-missing-translation
  (let [example {:value "Der Hund"}]
    (is (thrown-with-msg? js/Error
                          #"missing required fields"
                          (sut/save-example! nil "word-123" "Hund" nil example)))))


(deftest task-handler-fetches-and-saves-on-success
  (async-testing "task handler fetches and saves example"
    (let [example        {:value "Der Hund läuft" :translation "The dog runs"}
          requested-url  (atom nil)
          original-fetch js/fetch]
      (set! js/fetch
            (fn [url]
              (reset! requested-url url)
              ((fetch-mocks/mock-fetch-success example) url)))
      (try
        (await
         (with-test-dbs
          (^:async fn
           [dbs]
           (let [result (await (tasks/execute-task
                                {:task-type "example-fetch"
                                 :data      {:word-id "word-123" :word "Hund" :translations ["собака"]}}
                                (task-env dbs)))]
             (is (true? result))
             (is (= "/api/examples?word=Hund&translation=%D1%81%D0%BE%D0%B1%D0%B0%D0%BA%D0%B0"
                    @requested-url))
             (let [examples (await (db-queries/fetch-examples (:user/db dbs)))]
               (is (= 1 (count examples)))
               (is (re-matches #"example:word-123::[0-9a-f]{12}" (:_id (first examples))))
               (is (= "Der Hund läuft" (:value (first examples)))))))))
        (finally
         (set! js/fetch original-fetch))))))


(deftest a-fetch-whose-pair-is-answered-sends-no-request
  (async-testing "an example that arrived after the task was queued ends the task without a request"
    (let [requested      (atom [])
          original-fetch js/fetch
          fetch!         (fn [dbs collection-id]
                           (tasks/execute-task
                            {:task-type "example-fetch"
                             :data      {:collection-id collection-id :word-id "word-123" :word "Hund" :translations []}}
                            (task-env dbs)))]
      (set! js/fetch
            (fn [url]
              (swap! requested conj url)
              ((fetch-mocks/mock-fetch-success {:value "Der Hund läuft" :translation "Пёс бежит"}) url)))
      (try
        (await
         (with-test-dbs
          (^:async fn
           [dbs]
           ;; Stored as a pass brings it from another device.
           (await (sut/save-example! dbs "word-123" "Hund" "coll-1" schlaeft))
           (is (true? (await (fetch! dbs "coll-1"))) "the task is done")
           (is (true? (await (fetch! dbs nil))) "the main card sees every example of the word")
           (is (empty? @requested) "no example request is sent")
           (is (= ["Der Hund schläft."] (map :value (await (db-queries/fetch-examples (:user/db dbs)))))
               "nothing is written")
           (await (fetch! dbs "coll-2"))
           (is (= 1 (count @requested)) "another collection's example does not answer a theme"))))
        (finally
         (set! js/fetch original-fetch))))))


(deftest a-held-fetch-waits
  (async-testing "a fetch held back runs once what holds it resolves"
    (let [original-fetch js/fetch
          requested      (atom 0)
          release        (atom nil)]
      (set! js/fetch (fn [url]
                       (swap! requested inc)
                       ((fetch-mocks/mock-fetch-success {:value "Der Hund läuft" :translation "Пёс бежит"}) url)))
      (sut/hold-until! (js/Promise. (fn [resolve] (reset! release resolve))))
      (try
        (await
         (with-test-dbs
          (^:async fn
           [dbs]
           (let [done (tasks/execute-task {:task-type "example-fetch"
                                           :data      {:word-id "word-123" :word "Hund" :translations []}}
                                          (task-env dbs))]
             (await (wait/settled))
             (is (zero? @requested) "nothing is sent while the fetch is held")
             (@release nil)
             (is (true? (await done)))
             (is (= 1 @requested))))))
        (finally
         (sut/hold-until! (js/Promise.resolve nil))
         (set! js/fetch original-fetch))))))


(deftest an-arriving-example-cancels-the-fetch-of-its-pair
  (async-testing "the fetch ids follow from the example ids; other fetches stay"
    (await
     (with-test-dbs
      (^:async fn
       [dbs]
       (let [word {:id "vocab:hund" :value "Hund" :translation []}]
         (await (sut/request! dbs (test-clock) [{:collection-id "collection:1-87155332" :word word} {:word word}]))
         (is (= 1 (await (sut/cancel-answered! dbs [(:_id (documents/example-doc "vocab:hund" "Hund" "collection:1-87155332" schlaeft))
                                                    (:_id (documents/example-doc "vocab:katze" "Katze" nil bellt))]))))
         (is (= ["task:example-fetch:vocab:hund:"]
                (map :_id (await (db-queries/fetch-by-type (:device/db dbs) "task")))))))))))


(deftest task-handler-sends-all-confirmed-translations
  (async-testing "task handler sends every confirmed Russian translation as a repeated query param"
    (let [example        {:value "Wir sitzen auf einer Bank im Park." :translation "Мы сидим на скамейке в парке."}
          requested-url  (atom nil)
          original-fetch js/fetch]
      (set! js/fetch
            (fn [url]
              (reset! requested-url url)
              ((fetch-mocks/mock-fetch-success example) url)))
      (try
        (await
         (with-test-dbs
          (^:async fn
           [dbs]
           (await (tasks/execute-task
                   {:task-type "example-fetch"
                    :data      {:word-id "word-bank" :word "Bank" :translations ["банк" "скамейка"]}}
                   (task-env dbs)))
           (is
            (=
             "/api/examples?word=Bank&translation=%D0%B1%D0%B0%D0%BD%D0%BA&translation=%D1%81%D0%BA%D0%B0%D0%BC%D0%B5%D0%B9%D0%BA%D0%B0"
             @requested-url)))))
        (finally
         (set! js/fetch original-fetch))))))


(deftest task-handler-returns-false-on-fetch-failure
  (async-testing "task handler returns false on fetch failure"
    (let [original-fetch js/fetch]
      (set! js/fetch (fetch-mocks/mock-fetch-error 500))
      (try
        (await
         (with-test-dbs
          (^:async fn
           [dbs]
           (let [result (await (tasks/execute-task
                                {:task-type "example-fetch"
                                 :data      {:word-id "word-123" :word "Hund" :translations []}}
                                (task-env dbs)))]
             (is (false? result))))))
        (finally
         (set! js/fetch original-fetch))))))


(deftest task-handler-returns-retry-hint-when-server-provides-it
  (async-testing "task handler returns retry-after hint when fetch failure includes Retry-After"
    (let [original-fetch js/fetch]
      (set! js/fetch
            (fetch-mocks/mock-fetch-error-with-body
             429
             {:error "Examples are temporarily unavailable"}
             {"Retry-After" "3"}))
      (try
        (await
         (with-test-dbs
          (^:async fn
           [dbs]
           (let [result (await (tasks/execute-task
                                {:task-type "example-fetch"
                                 :data      {:word-id "word-123" :word "Hund" :translations []}}
                                (task-env dbs)))]
             (is (= {:retry-after-ms 3000} result))))))
        (finally
         (set! js/fetch original-fetch))))))


(deftest task-handler-returns-false-on-invalid-example-payload
  (async-testing "task handler returns false on invalid example payload"
    (let [original-fetch js/fetch]
      (set! js/fetch (fetch-mocks/mock-fetch-success {:translation "Собака бежит"}))
      (try
        (await
         (with-test-dbs
          (^:async fn
           [dbs]
           (let [result   (await (tasks/execute-task
                                  {:task-type "example-fetch"
                                   :data      {:word-id "word-123" :word "Hund" :translations []}}
                                  (task-env dbs)))
                 examples (await (db-queries/fetch-examples (:user/db dbs)))]
             (is (false? result))
             (is (empty? examples))))))
        (finally
         (set! js/fetch original-fetch))))))


(deftest a-fetch-is-one-task-per-pair
  (async-testing "the pair is the identity, so asking twice writes once"
    (await
     (with-test-dbs
      (^:async fn
       [dbs]
       (let [request {:collection-id "collection-1"
                      :collection-name "\u041f\u043e\u0435\u0437\u0434\u043a\u0430"
                      :word {:id "vocab:hund" :value "Hund" :translation []}}]
         (await (sut/request! dbs (test-clock) [request]))
         (await (sut/request! dbs (test-clock) [request]))
         (let [tasks (await (db-queries/fetch-by-type (:device/db dbs) "task"))]
           (is (= 1 (count tasks)))
           (is (= "task:example-fetch:vocab:hund:collection-1" (:_id (first tasks)))
               "the id names the pair, so the second write is a conflict"))))))))


(deftest a-fetch-queued-with-a-delay-is-due-then
  (async-testing "a fetch is due the delay it was queued with from now"
    (await
     (with-test-dbs
      (^:async fn
       [dbs]
       (let [word {:id "vocab:hund" :value "Hund" :translation []}]
         (await (sut/request! dbs (test-clock) [{:word word}]))
         (await (sut/request! dbs (test-clock) [{:collection-id "coll-1" :delay-ms 30000 :word word}]))
         (let [run-at (into {}
                            (map (juxt :_id :run-at))
                            (await (db-queries/fetch-by-type (:device/db dbs) "task")))]
           (is (= time/test-now-iso (run-at "task:example-fetch:vocab:hund:")))
           (is (= (utils/ms->iso (+ (time/now-ms) 30000)) (run-at "task:example-fetch:vocab:hund:coll-1"))))))))))


(deftest a-dead-lettered-fetch-leaves-its-pair-askable
  (async-testing "the failure is kept under a key of its own"
    (await
     (with-test-dbs
      (^:async fn
       [dbs]
       (let [request {:collection-id nil
                      :collection-name nil
                      :word {:id "vocab:hund" :value "Hund" :translation []}}]
         (await (sut/request! dbs (test-clock) [request]))
         (let [[task] (await (db-queries/fetch-by-type (:device/db dbs) "task"))]
           ;; What the queue does with a task it will never run again.
           (await (tasks/execute-task (assoc task :task-type "unknown-to-everyone")
                                      {:dbs dbs :clock (test-clock)}))
           (await (db/remove (:device/db dbs) task))
           (await (db/insert (:device/db dbs)
                             (-> task
                                 (assoc :_id (str (:_id task) ":failed") :status "failed")
                                 (dissoc :_rev)))))
         (await (sut/request! dbs (test-clock) [request]))
         (let [ids (set (map :_id (await (db-queries/fetch-by-type (:device/db dbs) "task"))))]
           (is (contains? ids "task:example-fetch:vocab:hund:")
               "the live id is free again, so the pair can be asked for")
           (is (contains? ids "task:example-fetch:vocab:hund::failed")
               "and the failure is still there to read"))))))))


(deftest request-queues-the-same-task-for-a-phrase
  (async-testing "GH-371: the fetch path reads a vocabulary entry, not a word"
    (await
     (with-test-dbs
      (^:async fn
       [dbs]
       (let [phrase {:id          "vocab:auf jeden fall"
                     :kind        "phrase"
                     :translation [{:lang "ru" :value "во всяком случае"}]
                     :value       "auf jeden Fall"}]
         (await (sut/request! dbs
                              (test-clock)
                              [{:collection-id "collection-1"
                                :collection-name "Поездка"
                                :word phrase}]))
         (let [tasks (await (db-queries/fetch-by-type (:device/db dbs) "task"))
               {:keys [data task-type]} (first tasks)]
           (is (= 1 (count tasks)))
           (is (= "example-fetch" task-type))
           (is (= "auf jeden Fall" (:word data)))
           (is (= "vocab:auf jeden fall" (:word-id data)))
           (is (= ["во всяком случае"] (:translations data)))
           (is (= "collection-1" (:collection-id data)))
           (is (= "Поездка" (:collection-name data))))))))))


(defn- ^:async with-conflicts
  "The example documents of `db`, each with the revisions it conflicts
   with."
  [db]
  (let [{rows :rows} (await (db/all-docs db {:conflicts true :include-docs true}))]
    (into [] (comp (map :doc) (filter #(= "example" (:type %)))) rows)))


(def ^:private moved-schlaeft
  "`schlaeft` as an earlier build kept it in device-db and the move writes
   it: a generated id, a creation time, and the structure's keys in another
   order."
  {:_id           "3F2B9C1A"
   :collection-id "coll-1"
   :created-at    "2026-01-01T00:00:00.000Z"
   :structure     [{:wordIndex 1 :usedForm "Hund" :dictionaryForm "Hund"}]
   :translation   (:translation schlaeft)
   :type          "example"
   :value         (:value schlaeft)
   :word          "Hund"
   :word-id       "vocab:hund"})


(defn- fetched
  "Stores `example` on the device whose user-db is `db`, as a fetch does."
  [example]
  (fn [db]
    (sut/save-example! {:user/db db} "vocab:hund" "Hund" "coll-1" example)))


(defn- moved
  "Stores `kept` on the device whose user-db is `db`, as the move does."
  [kept]
  (fn [db]
    (dbs/insert-all-if-absent {:user/db db}
                              documents/example-schema
                              [(documents/example-doc "vocab:hund" "Hund" "coll-1" kept)])))


(deftest two-devices-hold-one-document-per-example
  (async-testing "each device stores an example of one pair before either replicates"
    (doseq [{:keys [label one other values]}
            [{:label  "the same example, fetched on both"
              :one    (fetched schlaeft)
              :other  (fetched schlaeft)
              :values ["Der Hund schläft."]}
             {:label  "different examples, fetched on each"
              :one    (fetched schlaeft)
              :other  (fetched bellt)
              :values ["Der Hund bellt." "Der Hund schläft."]}
             {:label  "the same example, moved on one and fetched on the other"
              :one    (moved moved-schlaeft)
              :other  (fetched schlaeft)
              :values ["Der Hund schläft."]}]]
      ;; Each case starts from two empty databases.
      (await (db-fixtures/destroy-test-db test-device-db-name))
      (await (db-fixtures/destroy-test-db test-user-db-name))
      (await
       (db-fixtures/with-test-dbs
        [test-device-db-name test-user-db-name]
        ;; Two databases, each standing for the user-db of one device.
        (^:async fn
         [[db-one db-other]]
         (await (one db-one))
         (await (other db-other))
         (await (replication/replicated! db-one db-other))
         (await (replication/replicated! db-other db-one))
         (doseq [db [db-one db-other]]
           (let [docs (await (with-conflicts db))]
             (is (= values (sort (map :value docs))) (str label ": one document per distinct example"))
             (is (every? (comp empty? :_conflicts) docs) (str label ": no conflict"))))))))))

(ns client.examples-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.examples :as sut]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.fetch-mocks :as fetch-mocks]
   [client.support.time :as time]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [tasks :as tasks]))


(def test-device-db-name (db-fixtures/db-name "client.examples-test"))


(def test-user-db-name (db-fixtures/db-name "client.examples-test-user"))


(use-fixtures :each (db-fixtures/db-fixture-multi [test-device-db-name test-user-db-name]))


(defn- test-clock
  []
  {:clock/now-iso time/now-iso
   :clock/now-ms  time/now-ms})


(defn- task-env
  [dbs]
  {:dbs   dbs
   :clock (test-clock)})


(defn- with-test-db
  [f]
  (db-fixtures/with-test-db test-device-db-name f))


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


(deftest save-example-inserts-correct-document
  (async-testing "`save-example!` inserts correct document"
    (with-test-db
      (^:async fn
       [db]
       (let [example {:value "Der Hund" :translation "The dog" :structure []}
             dbs     {:device/db db}]
         (await (sut/save-example! dbs (test-clock) "word-123" "Hund" nil example))
         (let [docs  (await (db-queries/fetch-examples db))
               saved (first docs)]
           (is (= 1 (count docs)))
           (is (= "example" (:type saved)))
           (is (= "word-123" (:word-id saved)))
           (is (= "Hund" (:word saved)))
           (is (= "Der Hund" (:value saved)))))))))


(deftest save-example-throws-on-missing-value
  (let [example {:translation "The dog"}]
    (is (thrown-with-msg? js/Error
                          #"missing required fields"
                          (sut/save-example! nil nil "word-123" "Hund" nil example)))))


(deftest save-example-throws-on-missing-translation
  (let [example {:value "Der Hund"}]
    (is (thrown-with-msg? js/Error
                          #"missing required fields"
                          (sut/save-example! nil nil "word-123" "Hund" nil example)))))


(deftest of-word-reads-past-the-default-page
  (async-testing "`of-word` returns every example of a word, not the first 25"
    (with-test-db
      (^:async fn
       [db]
       (let [dbs     {:device/db db}
             collection-ids (mapv #(str "collection-" %) (range 30))
             example {:value "Der Hund" :translation "The dog" :structure []}]
         (doseq [collection-id collection-ids]
           (await (sut/save-example! dbs (test-clock) "word-1" "Hund" collection-id example)))
         (await (sut/save-example! dbs (test-clock) "word-2" "Katze" nil example))
         (let [examples (await (sut/of-word dbs "word-1"))]
           (is (= 30 (count examples)))
           (is (= (set collection-ids) (set (map :collection-id examples))))))))))


(deftest remove-deletes-existing-document
  (async-testing "`remove!` deletes existing document"
    (with-test-db
      (^:async fn
       [db]
       (let [{:keys [id]} (await (db/insert db {:type "example" :word-id "w1"}))]
         (await (sut/remove! {:device/db db} id))
         (let [examples (await (db-queries/fetch-examples db))]
           (is (empty? examples))))))))


(deftest remove-is-noop-when-not-exists
  (async-testing "`remove!` no-op when not found"
    (with-test-db
      (^:async fn
       [db]
       (await (sut/remove! {:device/db db} "nonexistent"))
       (let [examples (await (db-queries/fetch-examples db))]
         (is (empty? examples)))))))


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
             (let [examples (await (db-queries/fetch-examples (:device/db dbs)))]
               (is (= 1 (count examples)))
               (is (= "Der Hund läuft" (:value (first examples)))))))))
        (finally
         (set! js/fetch original-fetch))))))


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
                 examples (await (db-queries/fetch-examples (:device/db dbs)))]
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

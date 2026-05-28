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


(deftest find-returns-example-when-exists
  (async-testing "`find` returns example when it exists"
    (with-test-db
      (^:async fn
       [db]
       (await (db/insert db {:type "example" :word-id "word-123" :value "test"}))
       (let [result (await (sut/find {:device/db db} "word-123" nil))]
         (is (some? result))
         (is (= "word-123" (:word-id result))))))))


(deftest find-returns-nil-when-not-exists
  (async-testing "`find` returns nil when not found"
    (with-test-db
      (^:async fn
       [db]
       (let [result (await (sut/find {:device/db db} "nonexistent" nil))]
         (is (nil? result)))))))


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


(deftest task-handler-returns-true-when-word-deleted
  (async-testing "task handler returns true when word is deleted"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [result (await (tasks/execute-task
                           {:task-type "example-fetch"
                            :data      {:word-id "deleted-word"}}
                           (task-env dbs)))]
        (is (true? result)))))))


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
           (await (db/insert (:user/db dbs)
                             {:_id         "word-123"
                              :type        "vocab"
                              :value       "Hund"
                              :translation [{:lang "ru" :value "собака"}]}))
           (let [result (await (tasks/execute-task
                                {:task-type "example-fetch"
                                 :data      {:word-id "word-123"}}
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
           (await (db/insert (:user/db dbs)
                             {:_id         "word-bank"
                              :type        "vocab"
                              :value       "Bank"
                              :translation [{:lang "ru" :value "банк"}
                                            {:lang "ru" :value "скамейка"}]}))
           (await (tasks/execute-task
                   {:task-type "example-fetch"
                    :data      {:word-id "word-bank"}}
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
           (await (db/insert (:user/db dbs) {:_id "word-123" :type "vocab" :value "Hund"}))
           (let [result (await (tasks/execute-task
                                {:task-type "example-fetch"
                                 :data      {:word-id "word-123"}}
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
           (await (db/insert (:user/db dbs) {:_id "word-123" :type "vocab" :value "Hund"}))
           (let [result (await (tasks/execute-task
                                {:task-type "example-fetch"
                                 :data      {:word-id "word-123"}}
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
           (await (db/insert (:user/db dbs) {:_id "word-123" :type "vocab" :value "Hund"}))
           (let [result   (await (tasks/execute-task
                                  {:task-type "example-fetch"
                                   :data      {:word-id "word-123"}}
                                  (task-env dbs)))
                 examples (await (db-queries/fetch-examples (:device/db dbs)))]
             (is (false? result))
             (is (empty? examples))))))
        (finally
         (set! js/fetch original-fetch))))))

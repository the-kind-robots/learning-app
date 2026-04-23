(ns client.examples-test
  (:require
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.fetch-mocks :as fetch-mocks]
   [db :as db]
   [examples :as sut]
   [promesa.core :as p]
   [tasks :as tasks])
  (:require-macros
   [client.support.test :refer [async-testing]]))


;; =============================================================================
;; Test Helpers
;; =============================================================================


(def test-device-db-name (db-fixtures/db-name "client.examples-test"))


(def test-user-db-name (db-fixtures/db-name "client.examples-test-user"))


(use-fixtures :each (db-fixtures/db-fixture-multi [test-device-db-name test-user-db-name]))


(defn- with-test-db
  "Uses local test DB, calls (f db-instance)."
  [f]
  (db-fixtures/with-test-db test-device-db-name f))


(defn- with-test-dbs
  "Sets up test DBs, calls (f dbs) where dbs is {:device/db ... :user/db ...}."
  [f]
  (db-fixtures/with-test-dbs
   [test-device-db-name test-user-db-name]
   (fn [[device-db user-db]]
     (f {:device/db device-db :user/db user-db}))))


;; =============================================================================
;; Unit Tests: fetch-one
;; =============================================================================


(deftest fetch-one-returns-parsed-json-on-success
  (async-testing "`fetch-one` returns parsed JSON on success"
    (let [example        {:value "Ich habe einen Hund" :translation "I have a dog"}
          original-fetch js/fetch]
      (set! js/fetch (fetch-mocks/mock-fetch-success example))
      (p/finally
        (p/let [result (sut/fetch-one "Hund")]
          (is (= "Ich habe einen Hund" (:value result)))
          (is (= "I have a dog" (:translation result))))
        (fn []
          (set! js/fetch original-fetch))))))


(deftest fetch-one-rejects-on-server-error
  (async-testing "`fetch-one` rejects on server error"
    (let [original-fetch js/fetch]
      (set! js/fetch
            (fetch-mocks/mock-fetch-error-with-body
             500
             {:error "Examples are temporarily unavailable"}))
      (p/finally
        (p/catch
          (p/do
            (sut/fetch-one "Hund")
            (is false "Should have rejected"))
          (fn [error]
            (is (= "Examples are temporarily unavailable" (ex-message error)))
            (is (= 500 (:status (ex-data error))))))
        (fn []
          (set! js/fetch original-fetch))))))


(deftest fetch-one-includes-retry-after-ms-on-server-error
  (async-testing "`fetch-one` includes retry-after-ms when the server suggests a retry delay"
    (let [original-fetch js/fetch]
      (set! js/fetch
            (fetch-mocks/mock-fetch-error-with-body
             429
             {:error "Examples are temporarily unavailable"}
             {"Retry-After" "2"}))
      (p/finally
        (p/catch
          (p/do
            (sut/fetch-one "Hund")
            (is false "Should have rejected"))
          (fn [error]
            (is (= 429 (:status (ex-data error))))
            (is (= 2000 (:retry-after-ms (ex-data error))))))
        (fn []
          (set! js/fetch original-fetch))))))


(deftest fetch-one-rejects-on-invalid-example-payload
  (async-testing "`fetch-one` rejects on invalid example payload"
    (let [original-fetch js/fetch]
      (set! js/fetch (fetch-mocks/mock-fetch-success {:value "Der Hund läuft"}))
      (p/finally
        (p/catch
          (p/do
            (sut/fetch-one "Hund")
            (is false "Should have rejected"))
          (fn [error]
            (is (= sut/invalid-response-message (ex-message error)))
            (is (= "Hund" (:word (ex-data error))))
            (is (= :invalid-response (:error-kind (ex-data error))))))
        (fn []
          (set! js/fetch original-fetch))))))


(deftest fetch-one-rejects-on-invalid-json-success-response
  (async-testing "`fetch-one` rejects on invalid JSON in a success response"
    (let [original-fetch js/fetch]
      (set! js/fetch (fetch-mocks/mock-fetch-success-invalid-json))
      (p/finally
        (p/catch
          (p/do
            (sut/fetch-one "Hund")
            (is false "Should have rejected"))
          (fn [error]
            (is (= sut/invalid-response-message (ex-message error)))
            (is (= 502 (:status (ex-data error))))
            (is (= :invalid-json (:error-kind (ex-data error))))))
        (fn []
          (set! js/fetch original-fetch))))))


(deftest fetch-one-rejects-on-network-error
  (async-testing "`fetch-one` rejects on network error"
    (let [original-fetch js/fetch]
      (set! js/fetch (fetch-mocks/mock-fetch-network-error))
      (p/finally
        (p/catch
          (p/do
            (sut/fetch-one "Hund")
            (is false "Should have rejected"))
          (fn [error]
            (is (= "Network error" (.-message error)))))
        (fn []
          (set! js/fetch original-fetch))))))


;; =============================================================================
;; Unit Tests: save-example!
;; =============================================================================


(deftest save-example-inserts-correct-document
  (async-testing "`save-example!` inserts correct document"
    (with-test-db
      (fn [db]
        (let [example {:value "Der Hund" :translation "The dog" :structure []}
              dbs     {:device/db db}]
          (p/do
            (sut/save-example! dbs "word-123" "Hund" example)
            (p/let [docs (db-queries/fetch-examples db)]
              (let [saved (first docs)]
                (is (= 1 (count docs)))
                (is (= "example" (:type saved)))
                (is (= "word-123" (:word-id saved)))
                (is (= "Hund" (:word saved)))
                (is (= "Der Hund" (:value saved)))))))))))


(deftest save-example-throws-on-missing-value
  (let [example {:translation "The dog"}]
    (is (thrown-with-msg? js/Error
                          #"missing required fields"
                          (sut/save-example! nil "word-123" "Hund" example)))))


(deftest save-example-throws-on-missing-translation
  (let [example {:value "Der Hund"}]
    (is (thrown-with-msg? js/Error
                          #"missing required fields"
                          (sut/save-example! nil "word-123" "Hund" example)))))


;; =============================================================================
;; Unit Tests: find
;; =============================================================================


(deftest find-returns-example-when-exists
  (async-testing "`find` returns example when it exists"
    (with-test-db
      (fn [db]
        (p/do
          (db/insert db {:type "example" :word-id "word-123" :value "test"})
          (p/let [result (sut/find {:device/db db} "word-123")]
            (is (some? result))
            (is (= "word-123" (:word-id result)))))))))


(deftest find-returns-nil-when-not-exists
  (async-testing "`find` returns nil when not found"
    (with-test-db
      (fn [db]
        (p/let [result (sut/find {:device/db db} "nonexistent")]
          (is (nil? result)))))))


;; =============================================================================
;; Unit Tests: remove!
;; =============================================================================


(deftest remove-deletes-existing-document
  (async-testing "`remove!` deletes existing document"
    (with-test-db
      (fn [db]
        (p/do
          (p/let [{:keys [id]} (db/insert db {:type "example" :word-id "w1"})]
            (sut/remove! {:device/db db} id)
            (p/let [examples (db-queries/fetch-examples db)]
              (is (empty? examples)))))))))


(deftest remove-is-noop-when-not-exists
  (async-testing "`remove!` no-op when not found"
    (with-test-db
      (fn [db]
        (p/do
          (sut/remove! {:device/db db} "nonexistent")
          (p/let [examples (db-queries/fetch-examples db)]
            (is (empty? examples))))))))


;; =============================================================================
;; Integration Tests: Task Handler
;; =============================================================================


(deftest fetch-is-idempotent-for-word
  (async-testing "`fetch!` creates one active task per word"
    (with-test-dbs
     (fn [app-dbs]
       (p/with-redefs [tasks/flush! (constantly nil)]
         (p/do
           (sut/fetch! app-dbs "word-123")
           (sut/fetch! app-dbs "word-123")
           (p/let [{:keys [docs]} (db/find (:device/db app-dbs)
                                           {:selector {:type "task"}})]
             (is (= 1 (count docs)))
             (is (= (tasks/id-for "example-fetch" {:word-id "word-123"})
                    (:_id (first docs))))
             (is (= {:word-id "word-123"} (:data (first docs)))))))))))

(deftest task-handler-returns-true-when-word-deleted
  (async-testing "task handler returns true when word is deleted"
    (with-test-dbs
     (fn [dbs]
       (p/let [result (tasks/execute-task
                       {:task-type "example-fetch"
                        :data      {:word-id "deleted-word"}}
                       dbs)]
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
      (p/finally
        (with-test-dbs
         (fn [dbs]
           (p/do
             (db/insert (:user/db dbs) {:_id "word-123"
                                        :type "vocab"
                                        :value "Hund"
                                        :translation [{:lang "ru" :value "собака"}]})
             (p/let [result (tasks/execute-task
                             {:task-type "example-fetch"
                              :data      {:word-id "word-123"}}
                             dbs)]
               (is (true? result))
               (is (= "/api/examples?word=Hund&translation=%D1%81%D0%BE%D0%B1%D0%B0%D0%BA%D0%B0"
                      @requested-url))
               (p/let [examples (db-queries/fetch-examples (:device/db dbs))]
                 (is (= 1 (count examples)))
                 (is (= "Der Hund läuft" (:value (first examples)))))))))
        (fn []
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
      (p/finally
        (with-test-dbs
         (fn [dbs]
           (p/do
             (db/insert (:user/db dbs) {:_id "word-bank"
                                        :type "vocab"
                                        :value "Bank"
                                        :translation [{:lang "ru" :value "банк"}
                                                      {:lang "ru" :value "скамейка"}]})
             (p/let [_ (tasks/execute-task
                        {:task-type "example-fetch"
                         :data      {:word-id "word-bank"}}
                        dbs)]
               (is (= "/api/examples?word=Bank&translation=%D0%B1%D0%B0%D0%BD%D0%BA&translation=%D1%81%D0%BA%D0%B0%D0%BC%D0%B5%D0%B9%D0%BA%D0%B0"
                      @requested-url))))))
        (fn []
          (set! js/fetch original-fetch))))))


(deftest task-handler-returns-retry-result-on-fetch-failure
  (async-testing "task handler returns retry result on fetch failure"
    (let [original-fetch js/fetch]
      (set! js/fetch (fetch-mocks/mock-fetch-error 500))
      (p/finally
        (with-test-dbs
         (fn [dbs]
           (p/do
             (db/insert (:user/db dbs) {:_id "word-123" :type "vocab" :value "Hund"})
             (p/let [result (tasks/execute-task
                             {:task-type "example-fetch"
                              :data      {:word-id "word-123"}}
                             dbs)]
               (is (= :retry (:task-result result)))
               (is (string? (:last-error result)))))))
        (fn []
          (set! js/fetch original-fetch))))))


(deftest task-handler-returns-retry-hint-when-server-provides-it
  (async-testing "task handler returns retry-after hint when fetch failure includes Retry-After"
    (let [original-fetch js/fetch]
      (set! js/fetch
            (fetch-mocks/mock-fetch-error-with-body
             429
             {:error "Examples are temporarily unavailable"}
             {"Retry-After" "3"}))
      (p/finally
        (with-test-dbs
         (fn [dbs]
           (p/do
             (db/insert (:user/db dbs) {:_id "word-123" :type "vocab" :value "Hund"})
             (p/let [result (tasks/execute-task
                             {:task-type "example-fetch"
                              :data      {:word-id "word-123"}}
                             dbs)]
               (is (= :retry (:task-result result)))
               (is (= 3000 (:retry-after-ms result)))
               (is (= "Examples are temporarily unavailable" (:last-error result)))))))
        (fn []
          (set! js/fetch original-fetch))))))


(deftest task-handler-returns-retry-result-on-invalid-example-payload
  (async-testing "task handler returns retry result on invalid example payload"
    (let [original-fetch js/fetch]
      (set! js/fetch (fetch-mocks/mock-fetch-success {:translation "Собака бежит"}))
      (p/finally
        (with-test-dbs
         (fn [dbs]
           (p/do
             (db/insert (:user/db dbs) {:_id "word-123" :type "vocab" :value "Hund"})
             (p/let [result (tasks/execute-task
                             {:task-type "example-fetch"
                              :data      {:word-id "word-123"}}
                             dbs)]
               (is (= :retry (:task-result result)))
               (is (= sut/invalid-response-message (:last-error result)))
               (p/let [examples (db-queries/fetch-examples (:device/db dbs))]
                 (is (empty? examples)))))))
        (fn []
          (set! js/fetch original-fetch))))))

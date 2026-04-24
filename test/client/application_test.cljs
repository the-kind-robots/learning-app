(ns client.application-test
  (:require
   [application :as sut]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [clojure.string :as str]
   [db :as db]
   [dbs :as dbs]
   [dictionary-sync :as dictionary-sync]
   [promesa.core :as p]
   [tasks :as tasks])
  (:require-macros
   [cljs.test :refer [deftest is use-fixtures]]
   [client.support.test :refer [async-testing]]))


(def test-device-db-name (db-fixtures/db-name "client.application-test-device"))
(def test-user-db-name (db-fixtures/db-name "client.application-test-user"))
(def test-dictionary-db-name (db-fixtures/db-name "client.application-test-dictionary"))


(use-fixtures :each
  (db-fixtures/db-fixture-multi [test-device-db-name
                                 test-user-db-name
                                 test-dictionary-db-name]))


(defn- with-test-dbs
  [f]
  (db-fixtures/with-test-dbs
   [test-device-db-name test-user-db-name test-dictionary-db-name]
   (fn [[device-db user-db dictionary-db]]
     (let [app-dbs {:device/db     device-db
                    :user/db       user-db
                    :dictionary/db dictionary-db}]
       (p/with-redefs [dbs/dbs                 (constantly app-dbs)
                       dictionary-sync/loaded? (constantly true)
                       tasks/flush!            (constantly nil)]
         (f app-dbs))))))


(defn- request
  ([method uri]
   (request method uri nil))
  ([method uri query-string]
   (sut/ring-handler
    (cond-> {:request-method method
             :uri            uri
             :headers        {"accept" "text/html"}}
      query-string
      (assoc :query-string query-string)))))


(deftest post-word-examples-adds-user-example-resource
  (async-testing "POST /words/:id/examples adds user example under word"
    (with-test-dbs
      (fn [app-dbs]
        (p/do
          (db/insert (:user/db app-dbs) {:_id "word-1"
                                         :type "vocab"
                                         :value "der Hund"
                                         :translation [{:lang "ru" :value "собака"}]})
          (p/let [response (request :post
                                    "/words/word-1/examples"
                                    "value=Mein%20Hund.&translation=%D0%9C%D0%BE%D1%8F%20%D1%81%D0%BE%D0%B1%D0%B0%D0%BA%D0%B0.")
                  examples (db-queries/fetch-examples (:device/db app-dbs))]
            (is (= 1 (count examples)))
            (is (= 201 (:status response)))
            (is (= (str "/examples/" (:_id (first examples)))
                   (get-in response [:headers "Location"])))
            (is (= "user" (:source (first examples))))
            (is (= "word-1" (:word-id (first examples))))
            (is (nil? (:structure (first examples))))))))))


(deftest get-word-examples-renders-visible-user-edit-fields
  (async-testing "GET /words/:id/examples renders visible inputs for user example edits"
    (with-test-dbs
      (fn [app-dbs]
        (p/do
          (db/insert (:user/db app-dbs) {:_id "word-1"
                                         :type "vocab"
                                         :value "der Hund"
                                         :translation [{:lang "ru" :value "собака"}]})
          (db/insert (:device/db app-dbs) {:_id "example-1"
                                           :type "example"
                                           :word-id "word-1"
                                           :word "der Hund"
                                           :value "Mein Hund schläft."
                                           :translation "Моя собака спит."
                                           :source "user"})
          (p/let [response (request :get "/words/word-1/examples")
                  body     (:body response)]
            (is (= 200 (:status response)))
            (is (str/includes? body "textarea"))
            (is (str/includes? body "name=\"value\""))
            (is (str/includes? body "name=\"translation\""))
            (is (not (str/includes? body "type=\"hidden\"")))))))))


(deftest put-example-fetch-creates-idempotent-task-resource
  (async-testing "PUT /words/:id/example-fetch requests an example"
    (with-test-dbs
      (fn [app-dbs]
        (p/do
          (db/insert (:user/db app-dbs) {:_id "word-1"
                                         :type "vocab"
                                         :value "der Hund"
                                         :translation [{:lang "ru" :value "собака"}]})
          (p/let [first-response  (request :put "/words/word-1/example-fetch")
                  second-response (request :put "/words/word-1/example-fetch")
                  tasks           (db-queries/fetch-by-type (:device/db app-dbs) "task")]
            (is (= 200 (:status first-response)))
            (is (= 200 (:status second-response)))
            (is (= 1 (count tasks)))
            (is (= (tasks/id-for "example-fetch" {:word-id "word-1"})
                   (:_id (first tasks))))))))))


(deftest put-example-fetch-is-noop-when-ready
  (async-testing "PUT /words/:id/example-fetch does not create task when ready"
    (with-test-dbs
      (fn [app-dbs]
        (p/do
          (db/insert (:user/db app-dbs) {:_id "word-1"
                                         :type "vocab"
                                         :value "der Hund"
                                         :translation [{:lang "ru" :value "собака"}]})
          (db/insert (:device/db app-dbs) {:_id "example-1"
                                           :type "example"
                                           :word-id "word-1"
                                           :word "der Hund"
                                           :value "Der Hund bellt."
                                           :translation "Собака лает."
                                           :source "fetched"
                                           :structure [{:usedForm "Hund"
                                                        :dictionaryForm "der Hund"
                                                        :translation "собака"
                                                        :wordIndex 1}]})
          (p/let [response (request :put "/words/word-1/example-fetch")
                  tasks    (db-queries/fetch-by-type (:device/db app-dbs) "task")]
            (is (= 200 (:status response)))
            (is (empty? tasks))))))))


(deftest delete-example-resource-does-not-create-replacement-work
  (async-testing "DELETE /examples/:id deletes example without replacement work"
    (with-test-dbs
      (fn [app-dbs]
        (p/do
          (db/insert (:user/db app-dbs) {:_id "word-1"
                                         :type "vocab"
                                         :value "der Hund"
                                         :translation [{:lang "ru" :value "собака"}]})
          (db/insert (:device/db app-dbs) {:_id "example-1"
                                           :type "example"
                                           :word-id "word-1"
                                           :word "der Hund"
                                           :value "Der Hund bellt."
                                           :translation "Собака лает."
                                           :source "fetched"
                                           :structure [{:usedForm "Hund"
                                                        :dictionaryForm "der Hund"
                                                        :translation "собака"
                                                        :wordIndex 1}]})
          (p/let [response (request :delete "/examples/example-1")
                  examples (db-queries/fetch-examples (:device/db app-dbs))
                  tasks    (db-queries/fetch-by-type (:device/db app-dbs) "task")]
            (is (= 200 (:status response)))
            (is (empty? examples))
            (is (empty? tasks))))))))

(ns client.phrase-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.time :as time]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [ports.progress-store :as progress-store]
   [use-cases.phrase :as sut]
   [utils :as utils]))


(def user-db-name (db-fixtures/db-name "client.phrase-test.user"))


(def device-db-name (db-fixtures/db-name "client.phrase-test.device"))


(use-fixtures :each (db-fixtures/db-fixture-multi [user-db-name device-db-name]))


(defn- with-test-dbs
  [f]
  (db-fixtures/with-test-dbs
   [user-db-name device-db-name]
   (^:async fn
    [[user-db device-db]]
    (with-redefs [utils/now-iso time/now-iso
                  utils/now-ms  time/now-ms]
      (await (f {:user/db user-db :device/db device-db}))))))


(defn- test-capabilities
  [dbs example-requests]
  {:progress-store
   (progress-store/start! {:db    dbs
                           :clock {:clock/now-iso time/now-iso
                                   :clock/now-ms  time/now-ms}})
   :collections
   {:collections/active-id (fn [] nil)
    :collections/get       (fn [_] nil)
    :collections/add-word! (fn [_ _] (js/Promise.resolve nil))}
   :examples
   {:examples/find     (fn [_ _] (js/Promise.resolve nil))
    :examples/request! (fn [& args] (swap! example-requests conj args) nil)}})


(deftest add-creates-phrase-and-initial-review-without-examples
  (async-testing "`add!` creates a phrase doc, seeds a review, requests no example"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [example-requests (atom [])
            {:keys [word-id created?]} (await (sut/add! (test-capabilities dbs example-requests)
                                                        "Entschuldigung, dass ich zu spät komme"
                                                        "Извини, что я опоздал."))
            phrases (await (db-queries/fetch-by-type (:user/db dbs) "phrase"))
            reviews (await (db-queries/fetch-by-type (:user/db dbs) "review"))]
        (is (true? created?))
        (is (= 1 (count phrases)))
        (is (= word-id (:_id (first phrases))))
        (is (= "Entschuldigung, dass ich zu spät komme" (:value (first phrases))))
        (is (= [{:lang "ru" :value "Извини, что я опоздал."}]
               (:translation (first phrases))))
        (is (= 1 (count reviews)))
        (is (= word-id (:word-id (first reviews))))
        (is (empty? @example-requests)))))))


(deftest add-duplicate-merges-translation-whole
  (async-testing "a duplicate phrase merges translations as whole entries"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [example-requests (atom [])
            capabilities     (test-capabilities dbs example-requests)]
        (await (sut/add! capabilities "auf jeden Fall" "во всяком случае"))
        (let [{:keys [created?]} (await (sut/add! capabilities "Auf jeden Fall" "обязательно, точно"))
              phrases (await (db-queries/fetch-by-type (:user/db dbs) "phrase"))]
          (is (false? created?))
          (is (= 1 (count phrases)))
          (is (= [{:lang "ru" :value "во всяком случае"}
                  {:lang "ru" :value "обязательно, точно"}]
                 (:translation (first phrases))))))))))


(deftest add-rejects-blank-translation
  (async-testing "a blank translation is an error"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [example-requests (atom [])
            result (await (sut/add! (test-capabilities dbs example-requests)
                                    "auf jeden Fall"
                                    "   "))]
        (is (= {:error :empty-translations} result)))))))

(ns client.phrase-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.time :as time]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [ports.reviews :as reviews]
   [ports.words :as words]
   [use-cases.vocabulary :as sut]
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
  ([dbs example-requests]
   (test-capabilities dbs example-requests {}))
  ([dbs example-requests {:keys [active-id existing-example]}]
   (let [clock {:clock/now-iso time/now-iso
                :clock/now-ms  time/now-ms}]
     {:clock       clock
      :collections {:collections/active-id (fn [] active-id)
                    :collections/add-word! (fn [_ _] (js/Promise.resolve nil))
                    :collections/get       (fn [id] (js/Promise.resolve {:id id :name "Поездка"}))}
      :examples    {:examples/find     (fn [_ _] (js/Promise.resolve existing-example))
                    :examples/request! (fn [& args] (swap! example-requests conj (vec args)) nil)}
      :reviews     (reviews/start! {:clock clock :db dbs})
      :words       (words/start! {:clock clock :db dbs})})))


(deftest add-creates-phrase-and-initial-review-and-asks-for-an-example
  (async-testing "`add!` creates a phrase doc, seeds a review, and queues the example fetch"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [example-requests (atom [])
            {:keys [word-id created?]} (await (sut/add! (test-capabilities dbs example-requests)
                                                        "Entschuldigung, dass ich zu spät komme"
                                                        "Извини, что я опоздал."
                                                        :phrase))
            entries (await (db-queries/fetch-by-type (:user/db dbs) "vocab"))
            reviews (await (db-queries/fetch-by-type (:user/db dbs) "review"))]
        (is (true? created?))
        (is (= 1 (count entries)))
        (is (= word-id (:_id (first entries))))
        (is (= "phrase" (:kind (first entries))))
        (is (= "Entschuldigung, dass ich zu spät komme" (:value (first entries))))
        (is (= [{:lang "ru" :value "Извини, что я опоздал."}]
               (:translation (first entries))))
        (is (= 1 (count reviews)))
        (is (= word-id (:word-id (first reviews))))
        (is (= 1 (count @example-requests))
            "a phrase asks for an example like a word does")
        (is (= "phrase" (:kind (ffirst @example-requests)))))))))


(deftest add-queues-the-example-with-the-active-collection
  (async-testing "the queued fetch carries the active collection's id and name"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [example-requests (atom [])
            capabilities     (test-capabilities dbs example-requests {:active-id "collection-1"})]
        (await (sut/add! capabilities "auf jeden Fall" "во всяком случае" :phrase))
        (is (= 1 (count @example-requests)))
        (let [[_entry collection-id collection-name] (first @example-requests)]
          (is (= "collection-1" collection-id))
          (is (= "Поездка" collection-name))))))))


(deftest re-adding-into-a-collection-that-has-an-example-queues-nothing
  (async-testing "the re-fetch rule is the one words already follow"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [example-requests (atom [])
            capabilities     (test-capabilities dbs
                                                example-requests
                                                {:active-id        "collection-1"
                                                 :existing-example {:id "example-1"}})]
        (await (sut/add! capabilities "auf jeden Fall" "во всяком случае" :phrase))
        (reset! example-requests [])
        (await (sut/add! capabilities "auf jeden Fall" "обязательно" :phrase))
        (is (empty? @example-requests)))))))


(deftest add-duplicate-merges-translation-whole
  (async-testing "a duplicate phrase merges translations as whole entries"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [example-requests (atom [])
            capabilities     (test-capabilities dbs example-requests)]
        (await (sut/add! capabilities "auf jeden Fall" "во всяком случае" :phrase))
        (let [{:keys [created?]} (await (sut/add! capabilities "Auf jeden Fall" "обязательно, точно" :phrase))
              entries (await (db-queries/fetch-by-type (:user/db dbs) "vocab"))]
          (is (false? created?))
          (is (= 1 (count entries)))
          (is (= [{:lang "ru" :value "во всяком случае"}
                  {:lang "ru" :value "обязательно, точно"}]
                 (:translation (first entries)))
              "the translation is never split on punctuation")))))))


(deftest adding-a-phrase-over-a-word-merges-and-keeps-its-kind
  (async-testing "one value is one entry, and a second add does not change what it is"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [example-requests (atom [])
            capabilities     (test-capabilities dbs example-requests)]
        (await (sut/add! capabilities "Guten Morgen" "доброе утро" :word))
        (let [{:keys [created?]} (await (sut/add! capabilities "guten Morgen" "доброго утра" :phrase))
              entries (await (db-queries/fetch-by-type (:user/db dbs) "vocab"))]
          (is (false? created?))
          (is (= 1 (count entries)))
          (is (nil? (:kind (first entries)))
              "it was entered as a word and stays one")
          (is (= ["доброе утро" "доброго утра"]
                 (mapv :value (:translation (first entries)))))))))))


(deftest add-rejects-blank-translation
  (async-testing "a blank translation is an error"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [example-requests (atom [])
            result (await (sut/add! (test-capabilities dbs example-requests)
                                    "auf jeden Fall"
                                    "   "
                                    :phrase))]
        (is (= {:error :empty-translations} result))
        (is (empty? @example-requests)))))))


(deftest add-collapses-line-breaks-in-a-phrase
  (async-testing "the multi-line field's breaks are visual only"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [example-requests (atom [])]
        (await (sut/add! (test-capabilities dbs example-requests)
                         "auf jeden Fall"
                         "во всяком\n   случае"
                         :phrase))
        (let [entries (await (db-queries/fetch-by-type (:user/db dbs) "vocab"))]
          (is (= [{:lang "ru" :value "во всяком случае"}]
                 (:translation (first entries))))))))))

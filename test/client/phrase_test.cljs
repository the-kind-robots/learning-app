(ns client.phrase-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.learner :as learner]
   [client.support.time :as time]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [domain.vocabulary :as vocabulary]
   [use-cases.vocabulary :as sut]
   [utils :as utils]))


(def user-db-name (db-fixtures/db-name "client.phrase-test.user"))


(def device-db-name (db-fixtures/db-name "client.phrase-test.device"))


(use-fixtures :each (db-fixtures/db-fixture-multi [user-db-name device-db-name]))


(def ^:private clock
  {:clock/now-iso time/now-iso
   :clock/now-ms  time/now-ms})


(defn- with-test-dbs
  "Calls `f` with the test databases, under which `::learner` is the
   learner port over them."
  [f]
  (db-fixtures/with-test-dbs
   [user-db-name device-db-name]
   (^:async fn
    [[user-db device-db]]
    (with-redefs [utils/now-iso time/now-iso
                  utils/now-ms  time/now-ms]
      (let [dbs {:device/db device-db :user/db user-db}]
        (await (learner/with-learner dbs clock #(f (assoc dbs ::learner (:learner %) ::store (:store %))))))))))


(def ^:private phrase-id
  (vocabulary/vocab-id "auf jeden Fall"))


(defn- ^:async test-capabilities
  "What `add!` is handed: the learner port over `dbs`, with the example
   requests recorded into `example-requests` rather than queued. With
   `:active-id`, a
   collection «Поездка» under that id is stored and active; with
   `:existing-example`, an example of «auf jeden Fall» made in that
   collection is stored. It resolves once memory has what it stored."
  [dbs example-requests {:keys [active-id existing-example]}]
  (when active-id
    (await (db/insert (:user/db dbs) {:_id active-id :type "collection" :name "Поездка" :word-ids []})))
  (when existing-example
    (await (db/insert (:user/db dbs)
                      {:_id           (:id existing-example)
                       :type          "example"
                       :collection-id active-id
                       :word-id       phrase-id
                       :value         "Satz"
                       :translation   "фраза"})))
  (await (learner/caught-up dbs (::store dbs)))
  {:clock   clock
   :learner (assoc (::learner dbs)
                   :learner/active-collection
                   (fn [] (get-in ((:learner/memory (::learner dbs))) [:collections active-id]))
                   :learner/request-examples!
                   (fn [requests]
                     (swap! example-requests into requests)
                     (js/Promise.resolve nil)))})


(deftest add-creates-phrase-and-initial-review-and-asks-for-an-example
  (async-testing "`add!` creates a phrase doc, seeds a review, and queues the example fetch"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [example-requests (atom [])
            capabilities     (await (test-capabilities dbs example-requests {}))
            {:keys [word-id created?]} (await (sut/add! capabilities
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
        (is (= "phrase" (:kind (:word (first @example-requests))))))))))


(deftest add-queues-the-example-with-the-active-collection
  (async-testing "the queued fetch carries the active collection's id and name"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [example-requests (atom [])
            capabilities     (await (test-capabilities dbs example-requests {:active-id "collection-1"}))]
        (await (sut/add! capabilities "auf jeden Fall" "во всяком случае" :phrase))
        (is (= 1 (count @example-requests)))
        (let [{:keys [collection-id collection-name]} (first @example-requests)]
          (is (= "collection-1" collection-id))
          (is (= "Поездка" collection-name))))))))


(deftest re-adding-into-a-collection-that-has-an-example-queues-nothing
  (async-testing "the re-fetch rule is the one words already follow"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [example-requests (atom [])
            capabilities     (await (test-capabilities dbs
                                                       example-requests
                                                       {:active-id        "collection-1"
                                                        :existing-example {:id "example-1"}}))]
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
            capabilities     (await (test-capabilities dbs example-requests {}))]
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
            capabilities     (await (test-capabilities dbs example-requests {}))]
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
            result (await (sut/add! (await (test-capabilities dbs example-requests {}))
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
        (await (sut/add! (await (test-capabilities dbs example-requests {}))
                         "auf jeden Fall"
                         "во всяком\n   случае"
                         :phrase))
        (let [entries (await (db-queries/fetch-by-type (:user/db dbs) "vocab"))]
          (is (= [{:lang "ru" :value "во всяком случае"}]
                 (:translation (first entries))))))))))

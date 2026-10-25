(ns client.phrase-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.learner :as test-learner]
   [client.support.time :as time]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [domain.vocabulary :as vocabulary]
   [ports.learner :as learner]
   [use-cases.vocabulary :as sut]))


(def user-db-name (db-fixtures/db-name "client.phrase-test.user"))


(def device-db-name (db-fixtures/db-name "client.phrase-test.device"))


(use-fixtures :each (db-fixtures/db-fixture-multi [user-db-name device-db-name]))


(defn- with-test-dbs
  "Calls `f` with the test databases, under which `::learner` is the
   learner port over them."
  [f]
  (db-fixtures/with-test-dbs
   [user-db-name device-db-name]
   (^:async fn
    [[user-db device-db]]
    (let [dbs {:device/db device-db :user/db user-db}]
      (await (test-learner/with-learner dbs #(f (assoc dbs ::learner (:learner %) ::store (:store %)))))))))


(def ^:private phrase-id
  (vocabulary/vocab-id "auf jeden Fall"))


(defn- missing
  "The pairs memory is missing an example for, as the example backfill reads
   them, over the Learner `learner`."
  [learner]
  (test-learner/missing-in (learner/memory learner)))


(defn- ^:async learner-of
  "The Learner over `dbs`, for `add!`. With `:active-id`, a
   collection «Поездка» under that id is stored and active; with
   `:existing-example`, an example of «auf jeden Fall» made in that
   collection is stored. It resolves once memory has what it stored."
  [dbs {:keys [active-id existing-example]}]
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
  (await (test-learner/caught-up dbs (::store dbs)))
  (test-learner/with-active-collection
   (::learner dbs)
   (fn [] (get-in (learner/memory (::learner dbs)) [:collections active-id]))))


(deftest a-new-phrase-is-stored-with-a-first-review-and-is-missing-an-example
  (async-testing "the phrase, one review of it, and memory missing one example, as for a word"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [learner        (await (learner-of dbs {}))
            {:keys [word-id created?]} (await (sut/add! learner
                                                        time/world
                                                        {:translation "Извини, что я опоздал."
                                                         :value       "Entschuldigung, dass ich zu spät komme"}))
            [entry & more] (await (db-queries/fetch-by-type (:user/db dbs) "vocab"))
            reviews        (await (db-queries/fetch-by-type (:user/db dbs) "review"))]
        (is (true? created?))
        (is (empty? more))
        (is (= {:_id word-id :kind "phrase" :value "Entschuldigung, dass ich zu spät komme"}
               (select-keys entry [:_id :kind :value])))
        (is (= [word-id] (map :word-id reviews)))
        (is (= ["phrase"] (map (comp :kind :word) (missing learner)))))))))


(deftest re-adding-a-phrase-whose-collection-has-an-example-misses-nothing
  (async-testing "the re-fetch rule is the one words already follow"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [learner (await (learner-of dbs
                                       {:active-id        "collection-1"
                                        :existing-example {:id "example-1"}}))]
        (await (sut/add! learner time/world {:translation "во всяком случае" :value "auf jeden Fall"}))
        (await (sut/add! learner time/world {:translation "обязательно" :value "auf jeden Fall"}))
        (is (empty? (missing learner)) "the collection has its example already"))))))


(deftest add-duplicate-merges-translation-whole
  (async-testing "a duplicate phrase merges translations as whole entries"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [learner (await (learner-of dbs {}))]
        (await (sut/add! learner time/world {:translation "во всяком случае" :value "auf jeden Fall"}))
        (let [{:keys [created?]} (await (sut/add! learner
                                                  time/world
                                                  {:translation "обязательно, точно"
                                                   :value       "Auf jeden Fall"}))
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
      (let [learner (await (learner-of dbs {}))]
        ;; The dictionary gives `Guten Morgen` as a noun: a word, for all its space.
        (await (sut/add! learner time/world {:pos "noun" :translation "доброе утро" :value "Guten Morgen"}))
        (let [{:keys [created?]}
              (await
               (sut/add! learner time/world {:translation "доброго утра" :value "guten Morgen"}))
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
      (let [learner (await (learner-of dbs {}))
            result  (await (sut/add! learner time/world {:translation "   " :value "auf jeden Fall"}))]
        (is (= {:error :failure/empty-translations} result))
        (is (empty? (missing learner))))))))


(deftest add-keeps-one-translation-a-line-in-a-phrase
  (async-testing "each line of a phrase's translation is a translation of its own"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (sut/add! (await (learner-of dbs {}))
                       time/world
                       {:translation "во всяком случае\n   обязательно\n"
                        :value       "auf jeden Fall"}))
      (let [entries (await (db-queries/fetch-by-type (:user/db dbs) "vocab"))]
        (is (= [{:lang "ru" :value "во всяком случае"} {:lang "ru" :value "обязательно"}]
               (:translation (first entries)))))))))

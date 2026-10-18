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
      (let [learner (await (learner-of dbs {}))
            {:keys [word-id created?]} (await (sut/add! learner time/world
                                                        "Entschuldigung, dass ich zu spät komme"
                                                        "Извини, что я опоздал."
                                                        :phrase
                                                        nil))
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
        (await (sut/add! learner time/world "auf jeden Fall" "во всяком случае" :phrase nil))
        (await (sut/add! learner time/world "auf jeden Fall" "обязательно" :phrase nil))
        (is (empty? (missing learner)) "the collection has its example already"))))))


(deftest add-duplicate-merges-translation-whole
  (async-testing "a duplicate phrase merges translations as whole entries"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [learner (await (learner-of dbs {}))]
        (await (sut/add! learner time/world "auf jeden Fall" "во всяком случае" :phrase nil))
        (let [{:keys [created?]} (await (sut/add! learner time/world "Auf jeden Fall" "обязательно, точно" :phrase nil))
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
        (await (sut/add! learner time/world "Guten Morgen" "доброе утро" :word nil))
        (let [{:keys [created?]} (await (sut/add! learner time/world "guten Morgen" "доброго утра" :phrase nil))
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
            result  (await (sut/add! learner time/world "auf jeden Fall" "   " :phrase nil))]
        (is (= {:error :failure/empty-translations} result))
        (is (empty? (missing learner))))))))


(deftest add-collapses-line-breaks-in-a-phrase
  (async-testing "the multi-line field's breaks are visual only"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (sut/add! (await (learner-of dbs {}))
                       time/world
                       "auf jeden Fall"
                       "во всяком\n   случае"
                       :phrase
                       nil))
      (let [entries (await (db-queries/fetch-by-type (:user/db dbs) "vocab"))]
        (is (= [{:lang "ru" :value "во всяком случае"}]
               (:translation (first entries)))))))))

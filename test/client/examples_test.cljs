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
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [db.pouch :as dbs]))


(def test-device-db-name (db-fixtures/db-name "client.examples-test"))


(def test-user-db-name (db-fixtures/db-name "client.examples-test-user"))


(use-fixtures :each (db-fixtures/db-fixture-multi [test-device-db-name test-user-db-name]))


(defn- with-test-dbs
  [f]
  (db-fixtures/with-test-dbs
   [test-device-db-name test-user-db-name]
   (fn [[device-db user-db]]
     (f {:device/db device-db :user/db user-db}))))


;;
;; One request, and what its answer is
;;


(def ^:private hund
  {:collection-name nil :translations [] :word "Hund"})


(defn- ^:async answer-with
  "What `fetch-one` makes of the response `fake-fetch` gives, for
   `subject`."
  ([fake-fetch]
   (await (answer-with fake-fetch hund)))
  ([fake-fetch subject]
   (let [original js/fetch]
     (set! js/fetch fake-fetch)
     (try
       (await (sut/fetch-one subject (.-signal (js/AbortController.))))
       (finally
        (set! js/fetch original))))))


(deftest an-example-is-answered-as-one
  (async-testing "a success with a sentence and its translation"
    (is (= {:example {:translation "I have a dog" :value "Ich habe einen Hund"}}
           (await (answer-with (fetch-mocks/mock-fetch-success
                                {:translation "I have a dog" :value "Ich habe einen Hund"})))))))


(deftest every-status-is-one-kind
  (async-testing "the backend's statuses, the current ones and the honest ones of #523"
    (doseq [[status kind] [[400 :failure/rejected]
                           [404 :failure/rejected]
                           [422 :failure/rejected]
                           [401 :failure/unauthorized]
                           [403 :failure/unauthorized]
                           [408 :failure/unavailable]
                           [425 :failure/unavailable]
                           [429 :failure/throttled]
                           [500 :failure/unavailable]
                           [502 :failure/unavailable]
                           [503 :failure/unavailable]
                           [504 :failure/unavailable]]]
      (is (= {:failure kind :status status} (await (answer-with (fetch-mocks/mock-fetch-error status))))
          (str status)))))


(deftest a-failure-carries-the-server-s-message-and-its-wait
  (async-testing "the error body's message, and Retry-After in milliseconds"
    (is (= {:failure        :failure/throttled
            :message        "Too many requests"
            :retry-after-ms 2000
            :status         429}
           (await (answer-with
                   (fetch-mocks/mock-fetch-error-with-body 429 {:error "Too many requests"} {"Retry-After" "2"})))))
    (is (= {:failure        :failure/unavailable
            :message        "Examples are temporarily unavailable"
            :retry-after-ms 30000
            :status         503}
           (await (answer-with (fetch-mocks/mock-fetch-error-with-body
                                503
                                {:error "Examples are temporarily unavailable"}
                                {"Retry-After" "30"})))))
    (is (= {:failure :failure/throttled :status 429}
           (await (answer-with
                   (fetch-mocks/mock-fetch-error-with-body 429 nil {"Retry-After" "Wed, 21 Oct 2026 07:28:00 GMT"}))))
        "a wait given as a date is ignored")
    (is (= {:failure :failure/throttled :status 429}
           (await (answer-with
                   (fetch-mocks/mock-fetch-error-with-body 429 nil {"Retry-After" "30 seconds"}))))
        "only a whole number of seconds counts")))


(deftest a-success-that-is-no-example-is-an-invalid-response
  (async-testing "a body without a translation, and a body that is not JSON"
    (is (= {:failure :failure/invalid-response :status nil}
           (await (answer-with (fetch-mocks/mock-fetch-success {:value "Der Hund läuft"})))))
    (is (= {:failure :failure/invalid-response :status nil}
           (await (answer-with (fetch-mocks/mock-fetch-success-invalid-json)))))))


(deftest no-answer-is-a-network-failure
  (async-testing "a request that fails before any status, or a body that breaks off"
    (is (= {:failure :failure/network :message "Network error"}
           (await (answer-with (fetch-mocks/mock-fetch-network-error)))))
    (is (= {:failure :failure/network :message "network error"}
           (await (answer-with (fetch-mocks/mock-fetch-broken-body)))))))


(deftest a-subject-that-cannot-go-in-a-url-is-not-sent
  (async-testing "half an emoji in a theme name: nothing is fetched, and the kind says the subject is at fault"
    (let [fetched (atom 0)]
      (is (= {:failure :failure/invalid-subject}
             (await (answer-with (fn [& _] (swap! fetched inc) (js/Promise.reject (js/Error. "sent")))
                                 {:collection-name (str "Reise " (.charAt "😀" 0)) :translations [] :word "Hund"}))))
      (is (zero? @fetched)))))


(deftest an-aborted-request-is-aborted
  (async-testing "the signal the caller holds aborts the request, and nothing else is made of it"
    (let [original   js/fetch
          controller (js/AbortController.)]
      (set! js/fetch (fetch-mocks/mock-fetch-until-aborted))
      (try
        (let [answer (sut/fetch-one hund (.-signal controller))]
          (.abort controller)
          (is (= {:failure :failure/aborted} (await answer))))
        (finally
         (set! js/fetch original))))))


(deftest the-request-asks-the-question-with-the-session
  (async-testing "every Russian gloss as a repeated parameter, the collection as the context, the cookie included"
    (let [asked (atom nil)]
      (await (answer-with (fn [url options]
                            (reset! asked [url (.-credentials options) (some? (.-signal options))])
                            ((fetch-mocks/mock-fetch-success {:translation "t" :value "v"}) url))
                          {:collection-name "Park" :translations ["банк" "скамейка"] :word "Bank"}))
      (is
       (=
        ["/api/examples?word=Bank&translation=%D0%B1%D0%B0%D0%BD%D0%BA&translation=%D1%81%D0%BA%D0%B0%D0%BC%D0%B5%D0%B9%D0%BA%D0%B0&context=Park"
         "include"
         true]
        @asked)))))


;;
;; An example's identity, as every save writes it
;;


(def ^:private schlaeft
  {:structure   [{:dictionaryForm "Hund" :usedForm "Hund" :wordIndex 1}]
   :translation "Пёс спит."
   :value       "Der Hund schläft."})


(def ^:private bellt
  {:structure [] :translation "Пёс лает." :value "Der Hund bellt."})


(defn- stored
  "Stores `example` of the entry `word-id`, whose text is `word`, in the
   collection `collection-id`, as every save does: under the id its content
   gives it, kept when it is there already."
  [dbs word-id word collection-id example]
  (dbs/insert-all-if-absent dbs
                            documents/example-schema
                            [(documents/example-doc word-id word collection-id example)]))


(deftest an-example-is-stored-under-its-pair-and-its-content
  (async-testing "the id names the pair and a hash of the example follows"
    (await
     (with-test-dbs
      (^:async fn
       [dbs]
       (await (stored dbs "word-123" "Hund" nil bellt))
       (await (stored dbs "word-123" "Hund" "coll-1" bellt))
       (let [docs  (await (db-queries/fetch-examples (:user/db dbs)))
             saved (first docs)]
         (is (= 2 (count docs)))
         (is (re-matches #"example:word-123::[0-9a-f]{12}" (:_id saved))
             "a pair in no collection leaves its part empty")
         (is (re-matches #"example:word-123:coll-1:[0-9a-f]{12}" (:_id (second docs))))
         (is (= (subs (:_id (first docs)) 18) (subs (:_id (second docs)) 24))
             "one example has one hash, whatever its collection")
         (is (= "example" (:type saved)))
         (is (= "word-123" (:word-id saved)))
         (is (= "Hund" (:word saved)))
         (is (= "Der Hund bellt." (:value saved)))
         (is (not (contains? saved :created-at)) "nothing that depends on the device or the time")))))))


(deftest an-example-s-id-and-revision-are-pinned
  (async-testing
    "a change to the hash, the key order or the body's fields changes these, and every device must agree on them"
    (await
     (with-test-dbs
      (^:async fn
       [dbs]
       (await (stored dbs "vocab:hund" "Hund" "collection:1-87155332" schlaeft))
       (let [[stored] (await (db-queries/fetch-examples (:user/db dbs)))]
         (is (= "example:vocab:hund:collection:1-87155332:25b7d6a32783" (:_id stored)))
         (is (= "1-b8443dc633c55a5f1142d42e88eebfda" (:_rev stored)))))))))


(deftest every-distinct-example-is-kept-and-one-of-each
  (async-testing "another example of a stored pair is kept beside it; the same example again writes nothing"
    (await
     (with-test-dbs
      (^:async fn
       [dbs]
       (await (stored dbs "word-123" "Hund" "coll-1" schlaeft))
       (await (stored dbs "word-123" "Hund" "coll-1" bellt))
       (await (stored dbs "word-123" "Hund" "coll-1" schlaeft))
       (let [docs (await (db-queries/fetch-examples (:user/db dbs)))]
         (is (= #{"Der Hund schläft." "Der Hund bellt."} (set (map :value docs))))
         (is (every? #(= "1" (first (.split (:_rev %) "-"))) docs) "no second revision")))))))


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
  "Stores `example` on the device whose user-db is `db`, as a save of a
   fetched one does."
  [example]
  (fn [db]
    (stored {:user/db db} "vocab:hund" "Hund" "coll-1" example)))


(defn- moved
  "Stores `kept` on the device whose user-db is `db`, as the move does."
  [kept]
  (fn [db]
    (stored {:user/db db} "vocab:hund" "Hund" "coll-1" kept)))


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

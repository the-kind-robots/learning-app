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


(def example-structure
  [{:usedForm       "Hund"
    :dictionaryForm "der Hund"
    :translation    "собака"
    :wordIndex      1}])


(defn- example-payload
  []
  {:value       "Ich habe einen Hund"
   :translation "У меня есть собака"
   :structure   example-structure})


;; =============================================================================
;; Unit Tests: fetch-one
;; =============================================================================


(deftest fetch-one-returns-parsed-json-on-success
  (async-testing "`fetch-one` returns parsed JSON on success"
    (let [example        (example-payload)
          original-fetch js/fetch]
      (set! js/fetch (fetch-mocks/mock-fetch-success example))
      (p/finally
        (p/let [result (sut/fetch-one "Hund")]
          (is (= "Ich habe einen Hund" (:value result)))
          (is (= "У меня есть собака" (:translation result)))
          (is (= example-structure (:structure result))))
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


(deftest fetch-one-accepts-tolerant-example-payload
  (async-testing "`fetch-one` accepts payloads without fields this client can render"
    (let [original-fetch js/fetch]
      (set! js/fetch (fetch-mocks/mock-fetch-success {:value "Der Hund läuft"
                                                       :new-rich-field {:level 2}}))
      (p/finally
        (p/let [example (sut/fetch-one "Hund")]
          (is (= "Der Hund läuft" (:value example)))
          (is (= {:level 2} (:new-rich-field example))))
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
        (let [example {:value "Der Hund" :translation "The dog" :structure example-structure
                       :difficulty 2 :source "backend-owned"}
              dbs     {:device/db db}]
          (p/do
            (sut/save-example! dbs "word-123" "Hund" example)
            (p/let [docs (db-queries/fetch-examples db)]
              (let [saved (first docs)]
                (is (= 1 (count docs)))
                (is (= "example" (:type saved)))
                (is (= "word-123" (:word-id saved)))
                (is (= "Hund" (:word saved)))
                (is (= "Der Hund" (:value saved)))
                (is (= "fetched" (:source saved)))
                (is (= example-structure (:structure saved)))
                (is (= 2 (:difficulty saved)))
                (is (some? (:created-at saved)))
                (is (= (:created-at saved) (:modified-at saved)))))))))))


(deftest save-example-stores-unusable-but-parseable-map
  (async-testing "`save-example!` stores parseable fetched docs even if not usable by this client"
    (with-test-db
      (fn [db]
        (let [example {:value "Der Hund läuft."
                       :translation "The dog runs."
                       :future-shape true}
              dbs     {:device/db db}]
          (p/let [insert-result (sut/save-example! dbs "word-123" "Hund" example)
                  saved         (db/get db (:id insert-result))]
              (is (= "fetched" (:source saved)))
              (is (= true (:future-shape saved)))
              (is (nil? (:structure saved)))))))))


(deftest save-example-throws-on-non-map-payload
  (is (thrown-with-msg? js/Error
                        #"expected map"
                        (sut/save-example! nil "word-123" "Hund" ["not" "map"]))))


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


;; =============================================================================
;; Unit Tests: list / state
;; =============================================================================


(deftest list-returns-all-examples-for-words
  (async-testing "`list` returns all matching docs without lesson selection"
    (with-test-db
      (fn [db]
        (p/do
          (db/insert db {:_id "fetched-ok" :type "example" :word-id "w1" :word "Hund"
                         :value "Der Hund bellt." :translation "Собака лает."
                         :source "fetched" :structure example-structure})
          (db/insert db {:_id "fetched-bad" :type "example" :word-id "w1" :word "Hund"
                         :value "Bad" :translation "Плохо" :source "fetched"})
          (db/insert db {:_id "user-ok" :type "example" :word-id "w2" :word "Katze"
                         :value "Meine Katze schläft." :translation "Моя кошка спит."
                         :source "user"})
          (p/let [examples (sut/list {:device/db db} ["w1" "w2"])]
            (is (= #{"fetched-ok" "fetched-bad" "user-ok"} (set (map :_id examples))))))))))


(deftest states-report-example-state-by-word
  (async-testing "`states` derives ready/fetching/failed/no-example for multiple words"
    (with-test-dbs
      (fn [dbs]
        (let [db (:device/db dbs)]
          (p/do
            (db/insert db {:_id "ready-example" :type "example" :word-id "ready" :word "Hund"
                           :value "Der Hund bellt." :translation "Собака лает."
                           :source "fetched" :structure example-structure})
            (db/insert db {:_id "active-task" :type "task" :task-type "example-fetch"
                           :data {:word-id "fetching"} :attempts 0})
            (db/insert db {:_id "failed-task" :type "task" :task-type "example-fetch"
                           :data {:word-id "failed"} :status "failed"
                           :last-error "No credits" :attempts 5})
            (p/let [states (sut/states dbs ["ready" "fetching" "failed" "none"])]
              (is (= :ready (get-in states ["ready" :state])))
              (is (= :fetching (get-in states ["fetching" :state])))
              (is (= :failed (get-in states ["failed" :state])))
              (is (= "No credits" (get-in states ["failed" :last-error])))
              (is (= :no-example (get-in states ["none" :state]))))))))))


(deftest state-returns-single-word-state
  (async-testing "`state` reuses `states` and returns one state map"
    (with-test-dbs
      (fn [dbs]
        (let [db (:device/db dbs)]
          (p/do
            (db/insert db {:_id "ready-example" :type "example" :word-id "ready" :word "Hund"
                           :value "Der Hund bellt." :translation "Собака лает."
                           :source "fetched" :structure example-structure})
            (p/let [state (sut/state dbs "ready")]
              (is (= {:state :ready} state)))))))))


(deftest add-user-example-stores-unstructured-doc
  (async-testing "`add!` stores user example exactly as entered without structure"
    (with-test-dbs
      (fn [dbs]
        (p/do
          (db/insert (:user/db dbs) {:_id "word-1"
                                     :type "vocab"
                                     :value "das Haus"
                                     :translation [{:lang "ru" :value "дом"}]})
          (p/let [added (sut/add! dbs
                                   "word-1"
                                   "  Mein Haus ist alt.  "
                                   "  Мой дом старый.  ")
                  examples (db-queries/fetch-examples (:device/db dbs))
                  tasks    (db-queries/fetch-by-type (:device/db dbs) "task")]
            (is (= 1 (count examples)))
            (is (= (:_id added) (:_id (first examples))))
            (is (= "user" (:source added)))
            (is (= "das Haus" (:word added)))
            (is (= "  Mein Haus ist alt.  " (:value added)))
            (is (= "  Мой дом старый.  " (:translation added)))
            (is (nil? (:structure added)))
            (is (empty? tasks))))))))


(deftest add-user-example-coexists-with-fetched-example
  (async-testing "`add!` adds a separate user example without replacing fetched example"
    (with-test-dbs
      (fn [dbs]
        (p/do
          (db/insert (:user/db dbs) {:_id "word-1"
                                     :type "vocab"
                                     :value "der Hund"
                                     :translation [{:lang "ru" :value "собака"}]})
          (db/insert (:device/db dbs) {:_id "fetched-1"
                                       :type "example"
                                       :word-id "word-1"
                                       :word "der Hund"
                                       :value "Der Hund bellt."
                                       :translation "Собака лает."
                                       :source "fetched"
                                       :structure example-structure})
          (sut/add! dbs "word-1" "Mein Hund schläft." "Моя собака спит.")
          (p/let [examples (db-queries/fetch-examples (:device/db dbs))]
            (is (= #{"fetched" "user"} (set (map :source examples))))
            (is (= #{"Der Hund bellt." "Mein Hund schläft."}
                   (set (map :value examples))))))))))


(deftest add-user-example-returns-not-found-when-word-missing
  (async-testing "`add!` returns not-found when parent word does not exist"
    (with-test-dbs
      (fn [dbs]
        (p/let [added (sut/add! dbs "missing" "Hallo." "Привет.")]
          (is (= {:error :not-found} added)))))))


(deftest add-user-example-rejects-invalid-fields
  (async-testing "`add!` returns invalid when value or translation is blank"
    (with-test-dbs
      (fn [dbs]
        (p/let [added (sut/add! dbs "word-1" "" "Привет.")]
          (is (= {:error :invalid} added)))))))


(deftest update-user-example-updates-only-user-doc
  (async-testing "`update-user!` updates user example and rejects fetched example"
    (with-test-dbs
      (fn [dbs]
        (let [device-db (:device/db dbs)]
          (p/do
            (db/insert device-db {:_id "user-example"
                                  :type "example"
                                  :word-id "word-1"
                                  :word "der Hund"
                                  :value "Alt."
                                  :translation "Старый."
                                  :source "user"
                                  :created-at "2026-01-01T00:00:00.000Z"
                                  :modified-at "2026-01-01T00:00:00.000Z"})
            (db/insert device-db {:_id "fetched-example"
                                  :type "example"
                                  :word-id "word-1"
                                  :word "der Hund"
                                  :value "Der Hund bellt."
                                  :translation "Собака лает."
                                  :source "fetched"
                                  :structure example-structure})
            (p/let [updated (sut/update! dbs "user-example" "Neu." "Новый.")
                    fetched (sut/update! dbs "fetched-example" "Kaputt." "Сломано.")
                    invalid (sut/update! dbs "user-example" "" "Пусто.")
                    saved   (db/get device-db "user-example")]
              (is (= "Neu." (:value updated)))
              (is (= "Новый." (:translation saved)))
              (is (= "user" (:source saved)))
              (is (nil? (:structure saved)))
              (is (= :not-user-example (:error fetched)))
              (is (= {:error :invalid} invalid)))))))))


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


(deftest delete-does-not-create-replacement-work
  (async-testing "`delete!` removes example without scheduling replacement work"
    (with-test-dbs
      (fn [dbs]
        (p/do
          (db/insert (:device/db dbs) {:_id "example-1"
                                       :type "example"
                                       :word-id "word-1"
                                       :word "der Hund"
                                       :value "Der Hund bellt."
                                       :translation "Собака лает."
                                       :source "fetched"
                                       :structure example-structure})
          (p/let [result   (sut/delete! dbs "example-1")
                  examples (db-queries/fetch-examples (:device/db dbs))
                  tasks    (db-queries/fetch-by-type (:device/db dbs) "task")]
            (is (= {:deleted? true :word-id "word-1"} result))
            (is (empty? examples))
            (is (empty? tasks))))))))


(deftest fetch-creates-or-retries-task
  (async-testing "`fetch!` creates missing task and resets failed task"
    (with-test-dbs
     (fn [dbs]
       (p/with-redefs [tasks/flush! (constantly nil)]
         (let [failed-id (tasks/id-for "example-fetch" {:word-id "failed-word"})]
           (p/do
             (db/insert (:device/db dbs) {:_id "failed-task"
                                          :type "task"
                                          :task-type "other-task"
                                          :data {:word-id "keep-me"}})
             (db/insert (:device/db dbs) {:_id failed-id
                                          :type "task"
                                          :task-type "example-fetch"
                                          :data {:word-id "failed-word"}
                                          :status "failed"
                                          :attempts 5
                                          :failure-reason "retry-limit-reached"
                                          :last-error "No credits"
                                          :failed-at "2026-01-01T00:00:00.000Z"})
             (sut/fetch! dbs "new-word")
             (p/let [retried (sut/fetch! dbs "failed-word")
                     tasks   (db-queries/fetch-by-type (:device/db dbs) "task")]
               (is (= 3 (count tasks)))
               (is (= (tasks/id-for "example-fetch" {:word-id "new-word"})
                      (:_id (first (filter #(= {:word-id "new-word"} (:data %)) tasks)))))
               (is (= {:state :fetching} retried))
               (let [retried-task (first (filter #(= failed-id (:_id %)) tasks))]
                 (is (nil? (:status retried-task)))
                 (is (= 0 (:attempts retried-task)))
                 (is (nil? (:last-error retried-task))))))))))))


(deftest fetch-skips-ready-example
  (async-testing "`fetch!` does not create replacement work when example is ready"
    (with-test-dbs
      (fn [dbs]
        (p/with-redefs [tasks/flush! (constantly nil)]
          (p/do
            (db/insert (:device/db dbs) {:_id "ready-example"
                                         :type "example"
                                         :word-id "word-1"
                                         :word "der Hund"
                                         :value "Der Hund bellt."
                                         :translation "Собака лает."
                                         :source "fetched"
                                         :structure example-structure})
            (p/let [result (sut/fetch! dbs "word-1")
                    tasks  (db-queries/fetch-by-type (:device/db dbs) "task")]
              (is (= {:state :ready} result))
              (is (empty? tasks)))))))))


(deftest fetch-runs-when-only-user-example-is-ready
  (async-testing "`fetch!` still creates fetched work when word only has user examples"
    (with-test-dbs
      (fn [dbs]
        (p/with-redefs [tasks/flush! (constantly nil)]
          (p/do
            (db/insert (:device/db dbs) {:_id "user-example"
                                         :type "example"
                                         :word-id "word-1"
                                         :word "der Hund"
                                         :value "Mein Hund schläft."
                                         :translation "Моя собака спит."
                                         :source "user"})
            (p/let [result (sut/fetch! dbs "word-1")
                    tasks  (db-queries/fetch-by-type (:device/db dbs) "task")]
              (is (= {:state :fetching} result))
              (is (= 1 (count tasks)))
              (is (= {:word-id "word-1"} (:data (first tasks)))))))))))


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
    (let [example        {:value "Der Hund läuft" :translation "The dog runs" :structure example-structure}
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
                 (let [saved (first examples)]
                   (is (= 1 (count examples)))
                   (is (= "Der Hund läuft" (:value saved)))
                   (is (= "fetched" (:source saved)))
                   (is (= (:created-at saved) (:modified-at saved)))))))))
        (fn []
          (set! js/fetch original-fetch))))))


(deftest task-handler-sends-all-confirmed-translations
  (async-testing "task handler sends every confirmed Russian translation as a repeated query param"
    (let [example        {:value "Wir sitzen auf einer Bank im Park."
                          :translation "Мы сидим на скамейке в парке."
                          :structure example-structure}
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


(deftest task-handler-stores-unusable-fetched-payload
  (async-testing "task handler stores fetched payload even if this client cannot use it"
    (let [original-fetch js/fetch]
      (set! js/fetch (fetch-mocks/mock-fetch-success {:value "Der Hund läuft."
                                                       :translation "Собака бежит"}))
      (p/finally
        (with-test-dbs
         (fn [dbs]
           (p/do
             (db/insert (:user/db dbs) {:_id "word-123" :type "vocab" :value "Hund"})
             (p/let [result (tasks/execute-task
                             {:task-type "example-fetch"
                              :data      {:word-id "word-123"}}
                             dbs)]
               (is (true? result))
               (p/let [examples (db-queries/fetch-examples (:device/db dbs))]
                 (is (= 1 (count examples)))
                 (is (= "fetched" (:source (first examples))))
                 (is (false? (sut/usable? (first examples)))))))))
        (fn []
          (set! js/fetch original-fetch))))))

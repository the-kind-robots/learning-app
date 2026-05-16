(ns client.lesson-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.db-seed :as db-seed]
   [client.support.time :as time]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [domain.lesson :as domain]
   [repo.examples :as examples]
   [use-cases.lesson :as sut]
   [utils :as utils]))


(def user-db-name (db-fixtures/db-name "client.lesson-test.user"))


(def device-db-name (db-fixtures/db-name "client.lesson-test.device"))


(use-fixtures :each (db-fixtures/db-fixture-multi [user-db-name device-db-name]))


(defn- with-test-dbs
  [f]
  (db-fixtures/with-test-dbs
   [user-db-name device-db-name]
   (^:async fn [[user-db device-db]]
     (with-redefs [utils/now-iso time/now-iso
                   utils/now-ms  time/now-ms]
       (await (f {:user/db user-db :device/db device-db}))))))


(deftest start-creates-lesson-when-words-available
  (async-testing "`start!` creates lesson when vocabulary is not empty"
    (with-test-dbs
     (^:async fn [dbs]
       (await (db-seed/seed-vocabulary!
               (:user/db dbs)
               [{:_id "word-1" :value "der Hund" :translation "пёс"}
                {:_id "word-2" :value "die Katze" :translation "кошка"}]))
       (let [result (await (sut/start! dbs {:trial-selector :first}))]
         (is (some? (:lesson-state result)))
         (is (nil? (:error result)))
         (let [lesson (:lesson-state result)]
           (is (= "lesson" (:_id lesson)))
           (is (= "lesson" (:type lesson)))
           (is (= 2 (count (:trials lesson))))
           (is (some? (:current-trial lesson)))
           (is (some? (:_rev lesson)))))))))


(deftest start-returns-error-when-no-words
  (async-testing "`start!` errors when vocabulary is empty"
    (with-test-dbs
     (^:async fn [dbs]
       (let [result (await (sut/start! dbs))]
         (is (= :no-words-available (:error result)))
         (is (nil? (:lesson-state result))))))))


(deftest start-returns-error-when-db-insert-fails
  (async-testing "`start!` errors on db failure"
    (with-test-dbs
     (^:async fn [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}]))
       (with-redefs [db/insert (fn [_ _] (js/Promise.reject (ex-info "DB error" {})))]
         (let [result (await (sut/start! dbs {:trial-selector :first}))]
           (is (= :lesson-start-failed (:error result)))
           (is (nil? (:lesson-state result)))))))))


(deftest start-includes-example-trials
  (async-testing "`start!` includes example trials"
    (with-test-dbs
     (^:async fn [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}]))
       (await (db-seed/seed-examples! (:device/db dbs)
                                      [{:_id         "example-1"
                                        :word-id     "word-1"
                                        :word        "der Hund"
                                        :value       "Der Hund schlaeft."
                                        :translation "Пёс спит"}]))
       (let [result (await (sut/start! dbs {:trial-selector :first}))
             trials (:trials (:lesson-state result))]
         (is (= 2 (count trials)))
         (is (= 1 (count (filter #(= "word" (:type %)) trials))))
         (is (= 1 (count (filter #(= "example" (:type %)) trials))))
         (is (= [true]
                (->> trials
                     (filter #(= "example" (:type %)))
                     (map :locked?)
                     vec))))))))


(deftest restart-replaces-existing-lesson-with-fresh-session
  (async-testing "`restart!` discards persisted lesson state and starts fresh"
    (with-test-dbs
     (^:async fn [dbs]
       (await (db-seed/seed-vocabulary!
               (:user/db dbs)
               [{:_id "word-1" :value "der Hund" :translation "пёс"}
                {:_id "word-2" :value "die Katze" :translation "кошка"}]))
       (let [start-result (await (sut/start! dbs {:trial-selector :first}))
             first-trial  (domain/current-trial (:lesson-state start-result))]
         (await (sut/check-answer! dbs (:answer first-trial)))
         (let [restarted    (await (sut/restart! dbs {:trial-selector :first}))
               lessons      (await (db-queries/fetch-by-type (:device/db dbs) "lesson"))
               lesson-state (:lesson-state restarted)]
           (is (some? lesson-state))
           (is (nil? (:error restarted)))
           (is (= 1 (count lessons)))
           (is (= (count (:trials lesson-state))
                  (count (:remaining-trials lesson-state))))
           (is (nil? (domain/last-result lesson-state)))))))))


(deftest restart-returns-error-when-no-words
  (async-testing "`restart!` propagates start errors"
    (with-test-dbs
     (^:async fn [dbs]
       (let [result (await (sut/restart! dbs))]
         (is (= :no-words-available (:error result)))
         (is (nil? (:lesson-state result))))))))


(deftest check-answer-correct-word-trial
  (async-testing "`check-answer!` creates review on correct answer"
    (with-test-dbs
     (^:async fn [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}]))
       (await (sut/start! dbs {:trial-selector :first}))
       (let [result      (await (sut/check-answer! dbs "der Hund"))
             last-result (domain/last-result (:lesson-state result))]
         (is (some? (:lesson-state result)))
         (is (nil? (:error result)))
         (is (true? (:correct? last-result)))
         (is (= "der Hund" (:answer last-result))))
       (let [reviews (await (db-queries/fetch-by-type (:user/db dbs) "review"))]
         (is (= 2 (count reviews)))
         (is (= ["word-1"]
                (->> reviews
                     (map :word-id)
                     (remove nil?)
                     distinct
                     vec))))))))


(deftest check-answer-wrong-word-trial
  (async-testing "`check-answer!` creates review on wrong answer"
    (with-test-dbs
     (^:async fn [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}]))
       (await (sut/start! dbs {:trial-selector :first}))
       (let [result      (await (sut/check-answer! dbs "wrong answer"))
             last-result (domain/last-result (:lesson-state result))]
         (is (some? (:lesson-state result)))
         (is (false? (:correct? last-result))))
       (let [reviews (await (db-queries/fetch-by-type (:user/db dbs) "review"))]
         (is (= 2 (count reviews))))))))


(deftest check-answer-clamps-very-long-input
  (async-testing "`check-answer!` limits oversized answers"
    (with-test-dbs
     (^:async fn [dbs]
       (let [long-answer (apply str (repeat 1400 "x"))]
         (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}]))
         (await (sut/start! dbs {:trial-selector :first}))
         (let [result (await (sut/check-answer! dbs long-answer))
               answer (-> result :lesson-state domain/last-result :answer)]
           (is (= 1000 (count answer)))
           (is (= (subs long-answer 0 1000) answer))))))))


(deftest check-answer-example-trial-no-review
  (async-testing "`check-answer!` skips review for example trials"
    (with-test-dbs
     (^:async fn [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}]))
       (await (db-seed/seed-examples! (:device/db dbs)
                                      [{:_id         "example-1"
                                        :word-id     "word-1"
                                        :word        "der Hund"
                                        :value       "Der Hund schlaeft."
                                        :translation "Пёс спит."}]))
       (await (sut/start! dbs {:trial-selector :first}))
       (await (sut/check-answer! dbs "der Hund"))
       (await (sut/advance! dbs))
       (let [initial-reviews (await (db-queries/fetch-by-type (:user/db dbs) "review"))]
         (await (sut/check-answer! dbs "Der Hund schlaeft."))
         (let [final-reviews (await (db-queries/fetch-by-type (:user/db dbs) "review"))]
           (is (= (count initial-reviews) (count final-reviews)))))))))


(deftest check-answer-correct-word-unlocks-example-trial
  (async-testing "`check-answer!` unlocks examples after successful word answer"
    (with-test-dbs
     (^:async fn [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}]))
       (await (db-seed/seed-examples! (:device/db dbs)
                                      [{:_id         "example-1"
                                        :word-id     "word-1"
                                        :word        "der Hund"
                                        :value       "Der Hund schlaeft."
                                        :translation "Пёс спит."}]))
       (await (sut/start! dbs {:trial-selector :first}))
       (let [result (await (sut/check-answer! dbs "der Hund"))
             trial  (->> (:remaining-trials (:lesson-state result))
                         (filter domain/example-trial?)
                         first)]
         (is (some? trial))
         (is (false? (:locked? trial))))))))


(deftest token-info-returns-unknown-for-missing-word
  (async-testing "`token-info` reports unknown word when dictionary form not in vocabulary"
    (with-test-dbs
     (^:async fn [dbs]
       (let [info (await (sut/token-info dbs "die Seele" "душа"))]
         (is (= :unknown-word (:state info)))
         (is (= "die Seele" (:dictionary-form info)))
         (is (= "душа" (:translation info))))))))


(deftest token-info-returns-known-when-translation-in-set
  (async-testing "`token-info` reports known-with-translation when the gloss is already on the word"
    (with-test-dbs
     (^:async fn [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "w" :value "die Seele" :translation "душа"}]))
       (let [info (await (sut/token-info dbs "die Seele" "душа"))]
         (is (= :known-with-translation (:state info))))))))


(deftest token-info-returns-missing-when-translation-not-in-set
  (async-testing "`token-info` reports known-missing-translation when the gloss is new for an existing word"
    (with-test-dbs
     (^:async fn [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "w" :value "die Bank" :translation "банк"}]))
       (let [info (await (sut/token-info dbs "die Bank" "скамейка"))]
         (is (= :known-missing-translation (:state info))))))))


(deftest add-word-from-structure-appends-translation-to-existing-word
  (async-testing "`add-word-from-structure!` merges a new translation into an existing word's set"
    (with-test-dbs
     (^:async fn [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "w" :value "die Bank" :translation "банк"}]))
       (let [fetch-tasks (atom [])]
         (with-redefs [examples/create-fetch-task! (fn [word-id]
                                                     (swap! fetch-tasks conj word-id)
                                                     (js/Promise.resolve nil))]
           (let [result (await (sut/add-word-from-structure! dbs "die Bank" "скамейка"))
                 word   (await (db/get (:user/db dbs) "w"))]
             (is (false? (:created? result)))
             (is (empty? @fetch-tasks))
             (is (= ["банк" "скамейка"]
                    (mapv :value (:translation word)))))))))))


(deftest add-word-from-structure-creates-vocab-and-fetch-task
  (async-testing "`add-word-from-structure!` adds vocab and schedules fetch task"
    (with-test-dbs
     (^:async fn [dbs]
       (let [fetch-tasks (atom [])]
         (with-redefs [examples/create-fetch-task! (fn [word-id]
                                                     (swap! fetch-tasks conj word-id)
                                                     (js/Promise.resolve nil))]
           (let [result (await (sut/add-word-from-structure! dbs "die Seele" "душа"))
                 words  (await (db-queries/fetch-by-type (:user/db dbs) "vocab"))]
             (is (true? (:created? result)))
             (is (= ["die Seele"] (mapv :value words)))
             (is (= [(:word-id result)] @fetch-tasks)))))))))


(deftest add-word-from-structure-skips-fetch-task-for-duplicate
  (async-testing "`add-word-from-structure!` skips fetch task for duplicate"
    (with-test-dbs
     (^:async fn [dbs]
       (let [fetch-tasks (atom [])]
         (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "die Seele" :translation "душа"}]))
         (with-redefs [examples/create-fetch-task! (fn [word-id]
                                                     (swap! fetch-tasks conj word-id)
                                                     (js/Promise.resolve nil))]
           (let [result (await (sut/add-word-from-structure! dbs "die Seele" "душа"))
                 words  (await (db-queries/fetch-by-type (:user/db dbs) "vocab"))]
             (is (false? (:created? result)))
             (is (= 1 (count words)))
             (is (empty? @fetch-tasks)))))))))


(deftest check-answer-returns-error-when-no-lesson
  (async-testing "`check-answer!` errors when no lesson"
    (with-test-dbs
     (^:async fn [dbs]
       (let [result (await (sut/check-answer! dbs "any answer"))]
         (is (= :lesson-not-found (:error result)))
         (is (nil? (:lesson-state result))))))))


(deftest check-answer-handles-db-insert-failure
  (async-testing "`check-answer!` errors on db failure"
    (with-test-dbs
     (^:async fn [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}]))
       (await (sut/start! dbs {:trial-selector :first}))
       (with-redefs [db/insert (fn [_ _] (js/Promise.reject (ex-info "DB error" {})))]
         (let [result      (await (sut/check-answer! dbs "der Hund"))
               last-result (domain/last-result (:lesson-state result))]
           (is (= :lesson-save-failed (:error result)))
           (is (some? (:lesson-state result)))
           (is (true? (:correct? last-result)))))))))


(deftest advance-selects-next-trial
  (async-testing "`advance!` moves to next trial"
    (with-test-dbs
     (^:async fn [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs)
                                        [{:_id "word-1" :value "der Hund" :translation "пёс"}
                                         {:_id "word-2" :value "die Katze" :translation "cat"}]))
       (let [start-result (await (sut/start! dbs {:trial-selector :first}))
             first-trial  (domain/current-trial (:lesson-state start-result))]
         (await (sut/check-answer! dbs (:answer first-trial)))
         (let [advance-result (await (sut/advance! dbs))
               next-trial     (domain/current-trial (:lesson-state advance-result))]
           (is (some? (:lesson-state advance-result)))
           (is (nil? (:error advance-result)))
           (is (some? next-trial))
           (is (not= first-trial next-trial))))))))


(deftest advance-returns-nil-when-finished
  (async-testing "`advance!` returns nil when finished"
    (with-test-dbs
     (^:async fn [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}]))
       (await (sut/start! dbs {:trial-selector :first}))
       (await (sut/check-answer! dbs "der Hund"))
       (let [result (await (sut/advance! dbs))]
         (is (nil? result)))))))


(deftest advance-returns-error-when-no-lesson
  (async-testing "`advance!` errors when no lesson"
    (with-test-dbs
     (^:async fn [dbs]
       (let [result (await (sut/advance! dbs))]
         (is (= :lesson-not-found (:error result))))))))


(deftest advance-handles-db-insert-failure
  (async-testing "`advance!` errors on db failure"
    (with-test-dbs
     (^:async fn [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs)
                                        [{:_id "word-1" :value "der Hund" :translation "пёс"}
                                         {:_id "word-2" :value "die Katze" :translation "cat"}]))
       (await (sut/start! dbs {:trial-selector :first}))
       (await (sut/check-answer! dbs "der Hund"))
       (with-redefs [db/insert (fn [_ _] (js/Promise.reject (ex-info "DB error" {})))]
         (let [result (await (sut/advance! dbs))]
           (is (= :lesson-save-failed (:error result)))))))))


(deftest finish-removes-lesson
  (async-testing "`finish!` removes lesson from db"
    (with-test-dbs
     (^:async fn [dbs]
       (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}]))
       (await (sut/start! dbs {:trial-selector :first}))
       (let [lessons-before (await (db-queries/fetch-by-type (:device/db dbs) "lesson"))]
         (await (sut/finish! dbs))
         (let [lessons-after (await (db-queries/fetch-by-type (:device/db dbs) "lesson"))]
           (is (= 1 (count lessons-before)))
           (is (= 0 (count lessons-after)))))))))


(deftest finish-is-noop-when-no-lesson
  (async-testing "`finish!` no-op when no lesson"
    (with-test-dbs
     (^:async fn [dbs]
       (let [result (await (sut/finish! dbs))]
         (is (nil? result)))))))


(deftest full-lesson-flow-completes-successfully
  (async-testing "full lesson flow: start → answer → finish"
    (with-test-dbs
     (^:async fn [dbs]
       (await (db-seed/seed-vocabulary!
               (:user/db dbs)
               [{:_id "word-1" :value "der Hund" :translation "пёс"}
                {:_id "word-2" :value "die Katze" :translation "cat"}]))
       (let [start-result (await (sut/start! dbs {:trial-selector :first}))]
         (is (some? (:lesson-state start-result)))
         (let [first-trial (domain/current-trial (:lesson-state start-result))
               check1      (await (sut/check-answer! dbs (:answer first-trial)))]
           (is (true? (:correct? (domain/last-result (:lesson-state check1)))))
           (let [advance1 (await (sut/advance! dbs))]
             (is (some? (:lesson-state advance1)))
             (let [second-trial (domain/current-trial (:lesson-state advance1))
                   check2       (await (sut/check-answer! dbs (:answer second-trial)))]
               (is (true? (:correct? (domain/last-result (:lesson-state check2)))))
               (is (domain/finished? (:lesson-state check2)))
               (let [advance2 (await (sut/advance! dbs))]
                 (is (nil? advance2)))
               (await (sut/finish! dbs))
               (let [lessons (await (db-queries/fetch-by-type (:device/db dbs) "lesson"))]
                 (is (empty? lessons)))))))))))

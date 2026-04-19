(ns client.lesson-test
  (:require
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.db-seed :as db-seed]
   [client.support.time :as time]
   [db :as db]
   [domain.lesson :as domain]
   [lesson :as sut]
   [promesa.core :as p]
   [utils :as utils])
  (:require-macros
   [client.support.test :refer [async-testing]]))


;; =============================================================================
;; Test Helpers
;; =============================================================================


(def user-db-name (db-fixtures/db-name "client.lesson-test.user"))


(def device-db-name (db-fixtures/db-name "client.lesson-test.device"))


(use-fixtures :each (db-fixtures/db-fixture-multi [user-db-name device-db-name]))


(defn- with-test-dbs
  "Sets up test DBs and time utilities, calls (f dbs)."
  [f]
  (db-fixtures/with-test-dbs
   [user-db-name device-db-name]
   (fn [[user-db device-db]]
     (p/with-redefs [utils/now-iso time/now-iso
                     utils/now-ms  time/now-ms]
       (f {:user/db user-db :device/db device-db})))))


;; =============================================================================
;; start!
;; =============================================================================


(deftest start-creates-lesson-when-words-available
  (async-testing "`start!` creates lesson when vocabulary is not empty"
    (with-test-dbs
     (fn [dbs]
       (p/do
         (db-seed/seed-vocabulary!
          (:user/db dbs)
          [{:_id "word-1" :value "der Hund" :translation "пёс"}
           {:_id "word-2" :value "die Katze" :translation "кошка"}])
         (p/let [result (sut/start! dbs {:trial-selector :first})]
           (is (some? (:lesson-state result)))
           (is (nil? (:error result)))
           (let [lesson (:lesson-state result)]
             (is (= "lesson" (:_id lesson)))
             (is (= "lesson" (:type lesson)))
             (is (= 2 (count (:trials lesson))))
             (is (some? (:current-trial lesson)))
             (is (some? (:_rev lesson))))))))))


(deftest start-returns-error-when-no-words
  (async-testing "`start!` errors when vocabulary is empty"
    (with-test-dbs
     (fn [dbs]
       (p/let [result (sut/start! dbs)]
         (is (= :no-words-available (:error result)))
         (is (nil? (:lesson-state result))))))))


(deftest start-returns-error-when-db-insert-fails
  (async-testing "`start!` errors on db failure"
    (with-test-dbs
     (fn [dbs]
       (p/do
         (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}])
         ;; Override insert to fail
         (p/with-redefs [db/insert (fn [_ _] (p/rejected (ex-info "DB error" {})))]
           (p/let [result (sut/start! dbs {:trial-selector :first})]
             (is (= :lesson-start-failed (:error result)))
             (is (nil? (:lesson-state result))))))))))


(deftest start-includes-example-trials
  (async-testing "`start!` includes example trials"
    (with-test-dbs
     (fn [dbs]
       (p/do
         (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}])
         (db-seed/seed-examples! (:device/db dbs)
                                 [{:_id         "example-1"
                                   :word-id     "word-1"
                                   :word        "der Hund"
                                   :value       "Der Hund schlaeft."
                                   :translation "Пёс спит"}])
         (p/let [result (sut/start! dbs {:trial-selector :first})]
           (let [lesson (:lesson-state result)
                 trials (:trials lesson)]
             (is (= 2 (count trials)))
             (is (= 1 (count (filter #(= "word" (:type %)) trials))))
             (is (= 1 (count (filter #(= "example" (:type %)) trials))))
             (is (= [true]
                    (->> trials
                         (filter #(= "example" (:type %)))
                         (map :locked?)
                         vec))))))))))


;; =============================================================================
;; restart!
;; =============================================================================


(deftest restart-replaces-existing-lesson-with-fresh-session
  (async-testing "`restart!` discards persisted lesson state and starts fresh"
    (with-test-dbs
     (fn [dbs]
       (p/do
         (db-seed/seed-vocabulary!
          (:user/db dbs)
          [{:_id "word-1" :value "der Hund" :translation "пёс"}
           {:_id "word-2" :value "die Katze" :translation "кошка"}])
         (p/let [start-result (sut/start! dbs {:trial-selector :first})
                 first-trial  (domain/current-trial (:lesson-state start-result))
                 _            (sut/check-answer! dbs (:answer first-trial))
                 restarted    (sut/restart! dbs {:trial-selector :first})
                 lessons      (db-queries/fetch-by-type (:device/db dbs) "lesson")
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
     (fn [dbs]
       (p/let [result (sut/restart! dbs)]
         (is (= :no-words-available (:error result)))
         (is (nil? (:lesson-state result))))))))


;; =============================================================================
;; check-answer!
;; =============================================================================


(deftest check-answer-correct-word-trial
  (async-testing "`check-answer!` creates review on correct answer"
    (with-test-dbs
     (fn [dbs]
       (p/do
         (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}])
         (p/let [_ (sut/start! dbs {:trial-selector :first})
                 result (sut/check-answer! dbs "der Hund")]
           (is (some? (:lesson-state result)))
           (is (nil? (:error result)))
           (let [last-result (domain/last-result (:lesson-state result))]
             (is (true? (:correct? last-result)))
             (is (= "der Hund" (:answer last-result)))))
         ;; Verify a review was created for the word trial
         (p/let [reviews (db-queries/fetch-by-type (:user/db dbs) "review")]
           (is (= 2 (count reviews)))
           (is (= ["word-1"]
                  (->> reviews
                       (map :word-id)
                       (remove nil?)
                       distinct
                       vec)))))))))


(deftest check-answer-wrong-word-trial
  (async-testing "`check-answer!` creates review on wrong answer"
    (with-test-dbs
     (fn [dbs]
       (p/do
         (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}])
         (p/let [_ (sut/start! dbs {:trial-selector :first})
                 result (sut/check-answer! dbs "wrong answer")]
           (is (some? (:lesson-state result)))
           (let [last-result (domain/last-result (:lesson-state result))]
             (is (false? (:correct? last-result)))))
         ;; Verify a review was created (even for wrong answer)
         (p/let [reviews (db-queries/fetch-by-type (:user/db dbs) "review")]
           (is (= 2 (count reviews)))))))))


(deftest check-answer-clamps-very-long-input
  (async-testing "`check-answer!` limits oversized answers"
    (with-test-dbs
     (fn [dbs]
       (let [long-answer (apply str (repeat 1400 "x"))]
         (p/do
           (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}])
           (p/let [_ (sut/start! dbs {:trial-selector :first})
                   result (sut/check-answer! dbs long-answer)
                   answer (-> result :lesson-state domain/last-result :answer)]
             (is (= 1000 (count answer)))
             (is (= (subs long-answer 0 1000) answer)))))))))


(deftest check-answer-example-trial-no-review
  (async-testing "`check-answer!` skips review for example trials"
    (with-test-dbs
     (fn [dbs]
       (p/do
         (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}])
         (db-seed/seed-examples! (:device/db dbs)
                                 [{:_id         "example-1"
                                   :word-id     "word-1"
                                   :word        "der Hund"
                                   :value       "Der Hund schlaeft."
                                   :translation "Пёс спит."}])
         (p/let [_ (sut/start! dbs {:trial-selector :first})
                 ;; Answer the first word trial correctly
                 _ (sut/check-answer! dbs "der Hund")
                 ;; Advance to the example trial
                 _ (sut/advance! dbs)
                 initial-reviews (db-queries/fetch-by-type (:user/db dbs) "review")
                 ;; Answer the example trial
                 _ (sut/check-answer! dbs "Der Hund schlaeft.")
                 final-reviews   (db-queries/fetch-by-type (:user/db dbs) "review")]
           ;; No new review should be created for example trials
           (is (= (count initial-reviews) (count final-reviews)))))))))


(deftest check-answer-correct-word-unlocks-example-trial
  (async-testing "`check-answer!` unlocks examples after successful word answer"
    (with-test-dbs
     (fn [dbs]
       (p/do
         (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}])
         (db-seed/seed-examples! (:device/db dbs)
                                 [{:_id         "example-1"
                                   :word-id     "word-1"
                                   :word        "der Hund"
                                   :value       "Der Hund schlaeft."
                                   :translation "Пёс спит."}])
         (p/let [_      (sut/start! dbs {:trial-selector :first})
                 result (sut/check-answer! dbs "der Hund")
                 trial  (->> (:remaining-trials (:lesson-state result))
                             (filter domain/example-trial?)
                             first)]
           (is (some? trial))
           (is (false? (:locked? trial)))))))))


(deftest check-answer-returns-error-when-no-lesson
  (async-testing "`check-answer!` errors when no lesson"
    (with-test-dbs
     (fn [dbs]
       (p/let [result (sut/check-answer! dbs "any answer")]
         (is (= :lesson-not-found (:error result)))
         (is (nil? (:lesson-state result))))))))


(deftest check-answer-handles-db-insert-failure
  (async-testing "`check-answer!` errors on db failure"
    (with-test-dbs
     (fn [dbs]
       (p/do
         (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}])
         (p/let [_ (sut/start! dbs {:trial-selector :first})]
           ;; Make insert fail for subsequent calls
           (p/with-redefs [db/insert (fn [_ _] (p/rejected (ex-info "DB error" {})))]
             (p/let [result (sut/check-answer! dbs "der Hund")]
               ;; Should return error with computed lesson-state
               (is (= :lesson-save-failed (:error result)))
               (is (some? (:lesson-state result)))
               ;; The lesson-state should have the answer result computed
               (let [last-result (domain/last-result (:lesson-state result))]
                 (is (true? (:correct? last-result))))))))))))


;; =============================================================================
;; advance!
;; =============================================================================


(deftest advance-selects-next-trial
  (async-testing "`advance!` moves to next trial"
    (with-test-dbs
     (fn [dbs]
       (p/do
         (db-seed/seed-vocabulary! (:user/db dbs)
                                   [{:_id "word-1" :value "der Hund" :translation "пёс"}
                                    {:_id "word-2" :value "die Katze" :translation "cat"}])
         (p/let [start-result   (sut/start! dbs {:trial-selector :first})
                 first-trial    (domain/current-trial (:lesson-state start-result))
                 _ (sut/check-answer! dbs (:answer first-trial))
                 advance-result (sut/advance! dbs)]
           (is (some? (:lesson-state advance-result)))
           (is (nil? (:error advance-result)))
           (let [next-trial (domain/current-trial (:lesson-state advance-result))]
             (is (some? next-trial))
             (is (not= first-trial next-trial)))))))))


(deftest advance-returns-nil-when-finished
  (async-testing "`advance!` returns nil when finished"
    (with-test-dbs
     (fn [dbs]
       (p/do
         (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}])
         (p/let [_ (sut/start! dbs {:trial-selector :first})
                 _ (sut/check-answer! dbs "der Hund")
                 result (sut/advance! dbs)]
           (is (nil? result))))))))


(deftest advance-returns-error-when-no-lesson
  (async-testing "`advance!` errors when no lesson"
    (with-test-dbs
     (fn [dbs]
       (p/let [result (sut/advance! dbs)]
         (is (= :lesson-not-found (:error result))))))))


(deftest advance-handles-db-insert-failure
  (async-testing "`advance!` errors on db failure"
    (with-test-dbs
     (fn [dbs]
       (p/do
         (db-seed/seed-vocabulary! (:user/db dbs)
                                   [{:_id "word-1" :value "der Hund" :translation "пёс"}
                                    {:_id "word-2" :value "die Katze" :translation "cat"}])
         (p/let [_ (sut/start! dbs {:trial-selector :first})
                 _ (sut/check-answer! dbs "der Hund")]
           ;; Make insert fail
           (p/with-redefs [db/insert (fn [_ _] (p/rejected (ex-info "DB error" {})))]
             (p/let [result (sut/advance! dbs)]
               (is (= :lesson-save-failed (:error result)))))))))))


;; =============================================================================
;; finish!
;; =============================================================================


(deftest finish-removes-lesson
  (async-testing "`finish!` removes lesson from db"
    (with-test-dbs
     (fn [dbs]
       (p/do
         (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}])
         (p/let [_ (sut/start! dbs {:trial-selector :first})
                 lessons-before (db-queries/fetch-by-type (:device/db dbs) "lesson")
                 _ (sut/finish! dbs)
                 lessons-after  (db-queries/fetch-by-type (:device/db dbs) "lesson")]
           (is (= 1 (count lessons-before)))
           (is (= 0 (count lessons-after)))))))))


(deftest finish-is-noop-when-no-lesson
  (async-testing "`finish!` no-op when no lesson"
    (with-test-dbs
     (fn [dbs]
       (p/let [result (sut/finish! dbs)]
         ;; Should complete without error
         (is (nil? result)))))))


;; =============================================================================
;; Full lesson flow
;; =============================================================================


(deftest full-lesson-flow-completes-successfully
  (async-testing "full lesson flow: start → answer → finish"
    (with-test-dbs
     (fn [dbs]
       (p/do
         (db-seed/seed-vocabulary!
          (:user/db dbs)
          [{:_id "word-1" :value "der Hund" :translation "пёс"}
           {:_id "word-2" :value "die Katze" :translation "cat"}])
         (p/let [;; Start lesson
                 start-result (sut/start! dbs {:trial-selector :first})
                 _ (is (some? (:lesson-state start-result)))

                 ;; Answer first trial correctly
                 first-trial  (domain/current-trial (:lesson-state start-result))
                 check1       (sut/check-answer! dbs (:answer first-trial))
                 _ (is (true? (:correct? (domain/last-result (:lesson-state check1)))))

                 ;; Advance to next trial
                 advance1     (sut/advance! dbs)
                 _ (is (some? (:lesson-state advance1)))

                 ;; Answer second trial correctly
                 second-trial (domain/current-trial (:lesson-state advance1))
                 check2       (sut/check-answer! dbs (:answer second-trial))
                 _ (is (true? (:correct? (domain/last-result (:lesson-state check2)))))
                 _ (is (domain/finished? (:lesson-state check2)))

                 ;; Advance should return nil when finished
                 advance2     (sut/advance! dbs)
                 _ (is (nil? advance2))

                 ;; Finish lesson
                 _ (sut/finish! dbs)
                 lessons      (db-queries/fetch-by-type (:device/db dbs) "lesson")]
           (is (empty? lessons))))))))

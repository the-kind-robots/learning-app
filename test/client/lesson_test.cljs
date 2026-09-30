(ns client.lesson-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.examples :as examples]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.db-seed :as db-seed]
   [client.support.time :as time]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [domain.lesson :as domain]
   [ports.reviews :as reviews]
   [ports.words :as words]
   [use-cases.lesson :as sut]
   [use-cases.vocabulary :as vocabulary]
   [utils :as utils]))


(def user-db-name (db-fixtures/db-name "client.lesson-test.user"))


(def device-db-name (db-fixtures/db-name "client.lesson-test.device"))


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
  ([dbs]
   (test-capabilities dbs (fn [_word _coll-id _coll-name] (js/Promise.resolve nil))))
  ([dbs request!]
   (let [clock {:clock/now-iso time/now-iso
                :clock/now-ms  time/now-ms}]
     {::dbs        dbs
      :clock       clock
      :reviews     (reviews/start! {:db dbs :clock clock})
      :words       (words/start! {:db dbs :clock clock})
      :collections {:collections/active-id (fn [] nil)
                    :collections/get       (fn [_] nil)}
      :examples    {:examples/list     (fn [word-ids] (examples/list dbs word-ids))
                    :examples/request! request!}})))


(defn- ^:async start!
  "What entering the lesson does: draw it from memory loaded off `dbs`."
  [capabilities opts]
  (let [dbs    (::dbs capabilities)
        memory (await (db-seed/memory-of (:user/db dbs) (:device/db dbs)))]
    (sut/start memory ((get-in capabilities [:collections :collections/active-id])) opts (time/now-ms))))


(defn- ^:async started
  "The lesson state `start!` draws."
  [capabilities opts]
  (:lesson-state (await (start! capabilities opts))))


(defn- ^:async held-words
  "Every word as memory would hold `dbs` now, with its retention level and
   the urgency a lesson ranks it by."
  [capabilities]
  (let [dbs    (::dbs capabilities)
        memory (await (db-seed/memory-of (:user/db dbs) (:device/db dbs)))
        now    (time/now-ms)
        levels (into {} (map (juxt :id :retention-level)) (:words (vocabulary/rows memory {} now)))]
    {:words (mapv (fn [{:keys [word] :as card}]
                    {:id      (:id word)
                     :retention-level (levels (:id word))
                     :urgency (vocabulary/urgency-of card now)})
                  (vocabulary/collection-cards memory nil))}))


(deftest start-creates-lesson-when-words-available
  (async-testing "`start!` creates lesson when vocabulary is not empty"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (db-seed/seed-vocabulary!
              (:user/db dbs)
              [{:_id "word-1" :value "der Hund" :translation "пёс"}
               {:_id "word-2" :value "die Katze" :translation "кошка"}]))
      (let [result (await (start! (test-capabilities dbs) {:trial-selector :first}))]
        (is (some? (:lesson-state result)))
        (is (nil? (:error result)))
        (let [lesson (:lesson-state result)]
          (is (= 2 (count (:trials lesson))))
          (is (some? (:current-trial lesson)))
          (is (nil? (:_id lesson)) "the state carries no storage names")))))))


(deftest start-returns-error-when-no-words
  (async-testing "`start!` errors when vocabulary is empty"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [result (await (start! (test-capabilities dbs) {}))]
        (is (= :no-words-available (:error result)))
        (is (nil? (:lesson-state result))))))))


(deftest start-includes-example-trials
  (async-testing "`start!` includes example trials"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}]))
      (await (db-seed/seed-examples! (:device/db dbs)
                                     [{:_id         "example-1"
                                       :word-id     "word-1"
                                       :word        "der Hund"
                                       :value       "Der Hund schlaeft."
                                       :translation "Пёс спит"}]))
      (let [result (await (start! (test-capabilities dbs) {:trial-selector :first}))
            trials (:trials (:lesson-state result))]
        (is (= 2 (count trials)))
        (is (= 1 (count (filter #(= "word" (:type %)) trials))))
        (is (= 1 (count (filter #(= "example" (:type %)) trials))))
        (is (= [true]
               (->> trials
                    (filter #(= "example" (:type %)))
                    (map :locked?)
                    vec))))))))


(deftest check-answer-correct-word-trial
  (async-testing "`check-answer!` creates review on correct answer"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}]))
      (let [lesson      (await (started (test-capabilities dbs) {:trial-selector :first}))
            result      (await (sut/check-answer! (test-capabilities dbs) lesson "der Hund"))
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
     (^:async fn
      [dbs]
      (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}]))
      (let [lesson      (await (started (test-capabilities dbs) {:trial-selector :first}))
            result      (await (sut/check-answer! (test-capabilities dbs) lesson "wrong answer"))
            last-result (domain/last-result (:lesson-state result))]
        (is (some? (:lesson-state result)))
        (is (false? (:correct? last-result))))
      (let [reviews (await (db-queries/fetch-by-type (:user/db dbs) "review"))]
        (is (= 2 (count reviews))))))))


(deftest check-answer-clamps-very-long-input
  (async-testing "`check-answer!` limits oversized answers"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [long-answer (apply str (repeat 1400 "x"))]
        (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}]))
        (let [lesson (await (started (test-capabilities dbs) {:trial-selector :first}))
              result (await (sut/check-answer! (test-capabilities dbs) lesson long-answer))
              answer (-> result :lesson-state domain/last-result :answer)]
          (is (= 1000 (count answer)))
          (is (= (subs long-answer 0 1000) answer))))))))


(deftest check-answer-example-trial-no-review
  (async-testing "`check-answer!` skips review for example trials"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}]))
      (await (db-seed/seed-examples! (:device/db dbs)
                                     [{:_id         "example-1"
                                       :word-id     "word-1"
                                       :word        "der Hund"
                                       :value       "Der Hund schlaeft."
                                       :translation "Пёс спит."}]))
      (let [lesson          (await (started (test-capabilities dbs) {:trial-selector :first}))
            checked         (:lesson-state (await (sut/check-answer! (test-capabilities dbs) lesson "der Hund")))
            initial-reviews (await (db-queries/fetch-by-type (:user/db dbs) "review"))]
        (await (sut/check-answer! (test-capabilities dbs) (domain/advance checked) "Der Hund schlaeft."))
        (let [final-reviews (await (db-queries/fetch-by-type (:user/db dbs) "review"))]
          (is (= (count initial-reviews) (count final-reviews)))))))))


(deftest check-answer-correct-word-unlocks-example-trial
  (async-testing "`check-answer!` unlocks examples after successful word answer"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}]))
      (await (db-seed/seed-examples! (:device/db dbs)
                                     [{:_id         "example-1"
                                       :word-id     "word-1"
                                       :word        "der Hund"
                                       :value       "Der Hund schlaeft."
                                       :translation "Пёс спит."}]))
      (let [lesson (await (started (test-capabilities dbs) {:trial-selector :first}))
            result (await (sut/check-answer! (test-capabilities dbs) lesson "der Hund"))
            trial  (->> (:remaining-trials (:lesson-state result))
                        (filter domain/example-trial?)
                        first)]
        (is (some? trial))
        (is (false? (:locked? trial))))))))


(deftest token-info-returns-unknown-for-missing-word
  (async-testing "`token-info` reports unknown word when dictionary form not in vocabulary"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [info (await (sut/token-info (test-capabilities dbs) "die Seele" "душа"))]
        (is (= :unknown-word (:state info)))
        (is (= "die Seele" (:dictionary-form info)))
        (is (= "душа" (:translation info))))))))


(deftest token-info-returns-known-when-translation-in-set
  (async-testing "`token-info` reports known-with-translation when the gloss is already on the word"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (db-seed/seed-vocabulary! (:user/db dbs) [{:value "die Seele" :translation "душа"}]))
      (let [info (await (sut/token-info (test-capabilities dbs) "die Seele" "душа"))]
        (is (= :known-with-translation (:state info))))))))


(deftest token-info-returns-missing-when-translation-not-in-set
  (async-testing "`token-info` reports known-missing-translation when the gloss is new for an existing word"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (db-seed/seed-vocabulary! (:user/db dbs) [{:value "die Bank" :translation "банк"}]))
      (let [info (await (sut/token-info (test-capabilities dbs) "die Bank" "скамейка"))]
        (is (= :known-missing-translation (:state info))))))))


(deftest check-answer-returns-error-when-no-lesson
  (async-testing "`check-answer!` errors when no lesson"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [result (await (sut/check-answer! (test-capabilities dbs) nil "any answer"))]
        (is (= :lesson-not-found (:error result)))
        (is (nil? (:lesson-state result))))))))


(deftest check-answer-handles-db-insert-failure
  (async-testing "`check-answer!` errors on db failure"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}]))
      (let [lesson (await (started (test-capabilities dbs) {:trial-selector :first}))]
        (with-redefs [db/insert (fn [_ _] (js/Promise.reject (ex-info "DB error" {})))]
          (let [result      (await (sut/check-answer! (test-capabilities dbs) lesson "der Hund"))
                last-result (domain/last-result (:lesson-state result))]
            (is (= :review-save-failed (:error result)))
            (is (some? (:lesson-state result)))
            (is (true? (:correct? last-result))))))))))


(deftest full-lesson-flow-completes-successfully
  (async-testing "full lesson flow: start → answer → advance → finished"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (db-seed/seed-vocabulary!
              (:user/db dbs)
              [{:_id "word-1" :value "der Hund" :translation "пёс"}
               {:_id "word-2" :value "die Katze" :translation "cat"}]))
      (let [capabilities (test-capabilities dbs)
            lesson       (await (started capabilities {:trial-selector :first}))
            check1       (:lesson-state (await (sut/check-answer! capabilities lesson (:answer (domain/current-trial lesson)))))
            advanced     (domain/advance check1)
            check2       (:lesson-state (await (sut/check-answer! capabilities advanced (:answer (domain/current-trial advanced)))))]
        (is (true? (:correct? (domain/last-result check1))))
        (is (not= (domain/current-trial lesson) (domain/current-trial advanced)))
        (is (true? (:correct? (domain/last-result check2))))
        (is (domain/finished? check2))
        (is (nil? (domain/advance check2))))))))


(defn- ^:async seed-stale-vocabulary!
  "Words whose single review is old enough that every retention level
   underflows to a flat zero, so nothing but the sort key tells them apart."
  [db words]
  (await
   (js/Promise.all
    (into-array
     (mapcat
      (fn [{:keys [id value days-ago]}]
        [(db/insert db
                    {:_id         id
                     :type        "vocab"
                     :value       value
                     :translation [{:lang "ru" :value "слово"}]
                     :created-at  time/test-now-iso
                     :modified-at time/test-now-iso})
         (db/insert db
                    {:_id        (str "review-" id)
                     :type       "review"
                     :word-id    id
                     :retained   true
                     :created-at (utils/ms->iso (- (time/now-ms)
                                                   (* days-ago 24 3600 1000)))})])
      words)))))


(deftest start-draws-the-most-due-not-the-alphabet
  (async-testing
    "`start!` over words whose retention has all underflowed serves the most due, not the ones the alphabet puts first"
    (with-test-dbs
     (^:async fn
      [dbs]
      ;; The vocab view is keyed by document id, so left to itself the read
      ;; order here is abend, abfahrt, abholen, zeit, zug, zurueck — and the
      ;; three `ab-` words are the three *least* due of the six (#431).
      (await (seed-stale-vocabulary!
              (:user/db dbs)
              [{:id "vocab:abend" :value "der Abend" :days-ago 10}
               {:id "vocab:abfahrt" :value "die Abfahrt" :days-ago 10}
               {:id "vocab:abholen" :value "abholen" :days-ago 10}
               {:id "vocab:zeit" :value "die Zeit" :days-ago 300}
               {:id "vocab:zug" :value "der Zug" :days-ago 300}
               {:id "vocab:zurueck" :value "zurück" :days-ago 300}]))
      (let [capabilities    (test-capabilities dbs)
            {:keys [words]} (await (held-words capabilities))
            {:keys [lesson-state]}
            (await (start! capabilities
                           {:vocab-pool-size  3
                            :vocab-per-lesson 3
                            :trial-selector   :first}))]
        (is (every? zero? (map :retention-level words))
            "the premise: retention has underflowed to zero for all six")
        (is (= #{"vocab:zeit" "vocab:zug" "vocab:zurueck"}
               (set (map :word-id (:trials lesson-state))))
            "the lesson holds the three most due, not the three the alphabet leads with"))))))


(defn- ^:async seed-unreviewed-vocabulary!
  "Words with no review at all. Every one of them is maximally due, so every
   one has the same urgency — the tie that is left once the underflow is
   gone."
  [db values]
  (await (js/Promise.all
          (into-array
           (map (fn [value]
                  (db/insert db
                             {:_id         (str "vocab:" value)
                              :type        "vocab"
                              :value       value
                              :translation [{:lang "ru" :value "слово"}]
                              :created-at  time/test-now-iso
                              :modified-at time/test-now-iso}))
                values)))))


(deftest start-cuts-a-tied-vocabulary-at-random
  (async-testing "`start!` over words that all tie on urgency does not keep serving the alphabetically first"
    (with-test-dbs
     (^:async fn
      [dbs]
      ;; A word document and its reviews are separate documents and can
      ;; arrive apart, so a whole vocabulary can sit at ##Inf at once.
      (await (seed-unreviewed-vocabulary!
              (:user/db dbs)
              ["abend" "abfahrt" "abholen" "ankommen" "aufstehen"
               "bleiben" "bringen" "denken" "essen" "fahren"]))
      (let [capabilities (test-capabilities dbs)
            urgencies    (->> (await (held-words capabilities))
                              :words
                              (map :urgency)
                              set)
            ;; The pool has to be smaller than the vocabulary, or there is no
            ;; cut to make and this passes even on a pool cut alphabetically.
            draw         (^:async fn
                          []
                          (let [{:keys [lesson-state]}
                                (await (start! capabilities
                                               {:vocab-pool-size 3
                                                :trial-selector  :first}))]
                            (set (map :word-id (:trials lesson-state)))))
            first-draw   (await (draw))
            second-draw  (await (draw))
            third-draw   (await (draw))
            fourth-draw  (await (draw))
            fifth-draw   (await (draw))]
        (is (= #{##Inf} urgencies)
            "the premise: all ten tie at the same urgency")
        (is (< 1 (count #{first-draw second-draw third-draw fourth-draw fifth-draw}))
            "five lessons over the tied vocabulary did not all draw the same words"))))))

(ns client.lesson-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.learner.memory :as memory]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.db-seed :as db-seed]
   [client.support.learner :as learner]
   [client.support.time :as time]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [domain.lesson :as domain]
   [ports.learner :as ports]
   [use-cases.lesson :as sut]
   [use-cases.vocabulary :as vocabulary]
   [utils :as utils]))


(def user-db-name (db-fixtures/db-name "client.lesson-test.user"))


(def device-db-name (db-fixtures/db-name "client.lesson-test.device"))


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


(defn- test-capabilities
  [dbs]
  {::dbs    dbs
   :clock   clock
   :learner (assoc (::learner dbs) :learner/active-collection (fn [] nil))})


(defn- ^:async caught-up!
  "Resolves once memory has what the test seeded."
  [dbs]
  (await (learner/caught-up dbs (::store dbs))))


(defn- ^:async start!
  "What entering the lesson does: draw it from memory loaded off `dbs`."
  [capabilities opts]
  (let [dbs    (::dbs capabilities)
        memory (await (db-seed/memory-of (:user/db dbs)))]
    (sut/start ports/reads memory ((get-in capabilities [:learner :learner/active-collection])) opts (time/now-ms))))


(defn- ^:async started
  "The lesson state `start!` draws."
  [capabilities opts]
  (:lesson-state (await (start! capabilities opts))))


(defn- ^:async cached-words
  "Every word as memory would have `dbs` now, with its retention level and
   the urgency a lesson ranks it by."
  [capabilities]
  (let [dbs    (::dbs capabilities)
        memory (await (db-seed/memory-of (:user/db dbs)))
        now    (time/now-ms)
        levels (into {} (map (juxt :id :retention-level)) (:words (vocabulary/rows ports/reads memory {} now)))]
    {:words (mapv (fn [{:keys [word] :as card}]
                    {:id      (:id word)
                     :retention-level (levels (:id word))
                     :urgency (vocabulary/urgency-of card now)})
                  (memory/collection-cards memory nil))}))


(deftest an-answer-to-a-word-is-a-review-of-that-word
  (async-testing "a right answer is written as retained, a wrong one as forgotten"
    (doseq [[answer retained?] [["der Hund" true] ["wrong answer" false]]]
      ;; Each case starts from empty databases.
      (await (db-fixtures/destroy-test-db user-db-name))
      (await (db-fixtures/destroy-test-db device-db-name))
      (await
       (with-test-dbs
        (^:async fn
         [dbs]
         (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}]))
         (let [lesson  (await (started (test-capabilities dbs) {:trial-selector :first}))
               before  (set (map :_id (await (db-queries/fetch-by-type (:user/db dbs) "review"))))
               _       (await (sut/check-answer! (test-capabilities dbs) lesson answer))
               written (remove (comp before :_id) (await (db-queries/fetch-by-type (:user/db dbs) "review")))]
           (is (= [["word-1" retained?]] (map (juxt :word-id :retained) written)) answer))))))))


(deftest an-answer-to-an-example-writes-no-review
  (async-testing "`check-answer!` skips review for example trials"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (db-seed/seed-vocabulary! (:user/db dbs) [{:_id "word-1" :value "der Hund" :translation "пёс"}]))
      (await (db-seed/seed-examples! (:user/db dbs)
                                     [{:_id         "example-1"
                                       :word-id     "word-1"
                                       :word        "der Hund"
                                       :value       "Der Hund schlaeft."
                                       :translation "Пёс спит."}]))
      (let [lesson  (await (started (test-capabilities dbs) {:trial-selector :first}))
            checked (:lesson-state (await (sut/check-answer! (test-capabilities dbs) lesson "der Hund")))
            initial-reviews (await (db-queries/fetch-by-type (:user/db dbs) "review"))]
        (await (sut/check-answer! (test-capabilities dbs) (domain/advance checked) "Der Hund schlaeft."))
        (let [final-reviews (await (db-queries/fetch-by-type (:user/db dbs) "review"))]
          (is (= (count initial-reviews) (count final-reviews)))))))))


(deftest a-review-that-cannot-be-saved-is-reported-and-the-answer-stands
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


(deftest a-lesson-draws-the-most-due-not-the-alphabet
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
            {:keys [words]} (await (cached-words capabilities))
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



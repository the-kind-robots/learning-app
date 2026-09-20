(ns client.vocabulary-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.examples :as examples]
   [client.support.db-fixtures :as db-fixtures]
   [client.support.db-queries :as db-queries]
   [client.support.time :as time]
   [cljs.test :refer-macros [deftest is use-fixtures]]
   [db :as db]
   [domain.retention :as retention]
   [ports.reviews :as reviews]
   [ports.words :as words]
   [use-cases.vocabulary :as sut]
   [utils :as utils]))


(def user-db-name (db-fixtures/db-name "client.vocabulary-test.user"))


(def device-db-name (db-fixtures/db-name "client.vocabulary-test.device"))


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
  [dbs]
  (let [clock {:clock/now-iso time/now-iso
               :clock/now-ms  time/now-ms}]
    {:clock       clock
     :reviews     (reviews/start! {:db dbs :clock clock})
     :words       (words/start! {:db dbs :clock clock})
     ;; Vocabulary use-case calls into collections (active-id) and examples
     ;; (request!/find) to scope per-collection examples. Stub these as
     ;; main-card-active no-ops so tests stay isolated.
     :collections {:collections/active-id     (fn [] nil)
                   :collections/get           (fn [_] nil)
                   :collections/add-word!     (fn [_ _] (js/Promise.resolve nil))
                   :collections/docs-without-word (fn [_] (js/Promise.resolve []))
                   :collections/exclude-word! (fn [_ _] (js/Promise.resolve nil))}
     :examples    {:examples/find     (fn [_ _] (js/Promise.resolve nil))
                   :examples/purge-by-word! (fn [word-id] (examples/purge-by-word! dbs word-id))
                   :examples/request! (fn [_ _ _] nil)}}))


(deftest add-creates-vocab-and-initial-review
  (async-testing "`add!` creates vocab and initial review"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [{:keys [word-id created?]} (await (sut/add! (test-capabilities dbs) "der Hund" "пёс"))
            vocabs  (await (db-queries/fetch-by-type (:user/db dbs) "vocab"))
            reviews (await (db-queries/fetch-by-type (:user/db dbs) "review"))]
        (is (string? word-id))
        (is (true? created?))
        (is (= 1 (count vocabs)))
        (is (= 1 (count reviews)))
        (is (= "der Hund" (:value (first vocabs))))
        (is (= word-id (:word-id (first reviews))))
        (is (true? (:retained (first reviews)))))))))


(deftest list-returns-summaries-with-retention
  (async-testing "`list` returns summaries with retention"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (sut/add! (test-capabilities dbs) "der Hund" "пёс"))
      (await (sut/add! (test-capabilities dbs) "die Katze" "кот"))
      (let [{:keys [words total]} (await (sut/list (test-capabilities dbs) {}))]
        (is (= 2 (count words)))
        (is (= 2 total))
        (is (every? :retention-level words)))))))


(deftest list-filters-and-paginates
  (async-testing "`list` supports search and limit"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (sut/add! (test-capabilities dbs) "der Hund" "пёс"))
      (await (sut/add! (test-capabilities dbs) "die Katze" "кот"))
      (await (sut/add! (test-capabilities dbs) "der Vogel" "птица"))
      (let [{:keys [matches words total]} (await (sut/list (test-capabilities dbs) {:search "Hund" :limit 1}))]
        (is (= 3 total))
        (is (= 1 matches)
            "`matches` counts what the search left, `total` what the scope holds")
        (is (= 1 (count words)))
        (is (= "der Hund" (:value (first words)))))))))


(deftest list-reports-how-many-rows-the-page-left-behind
  (async-testing "`matches` says whether another page follows"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (js/Promise.all
              (into-array (map (fn [i] (sut/add! (test-capabilities dbs) (str "wort-" i) (str "перевод-" i)))
                               (range 12)))))
      (let [{:keys [matches words total]} (await (sut/list (test-capabilities dbs) {:limit 5}))]
        (is (= 5 (count words)) "the page is the limit")
        (is (= 12 matches) "every word matched the empty filter")
        (is (= 12 total)))
      (let [{:keys [matches words]} (await (sut/list (test-capabilities dbs) {:limit 50}))]
        (is (= 12 (count words)))
        (is (= 12 matches) "a page larger than the list leaves nothing behind"))))))


(deftest count-reads-the-vocab-view-not-the-documents
  (async-testing "`count` counts vocab view rows and runs no find"
    (let [find-calls  (atom 0)
          query-calls (atom [])
          row-count   26]
      (with-redefs [db/find
                    (fn [_ _]
                      (swap! find-calls inc)
                      (js/Promise.resolve {:docs []}))

                    db/query
                    (fn [_ view opts]
                      (swap! query-calls conj [view opts])
                      (js/Promise.resolve
                       {:rows (vec (repeat row-count {:id "vocab:x" :key "vocab:x" :value [nil "x" []]}))}))]
        (let [cnt (await (sut/count (test-capabilities {:user/db :fake})))]
          (is (= row-count cnt))
          (is (= 0 @find-calls))
          (is (= [["vocab-preview/rows" {}]] @query-calls)))))))


(deftest list-and-count-return-all-words-beyond-25
  (async-testing "`list` and `count` return full data when db has more than 25 words"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (js/Promise.all
              (into-array (map (fn [i] (sut/add! (test-capabilities dbs) (str "word-" i) (str "перевод-" i)))
                               (range 30)))))
      (let [cnt (await (sut/count (test-capabilities dbs)))
            {:keys [words total]} (await (sut/list (test-capabilities dbs) {}))]
        (is (= 30 cnt))
        (is (= 30 total))
        (is (= 30 (count words))))))))


(deftest get-returns-summary
  (async-testing "`get` returns word summary"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [{:keys [word-id]} (await (sut/add! (test-capabilities dbs) "der Hund" "пёс"))
            result (await (sut/get (test-capabilities dbs) word-id))]
        (is (= word-id (:id result)))
        (is (= "der Hund" (:value result)))
        (is (= "пёс" (-> result :translation first :value)))
        (is (number? (:retention-level result))))))))


(deftest update-updates-and-returns-summary
  (async-testing "`update!` modifies and returns summary"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [{:keys [word-id]} (await (sut/add! (test-capabilities dbs) "der Hund" "пёс"))
            result (await (sut/update! (test-capabilities dbs) word-id "лиса"))]
        (is (= word-id (:id result)))
        (is (= "der Hund" (:value result)))
        (is (= "лиса" (-> result :translation first :value))))))))


(deftest delete-removes-word-related-docs
  (async-testing "`delete!` removes word, reviews and examples"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [{:keys [word-id]} (await (sut/add! (test-capabilities dbs) "der Hund" "пёс"))]
        (await (sut/add-review (test-capabilities dbs) word-id true "пёс"))
        (await (db/insert (:device/db dbs) {:type "example" :word-id word-id :value "Der Hund läuft"}))
        (await (sut/delete! (test-capabilities dbs) word-id))
        (let [vocabs   (await (db-queries/fetch-by-type (:user/db dbs) "vocab"))
              reviews  (await (db-queries/fetch-by-type (:user/db dbs) "review"))
              examples (await (db-queries/fetch-by-type (:device/db dbs) "example"))]
          (is (empty? vocabs))
          (is (empty? reviews))
          (is (empty? examples))))))))


(deftest add-review-creates-review-document
  (async-testing "`add-review` creates review document"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [{:keys [word-id]} (await (sut/add! (test-capabilities dbs) "der Hund" "пёс"))]
        (await (sut/add-review (test-capabilities dbs) word-id false "собака"))
        (let [reviews (await (db-queries/fetch-by-type (:user/db dbs) "review"))]
          (is (= 2 (count reviews)))
          (is (= 1 (count (filter (fn [r] (false? (:retained r))) reviews))))))))))


(defn- ^:async seed-reviews!
  "Seven reviews per word over the week before `test-now`, alternating
   retained, so every word lands on a different retention level."
  [dbs word-ids]
  (await (js/Promise.all
          (into-array
           (for [[n word-id] (map-indexed vector word-ids)
                 k (range 7)]
             (db/insert (:user/db dbs)
                        {:type       "review"
                         :word-id    word-id
                         :retained   (even? (+ n k))
                         :created-at (utils/ms->iso (- (time/now-ms)
                                                       (* (+ 1 n k) 6 3600 1000)))}))))))


(deftest list-retention-matches-per-word-reviews-beyond-25-reviews
  (async-testing "`list` retention equals retention over each word's own reviews when reviews exceed one page"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [word-ids (mapv #(str "vocab:wort-" %) (range 5))]
        (await (js/Promise.all
                (into-array
                 (map (fn [word-id]
                        (db/insert (:user/db dbs)
                                   {:_id         word-id
                                    :type        "vocab"
                                    :value       (subs word-id 6)
                                    :translation [{:lang "ru" :value "слово"}]
                                    :created-at  time/test-now-iso
                                    :modified-at time/test-now-iso}))
                      word-ids))))
        (await (seed-reviews! dbs word-ids))
        (let [{reviews :docs} (await (db/find-all (:user/db dbs) {:selector {:type "review"}}))
              expected (->> (group-by :word-id reviews)
                            (map (fn [[word-id reviews]]
                                   [word-id (retention/retention-level reviews (time/now-ms))]))
                            (into {}))
              {:keys [words]} (await (sut/list (test-capabilities dbs) {}))
              actual (into {} (map (juxt :id :retention-level)) words)
              {subset :words} (await (sut/list (test-capabilities dbs) {:word-ids (take 2 word-ids)}))]
          (is (= 35 (count reviews)))
          (is (= 5 (count (distinct (vals expected)))))
          (is (= expected actual))
          (is (= (select-keys expected (take 2 word-ids))
                 (into {} (map (juxt :id :retention-level)) subset)))))))))


(defn- ^:async seed-single-review!
  [dbs id value days-ago]
  (await (db/insert (:user/db dbs)
                    {:_id         id
                     :type        "vocab"
                     :value       value
                     :translation [{:lang "ru" :value "слово"}]
                     :created-at  time/test-now-iso
                     :modified-at time/test-now-iso}))
  (await (db/insert (:user/db dbs)
                    {:_id        (str "review-" id)
                     :type       "review"
                     :word-id    id
                     :retained   true
                     :created-at (utils/ms->iso (- (time/now-ms)
                                                   (* days-ago 24 3600 1000)))})))


(deftest list-orders-by-dueness-past-the-retention-underflow
  (async-testing "`list` orders long-unreviewed words by how due they are, not by the alphabet"
    (with-test-dbs
     (^:async fn
      [dbs]
      ;; `vocab:a-…` reads first from the id-keyed view and is the least due;
      ;; ordering by retention alone leaves it first, because every level here
      ;; has underflowed to the same zero (#431).
      (await (seed-single-review! dbs "vocab:a-wort" "A-Wort" 5))
      (await (seed-single-review! dbs "vocab:m-wort" "M-Wort" 60))
      (await (seed-single-review! dbs "vocab:z-wort" "Z-Wort" 300))
      (let [{:keys [words]} (await (sut/list (test-capabilities dbs) {:order :asc}))]
        (is (every? zero? (map :retention-level words)))
        (is (= ["vocab:z-wort" "vocab:m-wort" "vocab:a-wort"] (mapv :id words))))))))

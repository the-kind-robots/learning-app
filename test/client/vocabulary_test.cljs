(ns client.vocabulary-test
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
     ;; (request!/list) to scope per-collection examples. Stub these as
     ;; main-card-active no-ops so tests stay isolated.
     :collections {:collections/active-id     (fn [] nil)
                   :collections/get           (fn [_] nil)
                   :collections/add-word!     (fn [_ _] (js/Promise.resolve nil))
                   :collections/docs-without-word (fn [_] (js/Promise.resolve []))
                   :collections/exclude-word! (fn [_ _] (js/Promise.resolve nil))}
     :examples    {:examples/of-word        (fn [_] (js/Promise.resolve []))
                   :examples/purge-by-word! (fn [word-id] (examples/purge-by-word! dbs word-id))
                   :examples/request!       (fn [_ _ _] nil)}}))


(defn- ^:async list-of
  "`rows` over the learner's data as memory would hold `dbs` now."
  [dbs opts]
  (sut/rows (await (db-seed/memory-of (:user/db dbs))) opts (time/now-ms)))


(defn- ^:async count-of
  [dbs]
  (sut/word-count (await (db-seed/memory-of (:user/db dbs)))))


(deftest add-creates-vocab-and-initial-review
  (async-testing "`add!` creates vocab and initial review"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [{:keys [word-id created?]} (await (sut/add! (test-capabilities dbs) "der Hund" "пёс" :word))
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
      (await (sut/add! (test-capabilities dbs) "der Hund" "пёс" :word))
      (await (sut/add! (test-capabilities dbs) "die Katze" "кот" :word))
      (let [{:keys [words total]} (await (list-of dbs {}))]
        (is (= 2 (count words)))
        (is (= 2 total))
        (is (every? :retention-level words)))))))


(deftest list-filters-and-paginates
  (async-testing "`list` supports search and limit"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (sut/add! (test-capabilities dbs) "der Hund" "пёс" :word))
      (await (sut/add! (test-capabilities dbs) "die Katze" "кот" :word))
      (await (sut/add! (test-capabilities dbs) "der Vogel" "птица" :word))
      (let [{:keys [matches words total]} (await (list-of dbs {:search "Hund" :limit 1}))]
        (is (= 3 total))
        (is (= 1 matches)
            "`matches` counts what the search left, `total` what the scope holds")
        (is (= 1 (count words)))
        (is (= "der Hund" (:value (first words)))))
      (let [{:keys [matches words total]} (await (list-of dbs {:search "zzz" :limit 50}))]
        (is (= [] words) "an empty page reads no reviews and returns no rows")
        (is (= 0 matches))
        (is
         (= 3 total)
         "the scope is still three words, which is how the screen
                         tells an empty vocabulary from a search with no match"))))))


(deftest a-phrase-is-counted-and-listed-like-a-word
  (async-testing "one vocabulary, two kinds (#371)"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (sut/add! (test-capabilities dbs) "der Hund" "пёс" :word))
      (await (sut/add! (test-capabilities dbs) "auf jeden Fall" "в любом случае" :phrase))
      (is (= 2 (await (count-of dbs)))
          "a phrase is counted like a word")
      (let [{:keys [matches total words]} (await (list-of dbs {:limit 50}))]
        (is (= ["vocab:auf jeden fall" "vocab:der hund"] (mapv :id words))
            "both kinds in one alphabet")
        (is (= ["phrase" nil] (mapv :kind words))
            "the kind rides along, so the row can be rendered as a phrase")
        (is (= 2 total))
        (is (= 2 matches)))
      (let [{:keys [words]} (await (list-of dbs {:search "jeden" :limit 50}))]
        (is (= ["vocab:auf jeden fall"] (mapv :id words))
            "a phrase is searchable by its value like a word"))))))


(deftest list-reports-how-many-rows-the-page-left-behind
  (async-testing "`matches` says whether another page follows"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (js/Promise.all
              (into-array (map (fn [i]
                                 (sut/add! (test-capabilities dbs) (str "wort-" i) (str "перевод-" i) :word))
                               (range 12)))))
      (let [{:keys [matches words total]} (await (list-of dbs {:limit 5}))]
        (is (= 5 (count words)) "the page is the limit")
        (is (= 12 matches) "every word matched the empty filter")
        (is (= 12 total)))
      (let [{:keys [matches words]} (await (list-of dbs {:limit 50}))]
        (is (= 12 (count words)))
        (is (= 12 matches) "a page larger than the list leaves nothing behind"))))))


(deftest list-and-count-return-all-words-beyond-25
  (async-testing "`list` and `count` return full data when db has more than 25 words"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (js/Promise.all
              (into-array (map (fn [i] (sut/add! (test-capabilities dbs) (str "word-" i) (str "перевод-" i) :word))
                               (range 30)))))
      (let [cnt (await (count-of dbs))
            {:keys [words total]} (await (list-of dbs {}))]
        (is (= 30 cnt))
        (is (= 30 total))
        (is (= 30 (count words))))))))


(deftest get-returns-summary
  (async-testing "`get` returns word summary"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [{:keys [word-id]} (await (sut/add! (test-capabilities dbs) "der Hund" "пёс" :word))
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
      (let [{:keys [word-id]} (await (sut/add! (test-capabilities dbs) "der Hund" "пёс" :word))
            result (await (sut/update! (test-capabilities dbs) word-id "лиса"))]
        (is (= word-id (:id result)))
        (is (= "der Hund" (:value result)))
        (is (= "лиса" (-> result :translation first :value))))))))


(deftest delete-removes-word-related-docs
  (async-testing "`delete!` removes word, reviews and examples"
    (with-test-dbs
     (^:async fn
      [dbs]
      (let [{:keys [word-id]} (await (sut/add! (test-capabilities dbs) "der Hund" "пёс" :word))]
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
      (let [{:keys [word-id]} (await (sut/add! (test-capabilities dbs) "der Hund" "пёс" :word))]
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
              {:keys [words]} (await (list-of dbs {}))
              actual (into {} (map (juxt :id :retention-level)) words)
              {subset :words} (await (list-of dbs {:word-ids (take 2 word-ids)}))]
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
      (let [{:keys [words]} (await (list-of dbs {:order :most-due}))]
        (is (every? zero? (map :retention-level words)))
        (is (= ["vocab:z-wort" "vocab:m-wort" "vocab:a-wort"] (mapv :id words))))))))


(deftest list-is-alphabetical-unless-the-caller-asks-for-the-due-ones
  (async-testing "the word list's order is the alphabet, whatever the retention"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (seed-single-review! dbs "vocab:a-wort" "A-Wort" 5))
      (await (seed-single-review! dbs "vocab:m-wort" "M-Wort" 60))
      (await (seed-single-review! dbs "vocab:z-wort" "Z-Wort" 300))
      (let [{:keys [words]} (await (list-of dbs {}))]
        (is (= ["vocab:a-wort" "vocab:m-wort" "vocab:z-wort"] (mapv :id words))
            "default order, and it is not the most due first")
        (is (every? :retention-level words)
            "a level for every row on the page, read by key"))
      (let [{:keys [words]} (await (list-of dbs {:limit 2}))]
        (is (= ["vocab:a-wort" "vocab:m-wort"] (mapv :id words))
            "the page is the head of the alphabet, not of the due list"))))))


(def ^:private nouns-and-a-verb
  "The issue's own six words. `aufstehen` before `das Auto`: `auf` sorts
   before `aut`, whatever the issue's illustration says."
  [["der Hund" "пёс"]
   ["die Katze" "кот"]
   ["das Auto" "машина"]
   ["der Zug" "поезд"]
   ["die Bank" "скамейка"]
   ["aufstehen" "вставать"]])


(def ^:private filed-order
  ["aufstehen" "das Auto" "die Bank" "der Hund" "die Katze" "der Zug"])


(defn- ^:async seed-nouns!
  [dbs]
  (doseq [[value translation] nouns-and-a-verb]
    (await (sut/add! (test-capabilities dbs) value translation :word))))


(deftest the-list-files-a-noun-under-its-word-not-its-article
  (async-testing "the article is ignored while ordering, on every alphabetical path (#438)"
    (with-test-dbs
     (^:async fn
      [dbs]
      (await (seed-nouns! dbs))
      (let [{:keys [words]} (await (list-of dbs {:limit 50}))]
        (is (= filed-order (mapv :value words)) "the whole vocabulary, paged off the view"))
      (let [page-1 (await (list-of dbs {:limit 3 :offset 0}))
            page-2 (await (list-of dbs {:limit 3 :offset 3}))]
        (is (= filed-order (into (mapv :value (:words page-1)) (mapv :value (:words page-2))))
            "a page past the first continues the order, none repeated and none skipped"))
      (let [ids (mapv :id (:words (await (list-of dbs {:limit 50}))))
            {:keys [words]} (await (list-of dbs
                                            {:limit 50 :word-ids (shuffle ids)}))]
        (is (= filed-order (mapv :value words)) "a collection-scoped page sorts on the same key"))
      ;; `z` matches Katze and Zug and nothing else here. Ordered by id they
      ;; would come back `der Zug`, `die Katze`.
      (let [{:keys [words]} (await (list-of dbs {:limit 50 :search "z"}))]
        (is (= ["die Katze" "der Zug"] (mapv :value words)) "so does a searched page"))))))

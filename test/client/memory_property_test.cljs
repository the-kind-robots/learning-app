(ns client.memory-property-test
  "Incremental ingest is the same memory as a rebuild (ADR-0016). Whatever
   order documents, new revisions, repeats and deletions arrive in, and
   however they are cut into batches, memory ends up equal to the one built
   in a single batch from the documents as they finally stand; every older
   memory stays what it was; and what screens read of it agrees with the
   retention formula applied to the documents directly."
  (:require
   [adapters.memory :as sut]
   [use-cases.collections :as collections]
   [use-cases.vocabulary :as vocabulary]
   [utils :as utils]
   [cljs.test :refer-macros [deftest is]]
   [clojure.test.check :as tc]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop :include-macros true]))


(def ^:private word-ids
  (mapv #(str "vocab:w" %) (range 90)))


(def ^:private gen-word
  (gen/let [id    (gen/elements word-ids)
            value gen/string-alphanumeric
            ru    gen/string-alphanumeric
            kind  (gen/elements [nil "phrase"])]
    (cond-> {:_id id :type "vocab" :value (str id value) :translation [{:lang "ru" :value ru}]}
      kind (assoc :kind kind))))


(def ^:private gen-review
  (gen/let [n        (gen/choose 0 60)
            word-id  (gen/elements word-ids)
            retained gen/boolean
            day      (gen/choose 1 28)]
    {:_id        (str "review:" n)
     :type       "review"
     :word-id    word-id
     :retained   retained
     :created-at (str "2026-01-" (if (< day 10) (str "0" day) day) "T00:00:00.000Z")}))


(def ^:private gen-collection
  (gen/let [n    (gen/choose 0 4)
            name gen/string-alphanumeric
            ids  (gen/vector (gen/elements word-ids) 0 5)]
    {:_id (str "collection:" n) :type "collection" :name name :word-ids ids :created-at "2026-01-01"}))


(def ^:private gen-example
  (gen/let [n       (gen/choose 0 8)
            word-id (gen/elements word-ids)
            coll    (gen/elements [nil "collection:0" "collection:1"])]
    (cond-> {:_id (str "example:" n) :type "example" :word-id word-id :value "Satz" :translation "фраза"}
      coll (assoc :collection-id coll))))


(def ^:private gen-other
  "A type memory does not hold: the lesson in progress, a task."
  (gen/return {:_id "lesson" :type "lesson"}))


(def ^:private gen-change
  "One thing that can arrive: a new revision of a document, or its deletion
   by id alone."
  (gen/frequency [[6 (gen/one-of [gen-word gen-review gen-collection gen-example gen-other])]
                  [1
                   (gen/let [id (gen/one-of [(gen/elements word-ids)
                                             (gen/fmap #(str "review:" %) (gen/choose 0 60))
                                             (gen/fmap #(str "collection:" %) (gen/choose 0 4))
                                             (gen/fmap #(str "example:" %) (gen/choose 0 8))])]
                     {:_deleted true :_id id})]]))


(defn- revised
  "The changes with a revision each, in the order they happened, and some of
   them delivered twice in a row — as the feed brings a write that
   write-through already applied."
  [changes repeats]
  (let [twice (set repeats)]
    (into []
          (comp (map-indexed (fn [i doc]
                               (let [doc (assoc doc :_rev (str (inc i) "-r"))]
                                 (if (twice i) [doc doc] [doc]))))
                cat)
          changes)))


(defn- final-docs
  "Each document as it last stood: what a rebuild from the database reads."
  [docs]
  (->> docs
       (reduce (fn [by-id doc] (assoc by-id (:_id doc) doc)) {})
       vals
       (remove :_deleted)))


(defn- snapshot
  "Memory as plain data by word id, review histories as vectors. Slot
   numbers are left out: they follow the order ids first arrived in, which
   batching changes."
  [memory]
  (let [plain   (fn [{:keys [ids retained seconds]}]
                  (vec (for [i (range (count ids))]
                         [(aget seconds i) (aget retained i) (aget ids i)])))
        cards   (:cards memory)]
    {:by-id    (into {}
                     (keep (fn [[id slot]]
                             (let [{:keys [word reviews]} (nth cards slot)]
                               (when (or word (pos? (count (:ids reviews))))
                                 [id [word (plain reviews)]]))))
                     (:slot-of memory))
     :examples [(:examples memory) (:examples-by-word memory)]
     :other    [(:collections memory) (:words memory)]}))


(defn- history
  "Each memory the batches go through, oldest first, with its snapshot taken
   when it was made."
  [docs sizes]
  (loop [memory sut/empty-memory
         docs   docs
         [size & sizes] (cycle sizes)
         seen   [[memory (snapshot memory)]]]
    (if (empty? docs)
      seen
      (let [memory (sut/with-docs memory (take size docs))]
        (recur memory (drop size docs) sizes (conj seen [memory (snapshot memory)]))))))


(defn- expected-urgency
  "The retention formula over a word's reviews as documents: the oracle the
   columns are checked against."
  [reviews now-ms]
  (let [reviews (->> reviews
                     (sort-by :_id)
                     (map #(update % :created-at utils/iso->secs))
                     (sort-by :created-at))]
    (if (empty? reviews)
      ##Inf
      (* (reduce (fn [rate [prev curr]]
                   (if (:retained curr)
                     (/ rate (inc (* rate (- (:created-at curr) (:created-at prev)))))
                     (* 2 rate)))
                 0.00231
                 (map vector reviews (rest reviews)))
         (utils/ms->secs (- now-ms (* 1000 (:created-at (last reviews)))))))))


(def ^:private now 1790000000000)


(def ^:private incremental-equals-rebuild
  (prop/for-all [changes (gen/vector gen-change 0 150)
                 repeats (gen/vector (gen/choose 0 149) 0 20)
                 sizes (gen/not-empty (gen/vector (gen/choose 1 120) 1 5))]
                (let [docs        (revised changes repeats)
                      finals      (final-docs docs)
                      rebuilt     (sut/with-docs sut/empty-memory finals)
                      seen        (history docs sizes)
                      incremental (first (peek seen))
                      reviews     (group-by :word-id (filter #(= "review" (:type %)) finals))]
                  (and (= (snapshot rebuilt) (snapshot incremental))
                       ;; An older memory is still what it was.
                       (every? (fn [[memory taken]] (= taken (snapshot memory))) seen)
                       ;; A card holds the word `:slot-of` names its slot for.
                       (every? (fn [[id slot]]
                                 (let [word (:word (nth (:cards incremental) slot))]
                                   (or (nil? word) (= id (:id word)))))
                               (:slot-of incremental))
                       (= (vocabulary/rows rebuilt {} now) (vocabulary/rows incremental {} now))
                       ;; Every word's urgency is the formula over its review documents.
                       (= (into {} (map (fn [w] [(:id w) (expected-urgency (reviews (:id w)) now)]))
                                (vals (:words incremental)))
                          (into {} (map (fn [e] [(:id (:word e)) (vocabulary/urgency-of e now)]))
                                (vocabulary/collection-cards incremental nil)))
                       ;; A collection's lesson draws from its words and its children's that exist.
                       (every? (fn [collection]
                                 (= (set (filter (set (map :id (vals (:words incremental))))
                                                 (collections/scope-word-ids (vals (:collections incremental))
                                                                             (:id collection))))
                                    (set (map (comp :id :word)
                                              (vocabulary/collection-cards incremental collection)))))
                               (vals (:collections incremental)))))))


(deftest incremental-ingest-equals-a-rebuild-from-the-final-documents
  (let [result (tc/quick-check 200 incremental-equals-rebuild :max-size 60)]
    (is (:pass? result) (pr-str (select-keys result [:seed :num-tests :failing-size :shrunk])))))

(ns client.memory-property-test
  "Incremental ingest is the same memory as a rebuild (ADR-0016). Whatever
   order documents, new revisions, repeats and deletions arrive in, and
   however they are cut into batches, memory ends up equal to the one built
   in a single batch from the documents as they finally stand; every older
   memory stays what it was; and what screens read of it agrees with the
   retention formula applied to the documents directly. A snapshot of memory,
   taken again, is the same memory; and with what was stored after it, the
   same as a rebuild."
  (:require
   [adapters.learner.memory :as sut]
   [adapters.learner.snapshot :as snapshot]
   [domain.collections :as collections]
   [ports.learner :as ports]
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
  "A type memory does not have: the lesson in progress, a task."
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
  (let [plain (fn [{:keys [ids retained seconds]}]
                (vec (for [i (range (count ids))]
                       [(aget seconds i) (aget retained i) (aget ids i)])))
        cards (:cards memory)]
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
                       ;; A card has the word `:slot-of` names its slot for.
                       (every? (fn [[id slot]]
                                 (let [word (:word (nth (:cards incremental) slot))]
                                   (or (nil? word) (= id (:id word)))))
                               (:slot-of incremental))
                       (= (vocabulary/rows ports/reads rebuilt {} now) (vocabulary/rows ports/reads incremental {} now))
                       ;; Every word's urgency is the formula over its review documents.
                       (= (into {}
                                (map (fn [w] [(:id w) (expected-urgency (reviews (:id w)) now)]))
                                (vals (:words incremental)))
                          (into {}
                                (map (fn [e] [(:id (:word e)) (vocabulary/urgency-of e now)]))
                                (sut/collection-cards incremental nil)))
                       ;; A collection's lesson draws from its words and its children's that exist.
                       (every? (fn [collection]
                                 (= (set (filter (set (map :id (vals (:words incremental))))
                                                 (collections/scope-word-ids (vals (:collections incremental))
                                                                             (:id collection))))
                                    (set (map (comp :id :word)
                                              (sut/collection-cards incremental collection)))))
                               (vals (:collections incremental)))))))


(deftest incremental-ingest-equals-a-rebuild-from-the-final-documents
  (let [result (tc/quick-check 200 incremental-equals-rebuild :max-size 60)]
    (is (:pass? result) (pr-str (select-keys result [:seed :num-tests :failing-size :shrunk])))))


(defn- fed
  "What the change feed reads from `docs`, the changes in the order the
   database stored them. Read as it happens, it brings every change; read
   later, only each document's last change, at its place."
  [docs latest-only?]
  (if latest-only?
    (let [last-at (into {} (map-indexed (fn [i doc] [(:_id doc) i])) docs)]
      (into [] (keep-indexed (fn [i doc] (when (= i (last-at (:_id doc))) doc))) docs))
    docs))


(def ^:private load-then-feed-equals-a-rebuild
  (prop/for-all
   [changes (gen/vector gen-change 0 120)
    start (gen/choose 0 120)
    read-at (gen/choose 0 120)
    size (gen/choose 1 30)
    order gen/int
    latest-only? gen/boolean]
   ;; The load notes the feed's position at `start`, and reads the
   ;; documents as they stand at `read-at`, not before `start`, in pages
   ;; taken in any order. Then the feed applies everything stored from
   ;; `start` on, in order.
   (let [docs    (revised changes [])
         start   (min start (count docs))
         read-at (min (count docs) (+ start read-at))
         pages   (partition-all size (final-docs (take read-at docs)))
         pages   (sort-by #(hash [order %]) pages)
         loaded  (reduce sut/with-docs sut/empty-memory pages)
         memory  (sut/with-docs loaded (fed (drop start docs) latest-only?))]
     (= (snapshot (sut/with-docs sut/empty-memory (final-docs docs)))
        (snapshot memory)))))


(deftest the-load-then-the-feed-equal-a-rebuild
  (let [result (tc/quick-check 200 load-then-feed-equals-a-rebuild :max-size 60)]
    (is (:pass? result) (pr-str (select-keys result [:seed :num-tests :failing-size :shrunk])))))


(deftest an-edit-that-wins-over-a-deletion-stays
  ;; Device A deleted the word at generation 5, device B edited it at
  ;; generation 3. Replication makes B's edit the winner: a document beats
  ;; a deletion, whatever the generations. The feed brings the winner last,
  ;; and memory keeps the word.
  (let [word   {:_id "vocab:w1" :_rev "1-a" :type "vocab" :value "Haus" :translation [{:lang "ru" :value "дом"}]}
        memory (sut/with-docs sut/empty-memory
                              [word
                               {:_id "vocab:w1" :_rev "5-b" :_deleted true}
                               (assoc word :_rev "3-c" :value "Haus!")])]
    (is (= "Haus!" (:value (sut/word memory "vocab:w1"))))))


(defn- taken-again
  "The memory a start takes from the snapshot of `memory`: encoded, read
   back, and its entries added to an empty memory."
  [memory]
  (let [stored (snapshot/parsed (snapshot/encode memory "u"))]
    (sut/with-position (sut/with-entries sut/empty-memory (snapshot/decoded stored))
                       (:position (:header stored)))))


(def ^:private taken-again-equals-memory
  (prop/for-all [changes (gen/vector gen-change 0 150)
                 sizes (gen/not-empty (gen/vector (gen/choose 1 120) 1 5))]
                (let [memory (first (peek (history (revised changes []) sizes)))
                      memory (sut/with-position memory (count changes))
                      again  (taken-again memory)]
                  (and (= (snapshot memory) (snapshot again))
                       (= (set (sut/entries memory)) (set (sut/entries again)))
                       (= (sut/position memory) (sut/position again))))))


(deftest a-snapshot-taken-again-is-the-same-memory
  (let [result (tc/quick-check 200 taken-again-equals-memory :max-size 60)]
    (is (:pass? result) (pr-str (select-keys result [:seed :num-tests :failing-size :shrunk])))))


(def ^:private snapshot-then-catch-up-equals-a-rebuild
  (prop/for-all
   [changes (gen/vector gen-change 0 120)
    taken-at (gen/choose 0 120)
    latest-only? gen/boolean]
   ;; Memory holds everything stored up to `taken-at` when its snapshot is
   ;; written. A later start takes the snapshot and catches up with what
   ;; was stored after it, read as the feed reads it.
   (let [docs     (revised changes [])
         taken-at (min taken-at (count docs))
         memory   (sut/with-changes sut/empty-memory (take taken-at docs) taken-at)
         started  (sut/with-changes (taken-again memory) (fed (drop taken-at docs) latest-only?) (count docs))]
     (= (snapshot (sut/with-docs sut/empty-memory (final-docs docs)))
        (snapshot started)))))


(deftest a-snapshot-then-a-catch-up-equal-a-rebuild
  (let [result (tc/quick-check 200 snapshot-then-catch-up-equals-a-rebuild :max-size 60)]
    (is (:pass? result) (pr-str (select-keys result [:seed :num-tests :failing-size :shrunk])))))

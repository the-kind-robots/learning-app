(ns client.memory-property-test
  "Incremental ingest is the same memory as a rebuild (ADR-0016). Whatever
   order documents, new revisions, repeats and deletions arrive in, and
   however they are cut into batches, memory ends up equal to the one built
   in a single batch from the documents as they finally stand. An index that
   one path forgets to update, or updates wrongly, shows up here as a
   difference."
  (:require
   [adapters.memory :as sut]
   [cljs.test :refer-macros [deftest is]]
   [clojure.test.check :as tc]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop :include-macros true]))


(def ^:private word-ids
  "Enough words that a batch can move more of them than the list splices one
   at a time (`resort-above`), so both ways of keeping it in order run."
  (mapv #(str "vocab:w" %) (range 90)))


(def ^:private gen-word
  (gen/let [id    (gen/elements word-ids)
            value gen/string-alphanumeric
            ru    gen/string-alphanumeric
            kind  (gen/elements [nil "phrase"])]
    (cond-> {:_id id :type "vocab" :value (str id value) :translation [{:lang "ru" :value ru}]}
      kind (assoc :kind kind))))


(def ^:private gen-review
  (gen/let [n        (gen/choose 0 30)
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
                                             (gen/fmap #(str "review:" %) (gen/choose 0 30))
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


(defn- in-batches
  [docs sizes]
  (loop [memory sut/empty-memory
         docs   docs
         [size & sizes] (cycle sizes)]
    (if (empty? docs)
      memory
      (recur (sut/with-docs memory (take size docs)) (drop size docs) sizes))))


(def ^:private incremental-equals-rebuild
  (prop/for-all [changes (gen/vector gen-change 0 150)
                 repeats (gen/vector (gen/choose 0 149) 0 20)
                 sizes (gen/not-empty (gen/vector (gen/choose 1 120) 1 5))]
                (let [docs (revised changes repeats)]
                  (= (sut/with-docs sut/empty-memory (final-docs docs))
                     (in-batches docs sizes)))))


(deftest incremental-ingest-equals-a-rebuild-from-the-final-documents
  (let [result (tc/quick-check 100 incremental-equals-rebuild :max-size 60)]
    (is (:pass? result) (pr-str (select-keys result [:seed :num-tests :failing-size])))))

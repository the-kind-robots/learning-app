(ns ports.learner
  "The learner's data, as the app reaches it: memory to read, every write, and the active collection.
   `adapters.learner` does the work."
  (:require
   [adapters.learner :as adapter]
   [adapters.learner.memory :as memory]))


(defprotocol Learner
  (memory [l]
    "The learner's data in memory as it stands now, as one value; a caller that compares two reads of it by
     identity needs it. The reads below read it for everything else.")
  (fresh-memory [l]
    "A promise of memory once it has everything stored, which a write or a replication may have just brought.")
  (change! [l ops]
    "Applies `ops`, `[entity id change]` each, in one bulk write; `change` returns the new entity, nil to
     write nothing, or `:entity/deleted`. A promise of true, nil when nothing was written, or false when the write was taken back.")
  (insert! [l entity value]
    "Writes a new `entity` of `value` (`:entity/collection`, `:entity/review` or `:entity/example`). A promise
     of the entity written, for an example whether the learner holds it afterwards.")
  (active-collection [l]
    "The collection active on this device, or nil for «Всё подряд».")
  (set-active-collection! [l id]
    "Makes the collection `id` active on this device; nil makes «Всё подряд» active.")
  (on-change [l f]
    "Calls `f`, with no arguments, whenever memory or its status changes. Returns a function that stops it.")
  (status [l]
    "`:status/unreadable`, `:status/loaded` or `:status/loading`.")
  (storage [l]
    "What the account replicates, opaque to the caller: the learner's databases and the wait for memory's snapshot
     check. `ports.account` hands it to its adapter.")
  (resolve-conflicts! [l pulled-ids]
    "A promise that resolves once the conflicts a replication pass left in the learner's documents are merged.
     `pulled-ids` is what the pass pulled, by document type."))


(defn as-learner
  "The Learner over the adapter's `learner`."
  [learner]
  (reify Learner
    (memory [_] (adapter/current-memory learner))
    (fresh-memory [_] (adapter/fresh-memory learner))
    (change! [_ ops] (adapter/change! learner ops))
    (insert! [_ entity value] (adapter/insert! learner entity value))
    (active-collection [_] (adapter/active-collection learner))
    (set-active-collection! [_ id] (adapter/set-active-collection! learner id))
    (on-change [_ f] (adapter/on-change learner f))
    (status [_] (adapter/status learner))
    (storage [_] (adapter/storage learner))
    (resolve-conflicts! [_ pulled-ids] (adapter/resolve-conflicts! learner pulled-ids))))


(defn ^:async start!
  "Starts the learner's data (`adapters.learner/start!`) and resolves with its Learner."
  []
  (as-learner (await (adapter/start!))))


;;
;; Reads of memory. Each takes the learner.
;;


(defn collection
  "The collection `id` of the learner, or nil."
  [learner id]
  (memory/collection (memory learner) id))


(defn collection-cards
  "A card for every word in scope of `coll`, nil for all: the word with its reviews."
  [learner coll]
  (memory/collection-cards (memory learner) coll))


(defn collection-words
  "The words in scope of `coll`, nil for all."
  [learner coll]
  (memory/collection-words (memory learner) coll))


(defn collections
  "Every collection of the learner."
  [learner]
  (memory/collections (memory learner)))


(defn collections-of
  "The collections that list the word `word-id`."
  [learner word-id]
  (memory/collections-of (memory learner) word-id))


(defn examples-of
  "The examples of the words `word-ids`."
  [learner word-ids]
  (memory/examples-of (memory learner) word-ids))


(defn matching-words
  "The words in scope of `coll` that match `query`."
  [learner coll query]
  (memory/matching-words (memory learner) coll query))


(defn review-history
  "The reviews of the word `word-id`."
  [learner word-id]
  (memory/review-history (memory learner) word-id))


(defn word
  "The word or phrase `id` of the learner, or nil."
  [learner id]
  (memory/word (memory learner) id))


(defn word-count
  "How many words and phrases the learner has."
  [learner]
  (memory/word-count (memory learner)))


(defn words
  "Every word and phrase of the learner."
  [learner]
  (memory/words (memory learner)))

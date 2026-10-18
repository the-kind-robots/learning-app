(ns ports.learner
  "The learner's data, as the app reaches it: memory to read, every write, and
   the active collection; opening its databases; replicating it with the
   account. `adapters.learner` does the work."
  (:require
   [adapters.learner :as learner]
   [adapters.learner.memory :as memory]
   [ports.page :as page]
   [ports.world :as world]))


(defn ^:async start!
  "Starts the learner's data (`adapters.learner/start!`) and resolves with its capabilities, given `world`
   and `page`. What it waits for sits in them (`:learner/initialized`)."
  [{:keys [page world] :as deps}]
  (await (learner/start! (assoc deps
                         :browser {:active?               #(page/active? page)
                                   :on-offline            #(page/on-offline page %)
                                   :on-online             #(page/on-online page %)
                                   :on-visibility-change  #(page/on-visibility-change page %)
                                   :online?               #(page/online? page)
                                   :visible?              #(page/visible? page)}
                         :world   {:now-iso #(world/clock world :now-iso)
                                   :sleep   #(world/clock world :sleep %1 %2)}))))


;;
;; Reads of memory. Each takes the memory value `:learner/memory` hands out.
;;


(defn collection
  "The collection `id` in `memory`, or nil."
  [mem id]
  (memory/collection mem id))


(defn collection-cards
  "A card for every word in scope of `coll`, nil for all: the word with its reviews."
  [mem coll]
  (memory/collection-cards mem coll))


(defn collection-words
  "The words in scope of `coll`, nil for all."
  [mem coll]
  (memory/collection-words mem coll))


(defn collections
  "Every collection in `memory`."
  [mem]
  (memory/collections mem))


(defn collections-of
  "The collections that list the word `word-id`."
  [mem word-id]
  (memory/collections-of mem word-id))


(defn examples-of
  "The examples of the words `word-ids`."
  [mem word-ids]
  (memory/examples-of mem word-ids))


(defn matching-words
  "The words in scope of `coll` that match `query`."
  [mem coll query]
  (memory/matching-words mem coll query))


(defn review-history
  "The reviews of the word `word-id`."
  [mem word-id]
  (memory/review-history mem word-id))


(defn word
  "The word or phrase `id` in `memory`, or nil."
  [mem id]
  (memory/word mem id))


(defn word-count
  "How many words and phrases `memory` holds."
  [mem]
  (memory/word-count mem))


(defn words
  "Every word and phrase in `memory`."
  [mem]
  (memory/words mem))

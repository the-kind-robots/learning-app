(ns ports.learner
  "The learner's data, as the app reaches it: memory to read, every write, and
   the active collection; opening its databases; replicating it with the
   account. `adapters.learner` does the work."
  (:refer-clojure :exclude [exists?])
  (:require
   [adapters.learner :as adapter]
   [adapters.learner.memory :as memory]
   [ports.page :as page]
   [ports.world :as world]))


(defprotocol Learner
  (memory [l]
    "The learner's data in memory as it stands now; the reads below take it.")
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
  (initialized [l]
    "A promise that resolves once memory is loaded and the first sync pass is done, or a minute has passed."))


(defprotocol Account
  (exists? [a]
    "Whether this device has an account.")
  (sync! [a reason]
    "Asks for a replication pass with the account, if there is one.")
  (sync-link [a] [a nonce]
    "A promise of the link another device opens to join this account, nil without one. With a `nonce` the
     opener echoes it back, so whoever showed the link can tell it joined.")
  (on-device-joined [a nonce f]
    "Calls `f` once the device that opened the link carrying `nonce` has joined."))


(defn as-learner
  "The Learner over the adapter's `learner` whose first sync pass is the promise `initialized`."
  [learner initialized]
  (reify Learner
    (memory [_] (adapter/current-memory learner))
    (fresh-memory [_] (adapter/fresh-memory learner))
    (change! [_ ops] (adapter/change! learner ops))
    (insert! [_ entity value] (adapter/insert! learner entity value))
    (active-collection [_] (adapter/active-collection learner))
    (set-active-collection! [_ id] (adapter/set-active-collection! learner id))
    (on-change [_ f] (adapter/on-change learner f))
    (status [_] (adapter/status learner))
    (initialized [_] initialized)))


(defn as-account
  "The Account over what the adapter's sync started with."
  [sync]
  (reify Account
    (exists? [_] (:exists? sync))
    (sync! [_ reason] ((:sync! sync) reason))
    (sync-link [_] ((:sync-link sync)))
    (sync-link [_ nonce] ((:sync-link sync) nonce))
    (on-device-joined [_ nonce f] ((:on-device-joined sync) nonce f))))


(defn ^:async start!
  "Starts the learner's data (`adapters.learner/start!`), given `world` and `page`, and resolves with
   `{:learner :account}`, a Learner and an Account."
  [{:keys [page world]}]
  (let [{:keys [initialized learner sync]}
        (await (adapter/start! {:browser {:active?              #(page/active? page)
                                          :on-offline           #(page/on-offline page %)
                                          :on-online            #(page/on-online page %)
                                          :on-visibility-change #(page/on-visibility-change page %)
                                          :online?              #(page/online? page)
                                          :visible?             #(page/visible? page)}
                                  :world   {:now-iso #(world/clock world :now-iso)
                                            :sleep   #(world/clock world :sleep %1 %2)}}))]
    {:account (as-account sync)
     :learner (as-learner learner initialized)}))


;;
;; Reads of memory. Each takes the memory value `memory` hands out.
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

(ns client.support.learner
  "The learner port over test databases, with memory loaded from them and
   following their change feeds, as the app runs it."
  (:require
   [adapters.learner :as adapter]
   [adapters.learner.loader :as loader]
   [adapters.learner.memory :as memory]
   [domain.examples :as examples]
   [ports.learner :as learner]))


(defn ^:async started
  "Starts memory over `dbs` into a new store, and the Learner over
   both. Resolves once memory is fully loaded, with `{:learner :stop :store}`:
   the Learner, a function that stops following the feeds, and
   the store."
  [dbs]
  (let [store (atom {:learner/memory memory/empty-memory})
        stop  (await (loader/start! dbs store))]
    {:learner (learner/as-learner
               (adapter/new-learner {:databases dbs :store store}))
     :stop    stop
     :store   store}))


(defn of-memory
  "A Learner that holds `memory`, a memory value, and offers nothing else."
  [memory]
  (reify learner/Learner
    (memory [_] memory)))


(defn with-active-collection
  "`learner`, but the collection active is the one `active-collection` returns, a function of no arguments."
  [learner active-collection]
  (reify learner/Learner
    (memory [_] (learner/memory learner))
    (fresh-memory [_] (learner/fresh-memory learner))
    (change! [_ ops] (learner/change! learner ops))
    (insert! [_ entity value] (learner/insert! learner entity value))
    (active-collection [_] (active-collection))
    (set-active-collection! [_ id] (learner/set-active-collection! learner id))
    (on-change [_ f] (learner/on-change learner f))
    (status [_] (learner/status learner))
    (storage [_] (learner/storage learner))
    (resolve-conflicts! [_ pulled-ids] (learner/resolve-conflicts! learner pulled-ids))))


(defn ^:async with-learner
  "Calls `f` with what `started` resolves with, and stops following the
   feeds once the promise `f` returns settles."
  [dbs f]
  (let [{:keys [stop] :as started} (await (started dbs))]
    (try
      (await (f started))
      (finally
       (stop)))))


(defn ^:async caught-up
  "Resolves once the memory in `store` has what a test seeded past the
   learner (`adapters.learner.loader/catch-up!`)."
  [_dbs store]
  (await (loader/catch-up! store)))


(defn missing-in
  "The pairs `memory` is missing an example for, by the rule the example
   backfill uses (`domain.examples/missing-pairs`)."
  [memory]
  (let [collections (memory/collections memory)]
    (into []
          (mapcat (fn [{:keys [id] :as entry}]
                    (examples/missing-pairs entry
                                            (filter #(some #{id} (:word-ids %)) collections)
                                            (memory/examples-of memory [id]))))
          (memory/words memory))))

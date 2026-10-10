(ns client.support.learner
  "The learner port over test databases, with memory loaded from them and
   following their change feeds, as the app runs it."
  (:require
   [adapters.learner.loader :as loader]
   [adapters.learner.memory :as memory]
   [application]
   [domain.examples :as examples]
   [nexus.registry :as nxr]
   [ports.learner :as learner]))


(defn store-dispatch
  "A dispatch that runs the memory loader's effects on `store`, as the app
   runs them. The store shows no screen, so the refresh those effects ask for
   changes nothing."
  [store]
  (nxr/register-system->state! #(-> % :store deref))
  (let [system {:capabilities {:clock   {:clock/now-ms (constantly 0)}
                               :learner {:learner/active-collection (constantly nil)}}
                :store        store}]
    (fn [effects]
      (nxr/dispatch system {} effects))))


(defn ^:async started
  "Starts memory over `dbs` into a new store, and the learner port over both.
   Resolves once memory is fully loaded, with `{:learner :stop :store}`:
   the port's functions, a function that stops following the feeds, and the
   store."
  [dbs clock]
  (let [store (atom {:learner/memory memory/empty-memory})
        stop  (await (loader/start! dbs store (store-dispatch store)))]
    {:learner (learner/start! {:clock clock :db dbs :store store})
     :stop    stop
     :store   store}))


(defn ^:async with-learner
  "Calls `f` with what `started` resolves with, and stops following the
   feeds once the promise `f` returns settles."
  [dbs clock f]
  (let [{:keys [stop] :as started} (await (started dbs clock))]
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
   fetcher uses (`domain.examples/missing-pairs`)."
  [memory]
  (let [collections (memory/collections memory)]
    (into []
          (mapcat (fn [{:keys [id] :as entry}]
                    (examples/missing-pairs entry
                                            (filter #(some #{id} (:word-ids %)) collections)
                                            (memory/examples-of memory [id]))))
          (memory/words memory))))

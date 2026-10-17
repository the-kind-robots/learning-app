(ns ports.learner
  "The learner's data, as the app reaches it: memory to read, every write, and
   the active collection; opening its databases; replicating it with the
   account; and backing it up. `adapters.learner` does the work."
  (:require
   [adapters.learner :as learner]
   [runtime.system :as system]))


(defn ^:async start!
  "Starts the learner's data (`adapters.learner/start!`) and resolves with
   `{:capabilities :stops}`. `world` and `browser` are given. The databases
   open, memory is read and followed, and sync starts, all behind this one
   start; what it waits for sits in the capabilities (`:learner/initialized`)."
  [deps]
  (await (learner/start! deps)))


(defn stop!
  "Stops what `start!` started, in reverse order."
  [{:keys [stops]}]
  (system/stop! stops))

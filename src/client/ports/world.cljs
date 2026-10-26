(ns ports.world
  "The world the app lives in, as the use cases reach it: the time, the
   timers and chance. `adapters.world` does the work."
  (:require
   [adapters.world :as world]))


(defprotocol World
  (clock [w effect]
    [w effect a]
    [w effect a b]
    "The clock, by what is asked of it. `(clock w :now-ms)` and `(clock w :now-iso)` tell the time, in milliseconds
     and as an ISO 8601 string. `(clock w :after ms f)` calls `f` once `ms` have passed and returns a function that
     cancels the call. `(clock w :sleep ms signal)` is a promise that resolves with nil after `ms`, or at once when
     `signal` aborts.")
  (random [w]
    "A number in [0, 1)."))


(defn start!
  []
  (reify World
    (clock [_ effect]
      (case effect
        :now-iso (world/now-iso)
        :now-ms  (world/now-ms)
        (throw (ex-info "Unknown clock effect" {:effect effect}))))
    (clock [_ effect a b]
      (case effect
        :after (world/after a b)
        :sleep (world/sleep a b)
        (throw (ex-info "Unknown clock effect" {:effect effect}))))
    (random [_] (world/random))))

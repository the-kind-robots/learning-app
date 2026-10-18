(ns ports.world
  "The world the app lives in, as the use cases reach it: the clock, which
   tells the time and keeps the timers, and chance. `adapters.world` does the
   work."
  (:require
   [adapters.world :as world]))


(defn start!
  []
  {:world/clock  {:clock/after   world/after
                  :clock/now-iso world/now-iso
                  :clock/now-ms  world/now-ms
                  :clock/sleep   world/sleep}
   :world/random world/random})

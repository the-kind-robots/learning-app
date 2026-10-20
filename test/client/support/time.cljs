(ns client.support.time
  (:require
   [ports.world :as world]
   [utils :as utils]))


(def test-now-iso "2024-08-20T10:00:00.000Z")


(def now-iso
  (constantly test-now-iso))


(def now-ms
  (constantly (utils/iso->ms (now-iso))))


(def world
  "A World whose clock stands still at `test-now-iso`; it has no timers and no chance."
  (reify world/World
    (clock [_ effect]
      (case effect
        :now-ms  (now-ms)
        :now-iso (now-iso)))
    (clock [_ _ _ _] (throw (ex-info "This world has no timers" {})))
    (random [_] (throw (ex-info "This world has no chance" {})))))

(ns ports.clock
  (:require
   [utils :as utils]))


(defn start!
  [_deps]
  {:clock/now-iso utils/now-iso
   :clock/now-ms  utils/now-ms})

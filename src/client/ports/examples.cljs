(ns ports.examples
  (:require
   [adapters.examples :as examples]))


(defn start!
  [{:keys [clock db]}]
  {:examples/find     (fn find
                        [word-id]
                        (examples/find db word-id))
   :examples/list     (fn list
                        [word-ids]
                        (examples/list db word-ids))
   :examples/remove!  (fn remove!
                        [example-id]
                        (examples/remove! db example-id))
   :examples/request! (fn request!
                        [word-id]
                        (examples/request! db clock word-id))})

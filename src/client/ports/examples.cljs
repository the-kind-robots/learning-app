(ns ports.examples
  (:require
   [adapters.examples :as examples]))


(defn start!
  [{:keys [clock db]}]
  {:examples/find     (fn find
                        [word-id collection-id]
                        (examples/find db word-id collection-id))
   :examples/list     (fn list
                        [word-ids collection-id]
                        (examples/list db word-ids collection-id))
   :examples/purge-by-collection! (fn purge-by-collection!
                                    [collection-id]
                                    (examples/purge-by-collection! db collection-id))
   :examples/remove!  (fn remove!
                        [example-id]
                        (examples/remove! db example-id))
   :examples/request! (fn request!
                        [word-id collection-id collection-name]
                        (examples/request! db clock word-id collection-id collection-name))})

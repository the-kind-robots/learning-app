(ns ports.examples
  (:require
   [adapters.examples :as examples]))


(defn start!
  [{:keys [clock db]}]
  {:examples/list           (fn list
                              [word-ids]
                              (examples/list db word-ids))
   :examples/of-word        (fn of-word
                              [word-id]
                              (examples/of-word db word-id))
   :examples/purge-by-collection! (fn purge-by-collection!
                                    [collection-id]
                                    (examples/purge-by-collection! db collection-id))
   :examples/purge-by-word! (fn purge-by-word!
                              [word-id]
                              (examples/purge-by-word! db word-id))
   :examples/remove!        (fn remove!
                              [example-id]
                              (examples/remove! db example-id))
   :examples/request!       (fn request!
                              [requests]
                              (examples/request! db clock requests))})

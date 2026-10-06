(ns client.support.replication
  "Replication between two test databases, as a pass brings another
   device's documents.")


(defn ^:async replicated!
  "Replicates `source` into `target` once. Resolves with how many documents
   it wrote."
  [source target]
  (let [^js result (await (.. ^js source -replicate (to target)))]
    (.-docs_written result)))

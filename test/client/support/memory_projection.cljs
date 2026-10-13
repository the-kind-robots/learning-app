(ns client.support.memory-projection
  "Memory as plain data that compares by value.")


(defn of
  "Memory as plain data by word id, review histories as vectors. Slot numbers are left out: they follow the order ids
   first arrived in, which batching changes."
  [memory]
  (let [plain (fn [{:keys [ids retained seconds]}]
                (vec (for [i (range (count ids))]
                       [(aget seconds i) (aget retained i) (aget ids i)])))
        cards (:cards memory)]
    {:by-id    (into {}
                     (keep (fn [[id slot]]
                             (let [{:keys [word reviews]} (nth cards slot)]
                               (when (or word (pos? (count (:ids reviews))))
                                 [id [word (plain reviews)]]))))
                     (:slot-of memory))
     :examples [(:examples memory) (:examples-by-word memory)]
     :other    [(:collections memory) (:words memory)]}))

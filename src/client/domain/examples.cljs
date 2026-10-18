(ns domain.examples
  "Which examples a read sees, and which pairs of entry and collection have
   none. A lesson and the example backfill read the same rule.")


(defn visible-in
  "The `examples` a read in `collection-id` sees. A named collection sees
   only the examples made for it; nil, outside every collection, sees all."
  [collection-id examples]
  (filterv #(or (nil? collection-id) (= collection-id (:collection-id %))) examples))


(defn missing-pairs
  "The pairs of `entry` that have no example, `{:collection-id :collection-name :word}`; an entry in no
   collection misses one only without any example, as `{:word entry}`."
  [entry collections examples]
  (if (seq collections)
    (into []
          (comp (filter #(empty? (visible-in (:id %) examples)))
                (map (fn [{:keys [id name]}]
                       {:collection-id id :collection-name name :word entry})))
          collections)
    (when (empty? examples)
      [{:word entry}])))

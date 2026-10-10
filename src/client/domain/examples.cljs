(ns domain.examples
  "Which examples a read sees, and which pairs of entry and collection have
   none. A lesson and the example fetcher read the same rule.")


(defn visible-in
  "The `examples` a read in `collection-id` sees. A named collection sees
   only the examples made for it; nil, outside every collection, sees all."
  [collection-id examples]
  (filterv #(or (nil? collection-id) (= collection-id (:collection-id %))) examples))


(defn missing-pairs
  "The pairs of `entry` that have no example, as `{:collection-id
   :collection-name :word}`. `collections` are the ones that name the entry.
   An entry in no collection misses one only when it has no example at all,
   and its pair is `{:word entry}`."
  [entry collections examples]
  (if (seq collections)
    (into []
          (comp (filter #(empty? (visible-in (:id %) examples)))
                (map (fn [{:keys [id name]}]
                       {:collection-id id :collection-name name :word entry})))
          collections)
    (when (empty? examples)
      [{:word entry}])))

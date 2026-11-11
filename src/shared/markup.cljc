(ns markup
  "Markup as data, walked once before it is rendered.")


(defn walk
  "`markup` with every node `replacement` answers for put in its place and walked in turn; `replacement` answers
   nil for a node it leaves. A vector or map with no such node is returned as it came, so a render copies only the
   branches that changed."
  [replacement markup]
  (if-some [replaced (replacement markup)]
    (walk replacement replaced)
    (cond
      (or (vector? markup) (map? markup))
      (reduce-kv (fn [coll k v]
                   (let [v' (walk replacement v)]
                     (if (identical? v v')
                       coll
                       (assoc coll k v'))))
                 markup
                 markup)

      (seq? markup)
      (doall (map #(walk replacement %) markup))

      :else
      markup)))

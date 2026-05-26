(ns adapters.active-collection)


(def ^:private id-key "active-collection-id")


(defn active-collection-id
  "Returns the active collection id, or nil if no named collection is active
   (i.e. the user is on the implicit main card)."
  []
  (js/localStorage.getItem id-key))


(defn set-active-collection!
  "Pass nil to clear (= back to main)."
  [collection-id]
  (if collection-id
    (js/localStorage.setItem id-key collection-id)
    (js/localStorage.removeItem id-key)))

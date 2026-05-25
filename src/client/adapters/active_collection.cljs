(ns adapters.active-collection)


(defn active-collection-id
  "Returns the active collection id, or nil if no named collection is active
   (i.e. the user is on the implicit main card)."
  []
  (js/localStorage.getItem "active-collection-id"))


(defn set-active-collection!
  "Pass nil to clear (= back to main)."
  [collection-id]
  (if collection-id
    (js/localStorage.setItem "active-collection-id" collection-id)
    (js/localStorage.removeItem "active-collection-id")))

(ns pages.home.presenter)


(defn- active-collection-props
  "{:id ... :name ...} for a named collection, nil when the implicit main
  card is active."
  [state]
  (when-let [id (:home/active-coll-id state)]
    {:id   id
     :name (:home/active-coll-name state)}))


(defn- suggestions-props
  [state]
  (when-let [s (:home/suggestions state)]
    {:items  (:suggestions/items s)
     :active (:suggestions/active s)}))


(defn- form-props
  [state]
  {:add-error   (:home/add-error state)
   :suggestions (suggestions-props state)
   :translation (:home/translation state)
   :word        (:home/word state)})


(defn page-props
  [state]
  {:active-collection (active-collection-props state)
   :empty-vocab? (boolean (:home/empty-vocab? state))
   :form (form-props state)})

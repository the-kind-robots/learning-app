(ns pages.home.presenter
  (:require
   [domain.phrase :as phrase]))


(defn- active-collection-props
  "{:id ... :name ...} for a named collection, nil when the implicit main
  card is active."
  [state]
  (when-let [id (:home/active-coll-id state)]
    {:id   id
     :name (:home/active-coll-name state)}))


(defn- suggestion-props
  "Each entry carries its own highlight. The view used to hold the comparison,
   against an item it had already decorated — the two shapes never matched and
   nothing was ever marked (#412). Position against the active index is the one
   thing that decides it, and it is decided here."
  [active-idx idx item]
  (assoc item
         :active? (= idx active-idx)
         :phrase? (phrase/phrase-suggestion? item)))


(defn- suggestions-props
  [state]
  (when-let [{:suggestions/keys [items active-idx]} (:home/suggestions state)]
    {:items (vec (map-indexed (partial suggestion-props active-idx) items))}))


(def ^:private mode-copy
  "The mode has no control of its own: the legend and the label are how the
   form says which of the two it is about to save."
  {:phrase {:legend      "Добавить фразу"
            :placeholder "Новая фраза"
            :value-label "Фраза (немецкий)"}
   :word   {:legend      "Добавить слово"
            :placeholder "Новое слово"
            :value-label "Слово (немецкий)"}})


(defn- form-props
  [state]
  (let [mode (phrase/add-mode (:home/word state)
                              (:suggestions/items (:home/suggestions state))
                              (:home/mode-override state))]
    {:add-error   (:home/add-error state)
     :copy        (mode-copy mode)
     :suggestions (suggestions-props state)
     :translation (:home/translation state)
     :word        (:home/word state)}))


(defn page-props
  [state]
  {:active-collection (active-collection-props state)
   :empty-vocab? (boolean (:home/empty-vocab? state))
   :form (form-props state)})

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
  [item]
  (assoc item :phrase? (phrase/phrase-suggestion? item)))


(defn- suggestions-props
  [state]
  (when-let [s (:home/suggestions state)]
    {:items  (mapv suggestion-props (:suggestions/items s))
     :active (:suggestions/active s)}))


(def ^:private mode-copy
  {:phrase {:chip-label  "фраза"
            :legend      "Добавить фразу"
            :placeholder "Новая фраза"
            :value-label "Фраза (немецкий)"}
   :word   {:chip-label  "слово"
            :legend      "Добавить слово"
            :placeholder "Новое слово"
            :value-label "Слово (немецкий)"}})


(defn- form-props
  [state]
  (let [mode (phrase/add-mode (:home/word state)
                              (:suggestions/items (:home/suggestions state))
                              (:home/mode-override state))]
    {:add-error    (:home/add-error state)
     :add-warning  (:home/add-warning state)
     :copy         (mode-copy mode)
     :phrase-mode? (= :phrase mode)
     :show-chip?   (or (= :phrase mode) (some? (:home/mode-override state)))
     :suggestions  (suggestions-props state)
     :translation  (:home/translation state)
     :word         (:home/word state)}))


(defn page-props
  [state]
  {:active-collection (active-collection-props state)
   :empty-vocab? (boolean (:home/empty-vocab? state))
   :form (form-props state)})

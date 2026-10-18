(ns pages.home.presenter
  (:require
   [domain.phrase :as phrase]))


(defn- suggestion-props
  "Each entry carries its own highlight: position against the active index is the one thing that decides it,
   and it is decided here, not in the view."
  [active-idx idx item]
  (assoc item
         :active? (= idx active-idx)
         :phrase? (phrase/phrase-suggestion? item)))


(defn- suggestions-props
  [state]
  (when-let [{:suggestions/keys [items active-idx]} (:home/suggestions state)]
    {:items (vec (map-indexed (partial suggestion-props active-idx) items))}))


(defn- form-props
  [state]
  (let [mode  (phrase/add-mode (:home/word state)
                               (:suggestions/items (:home/suggestions state))
                               (:home/mode-override state))
        error (:home/add-error state)]
    {:error                (case error
                             nil                 nil
                             :failure/empty-translations :error/empty-translations
                             :error/save-failed)
     :phrase?              (= :phrase mode)
     :suggestions          (suggestions-props state)
     :translation          (:home/translation state)
     :translation-invalid? (= :failure/empty-translations error)
     :word                 (:home/word state)}))


(defn page-props
  [state]
  {;; {:id ... :name ...} for a named collection, nil when the implicit main
   ;; card is active.
   :active-collection (when-let [id (:home/active-coll-id state)]
                        {:id   id
                         :name (:home/active-coll-name state)})
   :empty-vocab? (boolean (:home/empty-vocab? state))
   :form (form-props state)})

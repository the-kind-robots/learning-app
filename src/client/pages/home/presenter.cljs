(ns pages.home.presenter
  (:require
   [domain.phrase :as phrase]
   [domain.vocabulary :as vocabulary]))


(defn- suggestion-props
  "Each entry carries its own highlight: position against the active index is the one thing that decides it,
   and it is decided here, not in the view."
  [word active-idx idx item]
  (assoc item
         :active? (= idx active-idx)
         :completes? (vocabulary/completes? word item)
         :phrase? (phrase/phrase-suggestion? item)))


(defn- suggestions-props
  [state]
  (when-let [{:suggestions/keys [items active-idx]} (:home/suggestions state)]
    {:items (vec (map-indexed (partial suggestion-props (:home/word state) active-idx) items))}))


(defn- form-props
  [state]
  (let [items (:suggestions/items (:home/suggestions state))
        mode  (phrase/add-mode (:home/word state)
                               (mapv items (vocabulary/completing-indexes (:home/word state) items))
                               (:home/mode-override state))
        error (:home/add-error state)]
    {:error                (case error
                             nil                 nil
                             :empty-translations :error/empty-translations
                             :error/save-failed)
     :phrase?              (= :phrase mode)
     :suggestions          (suggestions-props state)
     :translation          (:home/translation state)
     :translation-invalid? (= :empty-translations error)
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

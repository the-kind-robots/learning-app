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


(def ^:private mode-wording
  "The mode has no control of its own: the legend and the label are how the
   form says which of the two it is about to save. A failed save is the app's
   fault and nothing the user does fixes it, so the text says only that (#313)."
  {:phrase {:legend      "Добавить фразу"
            :placeholder "Новая фраза"
            :save-failed "Фраза не сохранилась: в приложении сбой, и это не ваша ошибка."
            :value-label "Фраза (немецкий)"}
   :word   {:legend      "Добавить слово"
            :placeholder "Новое слово"
            :save-failed "Слово не сохранилось: в приложении сбой, и это не ваша ошибка."
            :value-label "Слово (немецкий)"}})


(defn- error-text
  "Every add error is said in words; a border alone left a failed save looking
   like nothing happened (#313)."
  [wording error]
  (case error
    nil nil
    :empty-translations "Добавьте перевод."
    (:save-failed wording)))


(defn- form-props
  [state]
  (let [mode    (phrase/add-mode (:home/word state)
                                 (:suggestions/items (:home/suggestions state))
                                 (:home/mode-override state))
        wording (mode-wording mode)
        error   (:home/add-error state)]
    {:error-text  (error-text wording error)
     :legend      (:legend wording)
     :placeholder (:placeholder wording)
     :suggestions (suggestions-props state)
     :translation (:home/translation state)
     :translation-invalid? (= :empty-translations error)
     :value-label (:value-label wording)
     :word        (:home/word state)}))


(defn page-props
  [state]
  ;; `:home/empty-vocab?` is nil until memory is ready: then neither the
  ;; invitation of an empty vocabulary nor the ways into it are shown.
  {:active-collection (active-collection-props state)
   :actions-hidden?   (not (false? (:home/empty-vocab? state)))
   :empty-vocab?      (true? (:home/empty-vocab? state))
   :form              (form-props state)})

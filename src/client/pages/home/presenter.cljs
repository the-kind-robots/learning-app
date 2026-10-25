(ns pages.home.presenter
  (:require
   [domain.phrase :as phrase]
   [domain.vocabulary :as vocabulary]))


(defn- suggestion-props
  "Each entry carries its own highlight: position against the active index is the one thing that decides it,
   and it is decided here, not in the view."
  [word active-idx idx item]
  (assoc item
         :active?    (= idx active-idx)
         :completes? (vocabulary/completes? word item)
         :phrase?    (phrase/phrase-suggestion? item)))


(defn- suggestions-props
  "The marked row is the reader's while it still completes the word, else the first row that does (#584);
   the arrows walk only the rows that complete it and stop at the ends."
  [state]
  (when-let [{:suggestions/keys [items active-idx]} (:home/suggestions state)]
    (let [word   (:home/word state)
          idxs   (vocabulary/completing-indexes word items)
          marked (if (some #{active-idx} idxs) active-idx (first idxs))]
      {:active-idx   marked
       :items        (vec (map-indexed (partial suggestion-props word marked) items))
       :next-idx     (or (first (filter #(> % (or marked -1)) idxs)) marked)
       :previous-idx (or (last (filter #(< % (or marked 0)) idxs)) marked)})))


(defn- form-props
  [state]
  (let [word  (:home/word state)
        pick  (:home/pick state)
        error (:home/add-error state)]
    {:error       (case error
                    nil nil
                    :failure/empty-translations :error/empty-translations
                    :error/save-failed)
     :phrase?     (phrase/phrase-value?
                   word
                   (phrase/dictionary-pos word pick (:suggestions/items (:home/suggestions state))))
     ;; Only a picked suggestion's part of speech is added with the word; without one the word decides.
     :pos         (phrase/dictionary-pos word pick nil)
     :suggestions (suggestions-props state)
     :translation (:home/translation state)
     :translation-invalid? (= :failure/empty-translations error)
     :word        (:home/word state)}))


(defn page-props
  [state]
  {;; {:id ... :name ...} for a named collection, nil when the implicit main
   ;; card is active.
   :active-collection (when-let [id (:home/active-coll-id state)]
                        {:id   id
                         :name (:home/active-coll-name state)})
   :empty-vocab? (boolean (:home/empty-vocab? state))
   :form (form-props state)})

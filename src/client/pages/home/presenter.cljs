(ns pages.home.presenter
  (:require
   [clojure.string :as str]
   [domain.phrase :as phrase]
   [domain.vocabulary :as vocabulary]))


(defn- suggestion-props
  "Each entry carries its own highlight: position against the active index is the one thing that decides it,
   and it is decided here, not in the view. Its `:suggestion-item/selection` is what picking it hands the
   actions."
  [word active-idx idx {:keys [exact? lemma pos translations] :as item}]
  {:suggestion-item/active?    (= idx active-idx)
   :suggestion-item/completes? (vocabulary/completes? word item)
   :suggestion-item/exact?     exact?
   :suggestion-item/lemma      lemma
   :suggestion-item/phrase?    (phrase/phrase-suggestion? item)
   :suggestion-item/selection  {:lemma        lemma
                                :pos          pos
                                ;; The text the entry puts into the translation field: one translation a line,
                                ;; as the field is read when the word is added.
                                :translations (->> translations
                                                   (map str/trim)
                                                   (remove str/blank?)
                                                   (str/join "\n"))}})


(defn- suggestions-props
  "The marked row is the reader's while it still completes the word, else the first row whose lemma the word
   begins (#584): a row found only through a word form waits for an arrow (#711). The arrows walk only the rows
   that complete the word and stop at the ends; from no mark either one marks the first."
  [state]
  (when-let [{:suggestions/keys [items active-idx]} (:home/suggestions state)]
    (let [word   (:home/word state)
          idxs   (vocabulary/completing-indexes word items)
          marked (if (some #{active-idx} idxs)
                   active-idx
                   (vocabulary/leading-index word items))
          rows   (vec (map-indexed (partial suggestion-props word marked) items))]
      {:items        rows
       :marked       (some->> marked (get rows) :suggestion-item/selection)
       :next-idx     (or (first (filter #(> % (or marked -1)) idxs)) marked)
       :previous-idx (or (last (filter #(< % (or marked 0)) idxs)) marked (first idxs))})))


(defn- form-props
  [state]
  (let [word        (:home/word state)
        pick        (:home/pick state)
        error       (:home/add-error state)
        items       (:suggestions/items (:home/suggestions state))
        suggestions (suggestions-props state)]
    {:add-form/error       (case error
                             nil nil
                             :failure/empty-translations :error/empty-translations
                             :error/save-failed)
     :add-form/phrase?     (phrase/phrase-value? word (phrase/dictionary-pos word pick items))
     ;; Only a picked suggestion's part of speech is added with the word; without one the word decides.
     :add-form/pos         (phrase/dictionary-pos word pick nil)
     :add-form/suggestions suggestions
     ;; A typed or picked translation is the user's. Until then the field shows the translation of the row
     ;; whose lemma the word begins, the one the list marks on its own; a row found through a word form is
     ;; another word (#711).
     :add-form/translation (if (:home/translation-typed? state)
                             (:home/translation state)
                             (or (when-not (str/blank? word)
                                   (some->> (vocabulary/leading-index word items)
                                            (get (:items suggestions))
                                            :suggestion-item/selection
                                            :translations))
                                 ""))
     :add-form/translation-invalid? (= :failure/empty-translations error)
     :add-form/word        (:home/word state)}))


(defn vocabulary?
  "Whether the vocabulary has words: only then home shows the lesson and words buttons."
  [state]
  (not (:home/empty-vocab? state)))


(defn page-props
  [state]
  {;; The heading of a named collection, nil when the implicit main card is active.
   :active-collection (when (:home/active-coll-id state)
                        {:collection-heading/name (:home/active-coll-name state)})
   :empty-vocab? (not (vocabulary? state))
   :form (form-props state)})

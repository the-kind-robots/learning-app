(ns use-cases.phrase
  (:require
   [clojure.string :as str]
   [domain.phrase :as domain]
   [domain.vocabulary :as vocabulary]))


(defn ^:async find-duplicate
  "Find an existing phrase doc with the same value, or nil."
  [{:keys [progress-store]} value]
  (await ((:progress-store/find-phrase-by-value progress-store) value)))


(defn ^:async add!
  "Adds a phrase with an initial review. The translation stays one whole
   entry — never split on punctuation — and no example fetch is queued: a
   phrase is its own example. Returns {:word-id id :created? bool} or
   {:error :empty-translations}."
  [{:keys [collections progress-store] :as capabilities} value translation]
  (let [translation (domain/collapsed translation)]
    (if (str/blank? translation)
      {:error :empty-translations}
      (let [existing      (await (find-duplicate capabilities value))
            collection-id ((:collections/active-id collections))]
        (if existing
          (let [merged (vocabulary/merge-translations
                        (:translation existing)
                        [(domain/translation-entry translation)])]
            (await ((:progress-store/save-word! progress-store)
                    (assoc existing :translation merged)))
            (when collection-id
              (await ((:collections/add-word! collections) (:_id existing) collection-id)))
            {:word-id (:_id existing) :created? false})
          (let [phrase       (domain/new-phrase value translation)
                {:keys [id]} (await ((:progress-store/save-word! progress-store) phrase))]
            (await ((:progress-store/save-review! progress-store) id true translation))
            (when collection-id
              (await ((:collections/add-word! collections) id collection-id)))
            {:word-id id :created? true}))))))

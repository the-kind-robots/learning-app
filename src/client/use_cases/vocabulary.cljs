(ns use-cases.vocabulary
  (:refer-clojure :exclude [list count get])
  (:require
   [domain.vocabulary :as domain]))


(defn ^:async find-duplicate
  "Find an existing vocab doc whose normalized value matches `normalized`."
  [{:keys [progress-store]} normalized]
  (await ((:progress-store/find-word-by-normalized-value progress-store) normalized)))


(defn ^:async add!
  "Adds a new vocabulary word with an initial review.
   If a duplicate exists (case-insensitive, article-stripped), merges translations.
   Returns {:word-id id :created? true/false}."
  [{:keys [progress-store] :as capabilities} value translation]
  (let [parsed     (domain/parse-translations translation)
        normalized (domain/normalize-value value)]
    (if (empty? parsed)
      {:error :empty-translations}
      (let [existing (await (find-duplicate capabilities normalized))]
        (if existing
          (let [merged  (domain/merge-translations (:translation existing) parsed)
                updated (assoc existing :translation merged)]
            (await ((:progress-store/save-word! progress-store) updated))
            {:word-id (:_id existing) :created? false})
          (let [word (domain/new-word value parsed)
                {:keys [id]} (await ((:progress-store/save-word! progress-store) word))]
            (await ((:progress-store/save-review! progress-store) id true translation))
            {:word-id id :created? true}))))))


(defn find-duplicate-by-value
  "Return existing vocab doc whose normalized value matches value."
  [capabilities value]
  (find-duplicate capabilities (domain/normalize-value value)))


(defn ^:async get
  "Returns a word row by id, or nil if not found."
  [{:keys [progress-store]} word-id]
  (await ((:progress-store/get-word progress-store) word-id)))


(defn ^:async list
  "Returns vocabulary rows with retention levels."
  [{:keys [progress-store]} opts]
  (await ((:progress-store/list-words progress-store) opts)))


(defn ^:async count
  "Returns the total number of vocabulary words."
  [{:keys [progress-store]}]
  (await ((:progress-store/count-words progress-store))))


(defn ^:async update!
  "Updates a word's translation. Returns updated row, or nil if not found."
  [{:keys [progress-store] :as capabilities} word-id translation]
  (when-let [word (await ((:progress-store/get-word progress-store) word-id))]
    (let [updated (domain/update-word word translation)]
      (await ((:progress-store/save-word! progress-store) updated))
      (await (get capabilities word-id)))))


(defn ^:async delete!
  "Deletes a word and all its associated reviews and examples.
   No-op if word doesn't exist."
  [{:keys [progress-store]} word-id]
  (await ((:progress-store/delete-word! progress-store) word-id)))


(defn add-review
  "Creates a review document for a word and updates its retention model."
  [{:keys [progress-store]} word-id retained translation]
  ((:progress-store/save-review! progress-store) word-id retained translation))

(ns use-cases.vocabulary
  (:refer-clojure :exclude [list count get])
  (:require
   [domain.vocabulary :as domain]))


(defn ^:async find-duplicate
  "Find an existing vocab doc whose normalized value matches `normalized`."
  [{:keys [progress-store]} normalized]
  (await ((:progress-store/find-word-by-normalized-value progress-store) normalized)))


(defn ^:async add!
  "Adds a new vocabulary word with an initial review and queues a
   collection-scoped example fetch. If a duplicate exists (case-insensitive,
   article-stripped), merges translations and does not re-fetch examples.
   Returns {:word-id id :created? true/false}."
  [{:keys [collections examples progress-store] :as capabilities} value translation]
  (let [parsed     (domain/parse-translations translation)
        normalized (domain/normalize-value value)]
    (if (empty? parsed)
      {:error :empty-translations}
      (let [existing (await (find-duplicate capabilities normalized))]
        (if existing
          (let [merged        (domain/merge-translations (:translation existing) parsed)
                updated       (assoc existing :translation merged)
                collection-id ((:collections/active-id collections))]
            (await ((:progress-store/save-word! progress-store) updated))
            (when collection-id
              (await ((:collections/add-word! collections) (:_id existing) collection-id))
              (when-not (await ((:examples/find examples) (:_id existing) collection-id))
                (let [collection-name (:name (await ((:collections/get collections) collection-id)))]
                  ((:examples/request! examples) (:_id existing) collection-id collection-name))))
            {:word-id (:_id existing) :created? false})
          (let [word (domain/new-word value parsed)
                {:keys [id]} (await ((:progress-store/save-word! progress-store) word))
                collection-id ((:collections/active-id collections))
                collection-name (when collection-id
                                  (:name (await ((:collections/get collections) collection-id))))]
            (await ((:progress-store/save-review! progress-store) id true translation))
            (when collection-id
              (await ((:collections/add-word! collections) id collection-id)))
            ((:examples/request! examples) id collection-id collection-name)
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


(defn ^:async list-active
  "Returns vocabulary rows scoped to the active collection. When no collection
   is active (implicit main card), returns all words like `list`."
  [{:keys [collections] :as capabilities} opts]
  (let [id       (not-empty ((:collections/active-id collections)))
        word-ids (when id
                   (some-> (await ((:collections/get collections) id))
                           :word-ids))]
    (await (list capabilities
                 (cond-> opts
                   word-ids (assoc :word-ids word-ids))))))


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
  "Atomically deletes a word and its reviews and unlinks it from every
   collection in a single bulk write. No-op if word doesn't exist."
  [{:keys [progress-store]} word-id]
  (await ((:progress-store/delete-word! progress-store) word-id)))


(defn ^:async remove-from-active!
  "User-initiated 'remove word' that respects the active scope:
   from a named collection, only the membership is dropped — the word
   itself stays in vocabulary; from the implicit main, the word and all
   its associated data are permanently deleted."
  [{:keys [collections] :as capabilities} word-id]
  (if-let [collection-id ((:collections/active-id collections))]
    (await ((:collections/exclude-word! collections) word-id collection-id))
    (await (delete! capabilities word-id))))


(defn add-review
  "Creates a review document for a word and updates its retention model."
  [{:keys [progress-store]} word-id retained translation]
  ((:progress-store/save-review! progress-store) word-id retained translation))


(defn export!
  "Exports all vocab + reviews as a portable map."
  [{:keys [progress-store]}]
  ((:progress-store/export-data! progress-store)))


(defn ^:async import!
  "Merges a previously exported map into the local store."
  [{:keys [progress-store]} payload]
  (await ((:progress-store/import-data! progress-store) payload)))

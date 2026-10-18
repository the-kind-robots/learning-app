(ns adapters.learner.conflicts
  "Conflicts a replication pass leaves in the learner's documents: several live versions of a word or of a
   collection, merged into one so the conflict is gone everywhere."
  (:require
   [adapters.learner.documents :as documents]
   [clojure.string :as str]
   [db :as db]
   [db.pouch :as pouch]
   [domain.collections :as collections]
   [domain.vocabulary :as domain]
   [lambdaisland.glogi :as log]))


(defn- all!
  "One promise for a sequence of promises."
  [promises]
  (js/Promise.all (into-array promises)))


(defn- ^:async resolve-conflict!
  "Writes the merged word (`domain.vocabulary/merged-word`) of a document's conflicting
   revisions and deletes the losers, so the conflict is gone everywhere."
  [user-db doc]
  ;; One revision at a time on purpose: `bulkGet` is built to save HTTP round trips and, against a
  ;; local database, measures slower than the plain reads (IndexedDB, 200 revisions).
  (let [conflicting (await (all! (map #(db/get user-db (:_id doc) {:rev %})
                                      (:_conflicts doc))))
        revisions   (cons doc conflicting)
        merged      (domain/merged-word revisions)
        losers      (remove #(= (:_rev %) (:_rev merged)) revisions)]
    (await (db/insert user-db merged))
    (await (all! (map #(db/remove user-db %) losers)))))


(defn ^:async resolve-vocab-conflicts!
  "Resolves every conflicted vocabulary doc a replication pass left behind."
  [user-db]
  (try
    (let [{rows :rows} (await (db/all-docs user-db
                                       ;; Vocabulary ids share a prefix (ADR-0008), so the entries are a key range, not a scan of the
                                       ;; database; phrases live in it too. `:conflicts` is the point of the query, and only `all-docs`,
                                       ;; `get` and `changes` honour it: `find` silently answers without conflicts.
                                       {:conflicts    true
                                        :endkey       (str domain/id-prefix "￰")
                                        :include-docs true
                                        :startkey     domain/id-prefix}))
          conflicted   (->> rows
                            (map :doc)
                            (filter #(-> % :_conflicts seq)))]
      (when (seq conflicted)
        (log/info :sync/resolving-conflicts {:count (count conflicted)}))
      (await (all! (map #(resolve-conflict! user-db %) conflicted))))
    (catch js/Error err
      (log/warn :sync/conflict-resolution-failed {:error (ex-message err)}))))


(defn- revision-order
  "The revision of a stored `version` as a comparable: its generation, then
   its hash."
  [version]
  (let [[generation hash] (str/split (:_rev version) #"-" 2)]
    [(js/parseInt generation 10) hash]))


(defn- ^:async resolve-collection!
  "When a pull left a collection with several live versions, writes one that
   holds them all and removes the others."
  [databases id]
  (let [live (->> (await (pouch/doc-versions databases documents/collection-schema id))
                  (remove :_deleted))]
    (when (next live)
      (let [collection (reduce (partial collections/merged-collection revision-order) live)
            losers     (remove #(= (:_rev collection) (:_rev %)) live)
            docs       (conj (mapv documents/tombstone losers) collection)
            written    (await (pouch/bulk-docs databases documents/collection-schema docs))]
        (when (< (count written) (count docs))
          (log/warn :sync/conflict-resolution-failed
                    {:id id :refused (- (count docs) (count written))}))))))


(defn ^:async resolve-collection-conflicts!
  "Resolves the collections `ids` that have more than one live version."
  [databases ids]
  (try
    (await (all! (map #(resolve-collection! databases %) ids)))
    (catch js/Error err
      (log/warn :sync/conflict-resolution-failed {:error (ex-message err)}))))

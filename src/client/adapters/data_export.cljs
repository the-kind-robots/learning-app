(ns adapters.data-export
  (:require
   [db :as db]
   [lambdaisland.glogi :as log]
   [utils :as utils]))


(defn ^:async export-data!
  "Exports all vocab and review docs from user-db.
   Returns {:schema 1 :exported-at iso :vocab [...] :review [...]}."
  [db-map]
  (let [user-db         (:user/db db-map)
        {vocab :docs}   (await (db/find-all user-db {:selector {:type "vocab"}}))
        {reviews :docs} (await (db/find-all user-db {:selector {:type "review"}}))]
    {:schema      1
     :exported-at (utils/now-iso)
     :vocab       (mapv #(dissoc % :_rev) vocab)
     :review      (mapv #(dissoc % :_rev) reviews)}))


(defn- conflict?
  [err]
  (let [status (or (.-status err) (:status err))
        n      (or (.-name err) (:name err))]
    (or (= status 409) (= status "409") (= n "conflict"))))


(defn ^:async import-vocab-doc!
  "Inserts or LWW-merges a single vocab doc into user-db."
  [user-db incoming]
  (try
    (await (db/insert user-db incoming (:_id incoming)))
    (catch js/Error err
      (if (conflict? err)
        (let [existing (await (db/get user-db (:_id incoming)))]
          (when (pos? (compare (or (:modified-at incoming) "")
                               (or (:modified-at existing) "")))
            (await (db/insert user-db (assoc incoming :_rev (:_rev existing)) (:_id incoming)))))
        (throw err)))))


(defn ^:async import-review-doc!
  "Inserts a review doc; skips if the _id already exists (idempotent union)."
  [user-db incoming]
  (try
    (await (db/insert user-db incoming (:_id incoming)))
    (catch js/Error err
      (when-not (conflict? err)
        (throw err)))))


(defn ^:async import-data!
  "Merges an export map into user-db.
   Vocab: LWW by :modified-at.  Reviews: union (skip duplicates by _id)."
  [db-map {:keys [schema vocab review]}]
  (when-not (= schema 1)
    (throw (ex-info "Unsupported export schema" {:schema schema})))
  (let [user-db (:user/db db-map)]
    (log/info :data-export/importing {:vocab (count vocab) :review (count review)})
    (await (js/Promise.all (into-array (map #(import-vocab-doc! user-db %) vocab))))
    (await (js/Promise.all (into-array (map #(import-review-doc! user-db %) review))))
    (log/info :data-export/import-done {:vocab (count vocab) :review (count review)})))

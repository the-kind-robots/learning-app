(ns adapters.data-export
  (:require
   [db :as db]
   [lambdaisland.glogi :as log]
   [utils :as utils]))


(defn ^:async export-data!
  "Exports every user-db doc, so no doc type can be silently dropped.
   Returns {:schema 2 :exported-at iso :docs [...]}."
  [db-map]
  (let [{rows :rows} (await (db/all-docs (:user/db db-map) {:include-docs true}))]
    {:schema      2
     :exported-at (utils/now-iso)
     :docs        (mapv #(dissoc (:doc %) :_rev) rows)}))


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


(defn ^:async import-doc!
  "Inserts a doc; skips if the _id already exists (idempotent union)."
  [user-db incoming]
  (try
    (await (db/insert user-db incoming (:_id incoming)))
    (catch js/Error err
      (when-not (conflict? err)
        (throw err)))))


(defn- payload-docs
  "Returns the docs of an export payload, accepting schema 1
   ({:vocab [...] :review [...]}) and schema 2 ({:docs [...]})."
  [{:keys [schema docs review vocab]}]
  (case schema
    1 (concat vocab review)
    2 docs
    (throw (ex-info "Unsupported export schema" {:schema schema}))))


(defn ^:async import-data!
  "Merges an export payload into user-db by doc type:
   vocab and phrase LWW by :modified-at, anything else insert-if-absent."
  [db-map payload]
  (let [user-db (:user/db db-map)
        docs    (payload-docs payload)]
    (log/info :data-export/importing {:count (count docs)})
    (await (js/Promise.all
            (into-array
             (map #(if (#{"vocab" "phrase"} (:type %))
                     (import-vocab-doc! user-db %)
                     (import-doc! user-db %))
                  docs))))
    (log/info :data-export/import-done {:count (count docs)})))

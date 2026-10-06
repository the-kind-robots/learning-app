(ns adapters.data-export
  (:require
   [adapters.learner.documents :as documents]
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


(defn ^:async import-vocab-doc!
  "Inserts or LWW-merges a single vocab doc into user-db."
  [user-db incoming]
  (try
    (await (db/insert user-db incoming (:_id incoming)))
    (catch js/Error err
      (if (db/conflict? err)
        (let [existing (await (db/get user-db (:_id incoming)))]
          (when (pos? (compare (or (:modified-at incoming) "")
                               (or (:modified-at existing) "")))
            (await (db/insert user-db (assoc incoming :_rev (:_rev existing)) (:_id incoming)))))
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
  "Merges an export payload into user-db by doc type: vocab LWW by
   :modified-at, anything else insert-if-absent. An example is written as
   the app writes it (`documents/example-doc`), so it gets the id and the
   revision it has on every device, whatever build exported it."
  [db-map payload]
  (let [user-db (:user/db db-map)
        docs    (payload-docs payload)]
    (log/info :data-export/importing {:count (count docs)})
    (await (js/Promise.all
            (into-array
             (map #(condp = (:type %)
                     "vocab"
                     (import-vocab-doc! user-db %)

                     (:type documents/example-schema)
                     (db/insert-if-absent user-db
                                          (documents/example-doc (:word-id %) (:word %) (:collection-id %) %))

                     (db/insert-if-absent user-db %))
                  docs))))
    (log/info :data-export/import-done {:count (count docs)})))

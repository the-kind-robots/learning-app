(ns adapters.repository
  "What every repository does at its edge, written once: a stored document
   becomes a domain entity keyed by `:id` and back, a deletion is a
   tombstone, creation is stamped, and a save lands on whatever revision is
   stored."
  (:require
   [db.pouch :as dbs]))


(defn entity
  "The domain shape of a stored document: `:id` for `:_id`, no revision, no
   type."
  [doc]
  (-> doc
      (dissoc :_id :_rev :type)
      (assoc :id (:_id doc))))


(defn doc
  "The stored shape of an entity: `:_id` for `:id`."
  [entity]
  (-> entity
      (dissoc :id)
      (assoc :_id (:id entity))))


(defn tombstone
  [doc]
  (assoc doc :_deleted true))


(defn stamp-created
  "`:created-at` now, unless the entity has one."
  [clock entity]
  (cond-> entity
    (nil? (:created-at entity)) (assoc :created-at ((:clock/now-iso clock)))))


(defn ^:async upsert!
  "Writes `doc` under its id over whichever revision is stored, so no
   revision travels through the domain."
  [dbs schema doc]
  (let [stored (await (dbs/get dbs schema (:_id doc)))]
    (await (dbs/insert dbs schema (cond-> doc stored (assoc :_rev (:_rev stored)))))))

(ns adapters.lessons
  "Repository of the one lesson in progress on this device. Outward the
   lesson state is the domain's map; the document behind it has one fixed
   id, so a save replaces whatever revision is stored."
  (:require
   [adapters.repository :as repository]
   [db.pouch :as dbs]))


(def schema
  {:type "lesson"
   :db   :device/db})


(def ^:private lesson-id "lesson")


(defn- stamp
  [clock lesson-state]
  (cond-> lesson-state
    (nil? (:started-at lesson-state)) (assoc :started-at ((:clock/now-iso clock)))))


(defn ^:async get-lesson
  ;; The one lesson has no identity of its own: the id names the slot.
  [dbs]
  (some-> (await (dbs/get dbs schema lesson-id)) repository/entity (dissoc :id)))


(defn save-lesson!
  [dbs clock lesson-state]
  (repository/upsert! dbs schema (repository/doc (assoc (stamp clock lesson-state) :id lesson-id))))


(defn ^:async remove-lesson!
  [dbs]
  (when-let [doc (await (dbs/get dbs schema lesson-id))]
    (await (dbs/remove dbs schema doc))))

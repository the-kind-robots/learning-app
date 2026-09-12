(ns adapters.lessons
  "Repository of the one lesson in progress on this device. Outward the
   lesson state is the domain's map; the document behind it has one fixed
   id, so a save replaces whatever revision is stored."
  (:require
   [db.pouch :as dbs]))


(def schema
  {:type "lesson"
   :db   :device/db})


(def ^:private lesson-id "lesson")


(defn- doc->lesson
  [doc]
  (dissoc doc :_id :_rev :type))


(defn- stamp
  [clock lesson-state]
  (cond-> lesson-state
    (nil? (:started-at lesson-state)) (assoc :started-at ((:clock/now-iso clock)))))


(defn ^:async get-lesson
  [dbs]
  (some-> (await (dbs/get dbs schema lesson-id)) doc->lesson))


(defn ^:async save-lesson!
  [dbs clock lesson-state]
  (let [stored (await (dbs/get dbs schema lesson-id))
        doc    (cond-> (assoc (stamp clock lesson-state) :_id lesson-id)
                 stored (assoc :_rev (:_rev stored)))]
    (await (dbs/insert dbs schema doc))))


(defn ^:async remove-lesson!
  [dbs]
  (when-let [doc (await (dbs/get dbs schema lesson-id))]
    (await (dbs/remove dbs schema doc))))

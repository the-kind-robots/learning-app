(ns adapters.lessons
  "Repository of the one lesson in progress on this device."
  (:require
   [db.pouch :as dbs]
   [domain.lesson :as lesson]))


(def schema
  {:type "lesson"
   :db   :device/db})


(defn- stamp
  [clock lesson-state]
  (cond-> lesson-state
    (nil? (:started-at lesson-state)) (assoc :started-at ((:clock/now-iso clock)))))


(defn get-lesson
  [dbs]
  (dbs/get dbs schema lesson/lesson-id))


(defn save-lesson!
  [dbs clock lesson-state]
  (dbs/insert dbs schema (stamp clock lesson-state)))


(defn ^:async remove-lesson!
  [dbs]
  (when-let [lesson-state (await (get-lesson dbs))]
    (await (dbs/remove dbs schema lesson-state))))

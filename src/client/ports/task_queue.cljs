(ns ports.task-queue
  (:require
   [tasks :as tasks]))


(defn ^:async start!
  [{:keys [clock db]}]
  (await (tasks/start! db clock)))


(defn stop!
  [_]
  (tasks/stop!))

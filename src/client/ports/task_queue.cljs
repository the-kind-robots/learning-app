(ns ports.task-queue
  (:require
   [tasks :as tasks]))


(defn start!
  "Starts the task runner once memory is loaded, so that the runner's
   queries do not compete with the load. It returns at once."
  [{:keys [clock db learner]}]
  (tasks/start! db clock ((:learner/loaded learner)))
  nil)


(defn stop!
  [_]
  (tasks/stop!))

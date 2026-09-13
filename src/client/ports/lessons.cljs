(ns ports.lessons
  (:require
   [adapters.lessons :as lessons]))


(defn start!
  [{:keys [clock db]}]
  {:lessons/get     (fn get
                      []
                      (lessons/get-lesson db))
   :lessons/remove! (fn remove!
                      []
                      (lessons/remove-lesson! db))
   :lessons/save!   (fn save!
                      [lesson-state]
                      (lessons/save-lesson! db clock lesson-state))})

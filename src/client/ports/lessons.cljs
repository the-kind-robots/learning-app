(ns ports.lessons
  (:require
   [adapters.lessons :as lessons]))


(defn start!
  "The lesson's writes run one after another, in the order they were asked
   for. They are asked for from both sides of a paint — the lesson begun on
   entry is stored after the screen shows it, the one being left is removed
   after home shows — and a write that overtook another would remove the
   lesson just stored, or store over a newer one."
  [{:keys [clock db]}]
  (let [queue   (atom (js/Promise.resolve nil))
        in-turn (fn [write]
                  (let [turn (.then @queue (fn [_] (write)))]
                    ;; The queue goes on after a failed write; the caller
                    ;; still hears of the failure through `turn`.
                    (reset! queue (.catch turn (constantly nil)))
                    turn))]
    {:lessons/get     (fn get
                        []
                        (lessons/get-lesson db))
     :lessons/remove! (fn remove!
                        []
                        (in-turn #(lessons/remove-lesson! db)))
     :lessons/save!   (fn save!
                        [lesson-state]
                        (in-turn #(lessons/save-lesson! db clock lesson-state)))}))

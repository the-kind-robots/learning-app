(ns ports.progress-store
  (:require
   [adapters.progress-store :as adapter]))


(defn start!
  [{:keys [clock db]}]
  {:progress-store/count-words    (fn count-words
                                    []
                                    (adapter/count-words db))
   :progress-store/delete-word!   (fn delete-word!
                                    [word-id]
                                    (adapter/delete-word! db word-id))
   :progress-store/find-word-by-normalized-value (fn find-word-by-normalized-value
                                                   [normalized]
                                                   (adapter/find-word-by-normalized-value db normalized))
   :progress-store/get-lesson     (fn get-lesson
                                    []
                                    (adapter/get-lesson db))
   :progress-store/get-word       (fn get-word
                                    [word-id]
                                    (adapter/get-word db clock word-id))
   :progress-store/list-words     (fn list-words
                                    [opts]
                                    (adapter/list-words db clock opts))
   :progress-store/remove-lesson! (fn remove-lesson!
                                    []
                                    (adapter/remove-lesson! db))
   :progress-store/save-lesson!   (fn save-lesson!
                                    [lesson-state]
                                    (adapter/save-lesson! db clock lesson-state))
   :progress-store/save-review!   (fn save-review!
                                    [word-id retained translation]
                                    (adapter/save-review! db clock word-id retained translation))
   :progress-store/save-word!     (fn save-word!
                                    [word]
                                    (adapter/save-word! db clock word))})

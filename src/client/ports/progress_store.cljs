(ns ports.progress-store
  "The learner's progress: words, their reviews, the lesson in progress and
   the backup of all of it. One capability over three repositories."
  (:require
   [adapters.data-export :as data-export]
   [adapters.lessons :as lessons]
   [adapters.reviews :as reviews]
   [adapters.words :as words]))


(defn start!
  [{:keys [clock db]}]
  {:progress-store/count-words        (fn count-words
                                        []
                                        (words/count-words db))
   :progress-store/delete-word!       (fn delete-word!
                                        [word-id]
                                        (words/delete-word! db word-id))
   :progress-store/find-word-by-value (fn find-word-by-value
                                        [value]
                                        (words/find-by-value db value))
   :progress-store/get-word           (fn get-word
                                        [word-id]
                                        (words/get-word db word-id))
   :progress-store/word-previews      (fn word-previews
                                        [word-ids]
                                        (words/previews db word-ids))
   :progress-store/save-word!         (fn save-word!
                                        [word]
                                        (words/save-word! db clock word))
   :progress-store/reviews-by-word    (fn reviews-by-word
                                        [word-ids]
                                        (reviews/reviews-by-word db word-ids))
   :progress-store/save-review!       (fn save-review!
                                        [word-id retained translation]
                                        (reviews/save-review! db clock word-id retained translation))
   :progress-store/get-lesson         (fn get-lesson
                                        []
                                        (lessons/get-lesson db))
   :progress-store/save-lesson!       (fn save-lesson!
                                        [lesson-state]
                                        (lessons/save-lesson! db clock lesson-state))
   :progress-store/remove-lesson!     (fn remove-lesson!
                                        []
                                        (lessons/remove-lesson! db))
   :progress-store/export-data!       (fn export-data!
                                        []
                                        (data-export/export-data! db))
   :progress-store/import-data!       (fn import-data!
                                        [payload]
                                        (data-export/import-data! db payload))})

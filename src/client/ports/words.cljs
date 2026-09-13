(ns ports.words
  (:require
   [adapters.words :as words]))


(defn start!
  [{:keys [clock db]}]
  {:words/count         (fn count
                          []
                          (words/count-words db))
   :words/delete!       (fn delete!
                          [word-id companion-docs]
                          (words/delete-word! db word-id companion-docs))
   :words/find-by-value (fn find-by-value
                          [value]
                          (words/find-by-value db value))
   :words/get           (fn get
                          [word-id]
                          (words/get-word db word-id))
   :words/previews      (fn previews
                          [word-ids]
                          (words/previews db word-ids))
   :words/save!         (fn save!
                          [word]
                          (words/save-word! db clock word))})

(ns ports.backup
  "The backup of the learner's data: every document out and back in,
   unchanged."
  (:require
   [adapters.data-export :as data-export]))


(defn start!
  [{:keys [db]}]
  {:backup/export! (fn export!
                     []
                     (data-export/export-data! db))
   :backup/import! (fn import!
                     [payload]
                     (data-export/import-data! db payload))})

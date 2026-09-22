(ns backend.support.db
  "The database a backend test runs against."
  (:require
   [migrations :as migrations])
  (:import
   [java.io File]))


(set! *warn-on-reflection* true)


(defn migrated-db
  "A fresh SQLite file with every migration applied, named after the test that
   asked for it and deleted when the JVM exits."
  [test-name]
  (let [file (doto (File/createTempFile test-name ".db")
               (.deleteOnExit))]
    ;; SQLite wants to create the file itself.
    (.delete file)
    (doto {:dbtype "sqlite" :dbname (.getAbsolutePath file)}
      (migrations/ensure-migrated!))))

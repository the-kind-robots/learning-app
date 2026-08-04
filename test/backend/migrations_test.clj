(ns backend.migrations-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [migrations :as sut]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as result-set])
  (:import
   [java.io File FileOutputStream]
   [java.net URL]
   [java.util.jar JarEntry JarOutputStream]))


(set! *warn-on-reflection* true)


(defn- temp-db-spec
  []
  (let [file (doto (File/createTempFile "migrations-test" ".db")
               (.deleteOnExit))]
    ;; SQLite wants to create the file itself.
    (.delete file)
    {:dbtype "sqlite" :dbname (.getAbsolutePath file)}))


(defn- apply-by-hand!
  "Runs a migration the way a schema file used to be applied: without recording
   that it ran. This is what every deployment looked like before migrations."
  [db id]
  (doseq [statement (#'sut/statements (#'sut/migration-sql id))]
    (jdbc/execute! db [statement])))


(defn- column
  [db query]
  (into #{}
        (map (comp val first))
        (jdbc/execute! db [query] {:builder-fn result-set/as-unqualified-maps})))


(defn- migrate!
  "Runs migrations against a database whose connections enforce foreign keys,
   the way `on-connection` leaves them for the rest of the app. Without that,
   SQLite's own default — foreign keys off — would hide what a table rebuild
   really does to the rows hanging off the table."
  [db]
  (with-open [conn (jdbc/get-connection db)]
    (jdbc/execute! conn ["PRAGMA foreign_keys = ON"]))
  (sut/ensure-migrated! db))


(defn- table-names
  [db]
  (column db "SELECT name FROM sqlite_master WHERE type = 'table'"))


(defn- recorded-ids
  [db]
  (column db "SELECT id FROM schema_migrations"))


(defn- jar-with
  "A jar holding the given entry names, plus the directory entry a build writes."
  [names]
  (let [file (doto (File/createTempFile "migrations-test" ".jar")
               (.delete)
               (.deleteOnExit))]
    (with-open [out (JarOutputStream. (FileOutputStream. file))]
      (doseq [name (cons "migrations/" names)]
        (.putNextEntry out (JarEntry. name))
        (when-not (str/ends-with? name "/")
          (.write out (.getBytes "-- statement")))
        (.closeEntry out)))
    file))


(deftest every-migration-is-discovered
  (testing "the list comes from the directory, not from a hand-written copy"
    (is (seq (sut/migrations)))
    (is (= (sut/migrations) (sort (sut/migrations))) "ordinal file names order the run")))


(deftest migrations-are-found-inside-a-jar
  ;; Production runs from an uberjar, where a resource directory cannot be
  ;; listed: an archive is a flat list of entries with no directory to open.
  (let [jar     (jar-with ["migrations/001-first.sql" "migrations/002-second.sql" "migrations/notes.txt"])
        jar-url (.toString (.toURL (.toURI jar)))
        entry   (URL. (str "jar:" jar-url "!/migrations/001-first.sql"))
        dir     (URL. (str "jar:" jar-url "!/migrations/"))]
    (testing "every entry under the directory is seen"
      (is (= ["" "001-first.sql" "002-second.sql" "notes.txt"]
             (sort (#'sut/archive-entries dir)))))
    (testing "listing leaves the archive usable"
      ;; A smoke check, not a proof: the JVM may hand out a JarFile it shares
      ;; with the classloader, and closing that one would take the rest of the
      ;; archive down with it. `archive-entries` disables caching to get its
      ;; own copy; nothing in-process reproduces the shared case.
      (#'sut/archive-entries dir)
      (is (= "-- statement" (slurp entry))))))


(deftest fresh-database-gets-the-whole-schema
  (let [db (temp-db-spec)]
    (migrate! db)
    (testing "every migration is recorded"
      (is (= (set (sut/migrations)) (recorded-ids db))))
    (testing "every statement runs, not only the first"
      ;; The driver executes the first statement of a multi-statement string
      ;; and drops the rest without raising, so a table created by the last
      ;; statement of a file is what proves nothing was silently truncated.
      (let [tables (table-names db)]
        (is (contains? tables "reviews") "last table of the baseline")
        (is (contains? tables "recovery_tokens") "last table of the file")))))


(deftest migrating-twice-changes-nothing
  (let [db (temp-db-spec)]
    (migrate! db)
    (let [before (table-names db)]
      (migrate! db)
      (testing "a migrated database is left alone"
        (is (= before (table-names db)))
        (is (= (set (sut/migrations)) (recorded-ids db)))))))


(deftest a-database-built-before-migrations-existed-is-adopted
  (let [db (temp-db-spec)]
    (apply-by-hand! db "001-initial-schema.sql")
    (migrate! db)
    (testing "the baseline is recognised as already applied, the rest runs"
      (is (= (set (sut/migrations)) (recorded-ids db))))
    (testing "the schema ends up where a fresh database would be"
      (is (= (table-names db) (table-names (doto (temp-db-spec) migrate!)))))))

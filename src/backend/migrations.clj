(ns migrations
  "Schema migrations for the server database.

   This list is the single source of truth for the schema: a fresh database is
   built by running every migration in order, an existing one by running only
   what it has not run yet. `CREATE TABLE IF NOT EXISTS` alone cannot express a
   change to an existing table, which is why a schema file applied by hand is
   not enough.

   Never edit a migration that has shipped — a database that already ran it
   will not run it again. Add the next one instead."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as result-set]
   [taoensso.telemere :as t])
  (:import
   [java.io File]
   [java.net JarURLConnection URL]
   [java.util.jar JarEntry]))


(set! *warn-on-reflection* true)


(def ^:private migration-name #"\d{3}-.+\.sql")


(defn- directory-entries
  "Names inside a resource directory that lives on the filesystem."
  [^URL url]
  (map File/.getName (.listFiles (io/file url))))


(defn- directory-prefix
  "Entries in an archive are paths, so its directories are just names ending
   in a slash."
  [^JarURLConnection connection]
  (let [name (.getEntryName connection)]
    (if (str/ends-with? name "/")
      name
      (str name "/"))))


(defn- archive-entries
  "Names inside a resource directory that lives in a jar. An archive has no
   directory to open — only a flat list of entries — so the list is filtered
   by prefix and the prefix stripped back off."
  [^URL url]
  ;; Caching off first: by default the connection hands back a JarFile the JVM
  ;; shares with the classloader, and closing that one would take every later
  ;; resource in the same archive with it.
  (let [^JarURLConnection connection (doto (.openConnection url)
                                       (.setUseCaches false))
        directory (directory-prefix connection)]
    (with-open [jar (.getJarFile connection)]
      (->> (enumeration-seq (.entries jar))
           (map JarEntry/.getName)
           (filter #(str/starts-with? % directory))
           (mapv #(subs % (count directory)))))))


(defn migrations
  "The migration files, in the order their names give them. Read on every call
   rather than baked in at compile time: the compiler tracks this namespace's
   source, not the directory the names come from, so a compiled copy would keep
   answering with the migrations that existed when it was built."
  []
  (if-let [url (io/resource "migrations")]
    (->> (if (= "jar" (.getProtocol ^URL url))
           (archive-entries url)
           (directory-entries url))
         (filter #(re-matches migration-name %))
         sort
         vec)
    (throw (ex-info "No migrations directory on the classpath" {}))))


(def ^:private statement-separator
  "Statements within a migration are separated by a `--;;` line. The driver
   executes only the first statement of a multi-statement string and drops the
   rest without raising, so statements have to be sent one at a time; splitting
   on `;` instead would break on the first semicolon inside a string literal or
   a trigger body."
  #"(?m)^\s*--;;\s*$")


(defn- statements
  [sql]
  (->> (str/split sql statement-separator)
       (map str/trim)
       (remove str/blank?)))


(defn- migration-sql
  [id]
  (if-let [file (io/resource (str "migrations/" id))]
    (slurp file)
    (throw (ex-info "Migration file missing" {:id id}))))


(def ^:private tracking-table
  "CREATE TABLE IF NOT EXISTS schema_migrations
   (
       id         TEXT PRIMARY KEY,
       applied_at INTEGER DEFAULT (UNIXEPOCH())
   )")


(defn- ensure-tracking-table!
  [db]
  (jdbc/execute! db [tracking-table]))


(defn- applied-ids
  [db]
  (into #{}
        (map :id)
        (jdbc/execute! db
          ["SELECT id FROM schema_migrations"]
          {:builder-fn result-set/as-unqualified-maps})))


(def ^:private lock-wait-ms
  "How long to wait for another process that is already migrating. Applying
   the whole chain is a handful of DDL statements — milliseconds — so this is
   three orders of magnitude more than a migration needs: long enough that a
   process started at the same moment waits its turn, short enough that a lock
   held by something else fails the boot instead of hanging it."
  10000)


(defn ensure-migrated!
  "Applies every migration this database has not run yet, all of them in a
   single transaction: the database ends up either fully migrated or untouched.

   Opens and closes a connection of its own. Every setting this depends on
   belongs to a connection rather than to the database, and the exclusive lock
   it takes has to be given back — a connection left open would hold the
   database read-only for everyone else. Exclusive locking is what keeps a
   second process from migrating alongside this one.

   Foreign keys are enforced everywhere else but switched off here, as SQLite's
   table-rebuild procedure requires: `DROP TABLE` runs an implicit `DELETE FROM`
   while they are on, so rebuilding a table cascades its children away — a
   migration that renames a column would silently take `user_settings` with it.
   `PRAGMA foreign_keys` is a no-op inside a transaction, so it cannot be left
   to a migration."
  [db-spec]
  (with-open [conn (jdbc/get-connection db-spec)]
    (jdbc/execute! conn ["PRAGMA foreign_keys = OFF"])
    (jdbc/execute! conn [(str "PRAGMA busy_timeout = " lock-wait-ms)])
    (jdbc/execute! conn ["PRAGMA locking_mode = EXCLUSIVE"])
    (ensure-tracking-table! conn)
    (jdbc/with-transaction [tx conn]
      (doseq [id (remove (applied-ids tx) (migrations))]
        (t/event! ::applying {:level :info :data {:id id}})
        (doseq [statement (statements (migration-sql id))]
          (jdbc/execute! tx [statement]))
        (jdbc/execute! tx ["INSERT INTO schema_migrations (id) VALUES (?)" id])
        (t/event! ::applied {:level :info :data {:id id}})))))

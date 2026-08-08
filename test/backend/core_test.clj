(ns backend.core-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [core :as sut]
   [db :as db]
   [migrations :as migrations]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as result-set]
   [org.httpkit.client :as client]
   [org.httpkit.server :as server])
  (:import
   [java.io File]))


(set! *warn-on-reflection* true)


(defn- migrated-db
  []
  (let [file (doto (File/createTempFile "core-test" ".db")
               (.deleteOnExit))]
    ;; SQLite wants to create the file itself.
    (.delete file)
    (doto {:dbtype "sqlite" :dbname (.getAbsolutePath file)}
      (migrations/ensure-migrated!))))


(defn- add-account!
  [db id token]
  (jdbc/execute! db
    ["INSERT INTO users (id, token_sha256) VALUES (?, ?)" id (#'sut/sha256-hex token)]))


(deftest a-token-authenticates-the-account-it-was-minted-for
  (let [db (migrated-db)]
    (add-account! db 1 "token-of-one")
    (add-account! db 2 "token-of-two")
    (testing "each token resolves to its own account"
      (is (= 1 (#'sut/authenticated-user-id db "token-of-one")))
      (is (= 2 (#'sut/authenticated-user-id db "token-of-two"))))
    (testing "anything else authenticates nobody"
      (is (nil? (#'sut/authenticated-user-id db "not-a-token")))
      (is (nil? (#'sut/authenticated-user-id db "")))
      (is (nil? (#'sut/authenticated-user-id db nil))))))


(deftest the-server-keeps-no-token-it-could-leak
  (let [db    (migrated-db)
        token "token-of-one"]
    (add-account! db 1 token)
    (testing "only the hash is written"
      (is (= [(#'sut/sha256-hex token)]
             (map :token_sha256
                  (jdbc/execute! db
                    ["SELECT token_sha256 FROM users"]
                    {:builder-fn result-set/as-unqualified-maps})))))))


(deftest a-grant-opens-exactly-one-account
  (let [db    (migrated-db)
        token (sut/mint-grant! db)]
    (testing "an unused grant burns"
      (is (true? (#'sut/burn-grant! db token))))
    (testing "and never burns twice"
      (is (false? (#'sut/burn-grant! db token))))
    (testing "a grant nobody minted does not burn"
      (is (false? (#'sut/burn-grant! db "invented"))))))


(deftest an-expired-grant-is-worthless
  (let [db    (migrated-db)
        token (sut/mint-grant! db -1)]
    (is (false? (#'sut/burn-grant! db token)))))


(deftest stopping-the-server-drains-in-flight-requests
  (let [in-flight (promise)
        handler   (fn [_]
                    (deliver in-flight true)
                    (Thread/sleep 300)
                    {:body "drained" :status 200})]
    (sut/start-server! handler 0)
    (try
      (let [port     (server/server-port @sut/server)
            response (future @(client/request {:method :get
                                               :url    (str "http://localhost:" port "/")}))]
        (is (true? (deref in-flight 2000 false)) "the request never reached the handler")
        (sut/stop-server!)
        (testing "the in-flight request finished with a response"
          (is (= 200 (:status @response))))
        (testing "the listening socket is closed"
          (is (thrown? java.net.ConnectException
                       (.close (java.net.Socket. "localhost" (int port))))))
        (testing "the server handle is released"
          (is (nil? @sut/server))))
      (finally
       (sut/stop-server!)))))


(deftest the-build-and-the-server-agree-on-where-the-version-lives
  (testing "the name is the only thing the two sides share, and nothing else checks it"
    (is (str/includes? (slurp "build.clj")
                       (str "\"" @#'sut/sw-version-resource "\""))
        "build.clj writes the service worker version under a different name than core.clj reads")))


(deftest the-new-secret-name-is-read
  (is (= "new" (#'sut/configured-db-auth-secret {"LEARNING_APP__DB_AUTH_SECRET" "new"} false))))


(deftest the-old-secret-name-still-works-until-the-unit-renames
  (is (= "old" (#'sut/configured-db-auth-secret {"LEARNING_APP_DB_AUTH_SECRET" "old"} false))))


(deftest the-new-secret-name-wins-when-both-are-set
  (is (= "new"
         (#'sut/configured-db-auth-secret
          {"LEARNING_APP_DB_AUTH_SECRET"  "old"
           "LEARNING_APP__DB_AUTH_SECRET" "new"}
          false))))


(deftest a-packaged-app-without-a-secret-refuses-to-start
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"LEARNING_APP__DB_AUTH_SECRET"
                        (#'sut/configured-db-auth-secret {} false))))


(deftest a-checkout-falls-back-to-the-well-known-secret
  (is (= "secret" (#'sut/configured-db-auth-secret {} true))))


(deftest a-packaged-app-without-a-couchdb-password-refuses-to-start
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"LEARNING_APP__COUCHDB_PASSWORD"
                        (#'sut/require-couchdb-password! {} false))))


(deftest a-couchdb-password-or-a-checkout-satisfies-the-guard
  (is (nil? (#'sut/require-couchdb-password! {"LEARNING_APP__COUCHDB_PASSWORD" "x"} false)))
  (is (nil? (#'sut/require-couchdb-password! {} true))))


(deftest a-recycled-id-is-refused-not-inherited
  (testing "a userdb left by a previous owner of the id blocks provisioning"
    (let [db (migrated-db)]
      (with-redefs [db/exists? (constantly true)]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"userdb already exists"
                              (#'sut/create-account! db))))
      (is (empty? (jdbc/execute! db ["SELECT id FROM users"]))
          "the transaction rolled the row back, the id stays unspent"))))


(deftest a-fresh-id-provisions-and-secures-its-userdb
  (testing "the guard does not get in the way of a normal provision"
    (let [db      (migrated-db)
          secured (atom nil)]
      (with-redefs [db/exists? (constantly false)
                    db/use     (fn [name] name)
                    db/secure  (fn [name security] (reset! secured [name security]))]
        (let [{:keys [id token]} (#'sut/create-account! db)]
          (is (int? id))
          (is (= 40 (count token)))
          (is (= (str "userdb-" id) (first @secured)))
          (is (= [(str "u:" id)] (get-in (second @secured) [:members :roles]))))))))


(deftest a-failed-couch-step-leaves-no-half-created-account
  (testing "the row and the secured userdb exist together or not at all"
    (let [db        (migrated-db)
          destroyed (atom nil)]
      (with-redefs [db/exists? (constantly false)
                    db/use     (fn [name] {:name name})
                    db/secure  (fn [_ _] (throw (ex-info "couch is down" {})))
                    db/destroy (fn [db] (reset! destroyed (:name db)) (delay nil))]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"couch is down"
                              (sut/create-account! db))))
      (is (empty? (jdbc/execute! db ["SELECT id FROM users"]))
          "the insert rolled back, no orphan row")
      (is (= "userdb-1" @destroyed)
          "the userdb created for the rolled-back row is removed with it"))))


(deftest a-failed-provision-burns-nothing-for-the-next-attempt
  (testing "after a couch failure the same id provisions cleanly"
    (let [db (migrated-db)]
      (with-redefs [db/exists? (constantly false)
                    db/use     (fn [name] {:name name})
                    db/secure  (fn [_ _] (throw (ex-info "couch is down" {})))
                    db/destroy (fn [_] (delay nil))]
        (is (thrown? clojure.lang.ExceptionInfo (sut/create-account! db))))
      (with-redefs [db/exists? (constantly false)
                    db/use     (fn [name] {:name name})
                    db/secure  (fn [_ _] nil)]
        (is (= 1 (:id (sut/create-account! db)))
            "the id the failed attempt minted is minted again, not leaked")))))


(defn- ^File temp-dir
  []
  (.toFile (java.nio.file.Files/createTempDirectory
            "adopt-test"
            ^"[Ljava.nio.file.attribute.FileAttribute;"
            (into-array java.nio.file.attribute.FileAttribute []))))


(deftest a-legacy-database-moves-into-the-configured-home
  (testing "content and wal travel, the legacy trio disappears"
    (let [dir    (temp-dir)
          legacy (doto (File. dir "app.db") (spit "main-bytes"))
          _ (spit (File. dir "app.db-wal") "wal-bytes")
          _ (spit (File. dir "app.db-shm") "shm-bytes")
          target (File. dir "state/db.sqlite")]
      (#'sut/adopt-database! legacy {:dbname (.getPath target)})
      (is (= "main-bytes" (slurp target)))
      (is (= "wal-bytes" (slurp (File. dir "state/db.sqlite-wal"))))
      (is (not (.exists legacy)) "legacy main gone")
      (is (not (.exists (File. dir "app.db-wal"))) "legacy wal gone")
      (is (not (.exists (File. dir "app.db-shm"))) "legacy shm gone"))))


(deftest an-existing-target-is-never-clobbered
  (testing "rollback safety: newer data at the target survives, legacy stays for the operator"
    (let [dir    (temp-dir)
          legacy (doto (File. dir "app.db") (spit "old-bytes"))
          target (doto (File. dir "db.sqlite") (spit "newer-bytes"))]
      (#'sut/adopt-database! legacy {:dbname (.getPath target)})
      (is (= "newer-bytes" (slurp target)) "target untouched")
      (is (= "old-bytes" (slurp legacy)) "legacy left in place"))))


(deftest no-legacy-database-means-no-adoption
  (let [dir    (temp-dir)
        target (File. dir "db.sqlite")]
    (#'sut/adopt-database! (File. dir "app.db") {:dbname (.getPath target)})
    (is (not (.exists target)))))


(deftest the-default-path-is-its-own-home
  (testing "dev: legacy and target are the same file, nothing moves"
    (let [dir (temp-dir)
          db  (doto (File. dir "app.db") (spit "dev-bytes"))]
      (#'sut/adopt-database! db {:dbname (.getPath db)})
      (is (= "dev-bytes" (slurp db))))))


(deftest an-empty-pre-created-target-does-not-block-adoption
  (testing "systemd-tmpfiles pre-creates the target as a zero-length file (#225)"
    (let [dir    (temp-dir)
          legacy (doto (File. dir "app.db") (spit "real-bytes"))
          target (doto (File. dir "db.sqlite") (spit ""))]
      (#'sut/adopt-database! legacy {:dbname (.getPath target)})
      (is (= "real-bytes" (slurp target)) "adopted over the empty placeholder")
      (is (not (.exists legacy))))))

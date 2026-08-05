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

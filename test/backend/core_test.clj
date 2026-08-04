(ns backend.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [core :as sut]
   [migrations :as migrations]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as result-set])
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

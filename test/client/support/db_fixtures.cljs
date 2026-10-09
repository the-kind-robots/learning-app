(ns client.support.db-fixtures
  (:require
   [clojure.string :as str]
   [db :as db]
   [db.pouch :as pouch]
   [tasks :as tasks])
  (:require-macros
   [cljs.test :refer [async]]))


(defn- node-env?
  []
  (and (exists? js/process)
       (some? (.-versions js/process))
       (some? (.-node (.-versions js/process)))))


(defn- sanitize-name
  [name]
  (-> name
      (str/replace #"/" "-")
      (str/replace #"\s+" "-")))


(defn- ensure-dir!
  [path]
  (when (node-env?)
    (try
      (let [fs (js/require "fs")]
        (.mkdirSync fs path #js {:recursive true}))
      (catch :default _
        nil))))


(defn db-name
  [ns-name]
  (let [ns-name (sanitize-name (str ns-name))]
    (when (node-env?)
      (ensure-dir! "target/pouch"))
    (str "target/pouch/" ns-name)))


(defn ^:async destroy-test-db
  [db-name]
  (try
    (await (db/destroy (db/use db-name)))
    (catch :default _
      nil)))


(defn db-fixture-multi
  [db-names]
  {:before (fn []
             (async done
               (.finally (js/Promise.all (into-array (map destroy-test-db db-names))) done)))
   :after  (fn []
             (async done
               (.finally (js/Promise.all (into-array (map destroy-test-db db-names))) done)))})


(defn- role
  "Which of the app's databases a test database stands for, by the suffix
   its test named it with: `.user` or `.device`. Any other name stands for
   both, for a test that keeps every type in one database."
  [db-name]
  (cond
    (str/ends-with? db-name ".user")   :user/db
    (str/ends-with? db-name ".device") :device/db))


(defn- ^:async prepared
  "A test database. One that stands for device-db, or for both databases,
   carries the task queue's index, as device-db has it once the queue has
   started, so a test can run the queue without starting it."
  [db-name]
  (let [db (db/use db-name)]
    (when-not (= :user/db (role db-name))
      (await (pouch/ensure-index! {:device/db db} tasks/schema tasks/index)))
    db))


(defn with-test-db
  [db-name f]
  (.then (prepared db-name) f))


(defn with-test-dbs
  [db-names f]
  (.then (js/Promise.all (into-array (map prepared db-names))) #(f (vec %))))

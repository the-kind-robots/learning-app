(ns client.support.db-fixtures
  (:require
   [client.support.schemas :as schemas]
   [clojure.string :as str]
   [db :as db]
   [db.pouch :as pouch])
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


(defn db-fixture
  [db-name]
  {:before (fn []
             (async done
               (.finally (destroy-test-db db-name) done)))
   :after  (fn []
             (async done
               (.finally (destroy-test-db db-name) done)))})


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
  "A test database carries what `db.pouch/init!` gives the database it
   stands for at start-up (the indexes and views of the schemas that live
   there), so adapters can rely on them here as they do there."
  [db-name]
  (let [db  (db/use db-name)
        own (filter #(if-let [db-key (role db-name)]
                       (= db-key (:db %))
                       true)
                    schemas/all)]
    (await (pouch/ensure-indexes! db (pouch/indexes-of own)))
    (await (pouch/ensure-views! db (mapcat :views own)))
    db))


(defn with-test-db
  [db-name f]
  (.then (prepared db-name) f))


(defn with-test-dbs
  [db-names f]
  (.then (js/Promise.all (into-array (map prepared db-names))) #(f (vec %))))

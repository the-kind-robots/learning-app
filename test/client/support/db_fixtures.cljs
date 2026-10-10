(ns client.support.db-fixtures
  (:require
   [clojure.string :as str]
   [db :as db])
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


(defn with-test-db
  "Calls `f` with the test database `db-name`. Returns a promise of what
   `f` returns."
  [db-name f]
  (.then (js/Promise.resolve (db/use db-name)) f))


(defn with-test-dbs
  "Calls `f` with the vector of the test databases `db-names`. Returns a
   promise of what `f` returns."
  [db-names f]
  (.then (js/Promise.resolve (mapv db/use db-names)) f))

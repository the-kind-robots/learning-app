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


(defn with-test-db
  [db-name f]
  (f (db/use db-name)))


(defn with-test-dbs
  [db-names f]
  (f (mapv db/use db-names)))

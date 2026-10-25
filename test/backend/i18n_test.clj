(ns backend.i18n-test
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.test :refer [deftest is]]))


(defn- dictionary
  [language]
  (edn/read-string (slurp (io/resource (str "i18n/" language ".edn")))))


(deftest turkish-has-every-text-of-russian
  (is (= #{}
         (set/difference (set (keys (dictionary "ru")))
                         (set (keys (dictionary "tr")))))))

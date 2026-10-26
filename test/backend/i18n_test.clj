(ns backend.i18n-test
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.test :refer [are deftest]]))


(defn- dictionary
  [language]
  (edn/read-string (slurp (io/resource (str "i18n/" language ".edn")))))


(deftest every-language-has-every-text-of-russian
  (are [language] (= #{}
                     (set/difference (set (keys (dictionary "ru")))
                                     (set (keys (dictionary language)))))
   "tr"
   "fr"))

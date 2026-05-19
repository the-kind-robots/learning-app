(ns ports.navigation
  (:require
   [reitit.frontend.easy :as rfe]))


(defn start!
  [_deps]
  {:navigation/navigate rfe/navigate
   :navigation/replace  rfe/replace-state})

(ns ports.dictionary
  (:require
   [adapters.dictionary :as dictionary]))


(defn start!
  [{:keys [db]}]
  {:dictionary/completions (fn completions
                             [prefix]
                             (dictionary/completions db prefix))})

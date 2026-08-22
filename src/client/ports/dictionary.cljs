(ns ports.dictionary
  (:require
   [adapters.dictionary :as dictionary]))


(defn start!
  [{:keys [db]}]
  {:dictionary/completions (fn completions
                             [prefix]
                             (dictionary/completions db prefix))
   ;; A reading, not a gate: completions are asked for whatever this says. It
   ;; is here so a caller can tell an empty answer from a missing dictionary
   ;; (#312).
   :dictionary/ready?      (fn ready?
                             []
                             (dictionary/ready? db))})

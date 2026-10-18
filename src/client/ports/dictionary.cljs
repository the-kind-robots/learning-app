(ns ports.dictionary
  (:require
   [adapters.dictionary :as dictionary]))


(defn ^:async start!
  "Opens the dictionary's database and resolves with the capability."
  []
  (let [db (await (dictionary/open! {}))]
    {:dictionary/completions (fn completions
                               [prefix]
                               (dictionary/completions db prefix))
     ;; A reading, not a gate: completions are asked for whatever this says. It lets a caller tell an empty answer from a missing dictionary.
     :dictionary/ready?      (fn ready?
                               []
                               (dictionary/ready? db))}))

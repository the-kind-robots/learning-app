(ns ports.dictionary
  (:require
   [adapters.dictionary :as dictionary]))


(defn ^:async start!
  "Opens the dictionary's database and resolves with the capability."
  [_deps]
  (let [db (await (dictionary/open! {}))]
    {:dictionary/completions (fn completions
                               [prefix]
                               (dictionary/completions db prefix))
     ;; A reading, not a gate: completions are asked for whatever this says. It
     ;; is here so a caller can tell an empty answer from a missing dictionary
     ;; (#312).
     :dictionary/ready?      (fn ready?
                               []
                               (dictionary/ready? db))}))

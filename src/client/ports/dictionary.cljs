(ns ports.dictionary
  "The dictionary the learner types against. `adapters.dictionary` does the work."
  (:require
   [adapters.dictionary :as dictionary]))


(defprotocol Dictionary
  (completions [d prefix]
    "A promise of the lemmas that complete `prefix`, best first. Empty for no match or for no database; `ready?` tells which.")
  (ready? [d]
    "Whether this tab has the dictionary now. A reading, not a gate: completions are asked for whatever it says."))


(defn ^:async start!
  "Opens the dictionary's database and resolves with the Dictionary."
  []
  (let [db (await (dictionary/open! {}))]
    (reify Dictionary
      (completions [_ prefix] (dictionary/completions db prefix))
      (ready? [_] (dictionary/ready? db)))))

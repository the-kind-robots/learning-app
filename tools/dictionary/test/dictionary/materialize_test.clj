(ns dictionary.materialize-test
  (:require
   [clojure.test :refer [deftest is]]
   [dictionary.materialize :as materialize]))


(def ^:private surface-forms #'materialize/surface-forms)


(deftest a-lemma-is-found-by-its-spelling-with-and-without-umlauts
  (is (= #{"die gruesse" "gruesse" "gruessen" "die grusse" "grusse" "grussen"}
         (set (map :normalized-form
                   (surface-forms "l1" "die Grüße" ["Grüßen"]))))
      "the digraph spelling stays, the base-letter spelling is added")
  (is (= #{"fuer" "fur"}
         (set (map :normalized-form (surface-forms "l2" "für" []))))))


(deftest a-word-without-umlauts-adds-no-duplicate-form
  (is (= ["hund" "hunde"]
         (map :normalized-form (surface-forms "l3" "Hund" ["Hunde"])))))

(ns client.pages.collections-presenter-test
  (:require
   [cljs.test :refer-macros [are deftest]]
   [pages.collections.presenter :as sut]))


(defn- tile-names
  [collection-names]
  (mapv :name (sut/tiles {:collections/items (map-indexed (fn [i n] {:id (str i) :name n :word-ids []})
                                                          collection-names)})))


(deftest tiles-follow-all-words-then-names-alphabetically-ignoring-case
  (are [given expected] (= expected (tile-names given))
    ["bogen" "Ärger" "Zug" "Ausflug"] ["Всё подряд" "Ärger" "Ausflug" "bogen" "Zug"]
    ["banane" "Apfel" "cherry"]       ["Всё подряд" "Apfel" "banane" "cherry"]
    ["b" "A" "a" "B"]                 ["Всё подряд" "A" "a" "b" "B"]
    []                                ["Всё подряд"]))

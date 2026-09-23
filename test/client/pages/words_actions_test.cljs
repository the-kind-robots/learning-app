(ns client.pages.words-actions-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [pages.words.actions :as sut]
   [pages.words.presenter :as presenter]))


(defn- word-rows
  [n]
  (vec (for [i (range n)]
         {:id (str "word-" i) :value (str "Wort" i) :translation [] :retention-level i})))


(defn- reload-effect
  "What `:action/reload-page` re-dispatches after a sync pull: what the page
   stored under `:page/load`. An action rather than the effect, so the pull's
   read is numbered when it happens rather than carrying the number of the
   read that produced these rows (GH-439)."
  [state]
  (:page/load state))


(deftest a-background-reload-asks-for-the-page-the-reader-has
  (testing "three pages loaded, then a pull arrives"
    (let [state (sut/words-shown {:limit   150
                                  :matches 400
                                  :search  ""
                                  :total   400
                                  :words   (word-rows 150)})]
      (is (= [:action/load-words {:limit 150 :search ""}] (reload-effect state))
          "the reload asks for the 150 rows on screen, not the first page")
      (is (= 150 (:words/limit state)))))
  (testing "the first page is still the first page"
    (let [state (sut/words-shown {:limit   presenter/page-size
                                  :matches 400
                                  :search  ""
                                  :total   400
                                  :words   (word-rows presenter/page-size)})]
      (is (= [:action/load-words {:limit 50 :search ""}] (reload-effect state))))))


(deftest a-background-reload-keeps-the-query
  (testing "a pull must not drop the filter the reader is looking through"
    (let [state (sut/words-shown {:limit   100
                                  :matches 120
                                  :search  "hund"
                                  :total   400
                                  :words   (word-rows 100)})]
      (is (= [:action/load-words {:limit 100 :search "hund"}] (reload-effect state)))
      (is (= "hund" (:words/search state))))))

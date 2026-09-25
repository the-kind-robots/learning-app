(ns client.page-reload-test
  "What a sync pull re-reads follows the route on display, not what a read
   wrote (#486)."
  (:require
   [application :as sut]
   [cljs.test :refer-macros [deftest is testing]]
   [pages.words.actions :as words]))


(deftest each-screen-re-reads-its-own-data
  (is (= [:effect/refresh-home] (sut/page-reload {:page/current :page/home})))
  (is (= [:action/reload-words] (sut/page-reload {:page/current :page/words})))
  (is (= [:effect/load-collections]
         (sut/page-reload {:page/current :page/collections}))))


(deftest a-lesson-and-no-screen-re-read-nothing
  (is (nil? (sut/page-reload {:page/current :page/lesson})))
  (is (nil? (sut/page-reload {:page/current :page/loading}))))


(deftest a-late-words-read-leaves-home-s-reload-alone
  (testing "rows read for a screen already left do not change what home re-reads"
    (let [state (merge {:page/current :page/home}
                       (words/words-shown {:limit   50
                                           :matches 1
                                           :search  ""
                                           :total   1
                                           :words   [{:id          "w"
                                                      :value       "Haus"
                                                      :translation []
                                                      :retention-level 0}]}))]
      (is (= :page/home (:page/current state)))
      (is (= [:effect/refresh-home] (sut/page-reload state))))))

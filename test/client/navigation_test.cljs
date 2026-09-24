(ns client.navigation-test
  (:require
   [cljs.test :refer-macros [deftest is]]
   [ports.navigation :as sut]))


(deftest move-keeps-the-history-at-home-and-one-screen
  (is (nil? (sut/move {:at-home? true :to :page/home})) "home to home writes nothing")
  (is (= :push (sut/move {:at-home? true :to :page/words})) "home to a screen pushes")
  (is (= :replace (sut/move {:at-home? false :to :page/lesson})) "a screen to a screen replaces")
  (is (= :back (sut/move {:at-home? false :to :page/home})) "a screen to home steps back"))

(ns client.pages.collections-tap-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [pages.collections.effects :as sut]))


(deftest a-lift-without-movement-is-a-tap
  (testing "within 10 px of travel and 2 px of scroll, measured at the lift, the cancelled tap fires"
    (is (true? (:fired? (sut/on-lift {:moved-px 0 :scroll-delta 0}))))
    (is (some? (sut/on-lift {:moved-px 10 :scroll-delta 2})))
    (is (some? (sut/on-lift {})) "no move seen counts as 0"))
  (testing "a drag or a scroll is not a tap"
    (is (nil? (sut/on-lift {:moved-px 11 :scroll-delta 0})))
    (is (nil? (sut/on-lift {:moved-px 0 :scroll-delta 3})))
    (is (nil? (sut/on-lift {:moved-px 0 :scroll-delta -3})))))


(deftest a-gesture-fires-at-most-once
  (testing "a lift after the gesture fired — a long press — is nothing"
    (is (nil? (sut/on-lift {:moved-px 0 :scroll-delta 0 :fired? true})))
    (is (nil? (sut/on-lift (sut/on-lift {:moved-px 0 :scroll-delta 0}))))))


(deftest focus-goes-to-the-next-target-else-the-previous
  (let [ids ["main" "collection:kurs" "collection:k1" "collection:solo"]]
    (is (= "collection:k1" (sut/neighbour ids "collection:kurs"))
        "a deleted folder parent leaves a label; its first row follows it")
    (is (= "collection:k1" (sut/neighbour ids "collection:solo")) "the last target falls back")
    (is (= "collection:solo" (sut/neighbour ids "collection:k1")))))

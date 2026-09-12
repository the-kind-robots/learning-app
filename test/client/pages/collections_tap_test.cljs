(ns client.pages.collections-tap-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [pages.collections.tap :as sut]))


(deftest a-cancel-without-movement-is-a-tap
  (testing "within 10 px and 2 px of scroll the cancelled tap fires"
    (is (true? (:dispatch? (sut/on-cancel {:moved-px 0 :scroll-delta 0}))))
    (is (true? (:dispatch? (sut/on-cancel {:moved-px 10 :scroll-delta 2}))))
    (is (true? (:dispatch? (sut/on-cancel {}))) "no move seen counts as 0"))
  (testing "a drag or a scroll is not a tap"
    (is (false? (:dispatch? (sut/on-cancel {:moved-px 11 :scroll-delta 0}))))
    (is (false? (:dispatch? (sut/on-cancel {:moved-px 0 :scroll-delta 3}))))
    (is (false? (:dispatch? (sut/on-cancel {:moved-px 0 :scroll-delta -3}))))))


(deftest a-gesture-fires-at-most-once
  (testing "a click after a recovered cancel is a no-op"
    (let [after-cancel (sut/on-cancel {:moved-px 0 :scroll-delta 0})
          after-click  (sut/on-click after-cancel)]
      (is (true? (:dispatch? after-cancel)))
      (is (false? (:dispatch? after-click)))))
  (testing "a plain click fires once"
    (let [after-click (sut/on-click {})]
      (is (true? (:dispatch? after-click)))
      (is (false? (:dispatch? (sut/on-click after-click))))))
  (testing "a cancel after a click does not fire again"
    (is (false? (:dispatch? (sut/on-cancel (sut/on-click {})))))))

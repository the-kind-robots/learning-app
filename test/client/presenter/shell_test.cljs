(ns client.presenter.shell-test
  (:require
   [application.presenter :as sut]
   [cljs.test :refer-macros [deftest is testing]]))


(deftest shell-props-offers-the-update-only-while-a-worker-waits
  (testing "no flag, no control"
    (is (false? (:show-update? (sut/shell-props {})))))
  (testing "a waiting worker shows the control"
    (is (true? (:show-update? (sut/shell-props {:pwa/update-waiting? true})))))
  (testing "the flag cleared hides it again"
    (is (false? (:show-update? (sut/shell-props {:pwa/update-waiting? false}))))))

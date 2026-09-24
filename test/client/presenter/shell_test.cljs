(ns client.presenter.shell-test
  (:require
   [application.presenter :as sut]
   [cljs.test :refer-macros [deftest is testing]]))


(deftest shell-props-offers-the-update-only-while-a-new-build-waits
  (testing "no flag, no control"
    (is (false? (:show-update? (sut/shell-props {})))))
  (testing "a waiting build shows the control"
    (is (true? (:show-update? (sut/shell-props {:pwa/new-build-waiting? true})))))
  (testing "the flag cleared hides it again"
    (is (false? (:show-update? (sut/shell-props {:pwa/new-build-waiting? false}))))))


(deftest shell-props-offers-sync-on-home-with-an-account-only
  (testing "home with an account shows the control"
    (is (true? (:show-sync? (sut/shell-props {:page/current   :page/home
                                              :app/account-id "7"})))))
  (testing "home without an account hides it"
    (is (false? (:show-sync? (sut/shell-props {:page/current :page/home})))))
  (testing "every other page hides it, account or not"
    (doseq [page [:page/words :page/lesson :page/collections nil]]
      (is (false? (:show-sync? (sut/shell-props {:page/current   page
                                                 :app/account-id "7"})))
          (str page)))))


(deftest shell-props-puts-the-grid-on-home-and-the-close-mark-elsewhere
  (is (= :collections (:corner (sut/shell-props {:page/current :page/home}))))
  (doseq [page [:page/words :page/lesson :page/collections]]
    (is (= :close (:corner (sut/shell-props {:page/current page}))) (str page)))
  (testing "no page on display yet, no corner control"
    (is (nil? (:corner (sut/shell-props {:page/current :page/loading}))))))

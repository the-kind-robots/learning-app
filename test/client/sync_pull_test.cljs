(ns client.sync-pull-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [sync :as sut]))


(deftest a-navigation-soon-after-a-pass-runs-no-pass
  (testing "within the interval, nothing written locally: no pass"
    (is (false? (sut/pull-due? {:reason nil :dirty? false :last-pass-ms 100000 :now-ms 110000}))))
  (testing "the interval has passed"
    (is (true? (sut/pull-due?
                {:reason nil :dirty? false :last-pass-ms 100000 :now-ms (+ 100000 sut/pass-interval-ms)}))))
  (testing "no pass yet"
    (is (true? (sut/pull-due? {:reason nil :dirty? false :last-pass-ms nil :now-ms 5000})))))


(deftest a-poke-or-a-local-write-always-passes
  (is (true? (sut/pull-due? {:reason :poke :dirty? false :last-pass-ms 100000 :now-ms 100500})))
  (is (true? (sut/pull-due? {:reason nil :dirty? true :last-pass-ms 100000 :now-ms 100500}))))

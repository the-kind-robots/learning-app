(ns client.service-worker-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [service-worker :as sut]))


(defn- collecting-dispatch
  "A dispatch that keeps what it was given, so the saves can be read back."
  [saved]
  (fn [effects]
    (swap! saved into effects)))


(defn- registration-waiting
  "A registration whose waiting build counts the messages it receives."
  [received]
  #js {:waiting #js {:postMessage (fn [message] (swap! received conj message))}})


(deftest taking-a-new-build-takes-the-announcement-down
  (testing "a build waiting is asked to serve now, and the announcement goes"
    (let [saved    (atom [])
          received (atom [])]
      (is (true? (sut/take-new-build! (collecting-dispatch saved)
                                      (registration-waiting received))))
      (is (= 1 (count @received)))
      (is (= "activate-waiting" (.-type (first @received))))
      (is (= [[:effect/save {:pwa/new-build-waiting? false}]] @saved))))

  (testing "nothing waiting: the announcement still goes, so «Обновить» goes"
    (let [saved (atom [])]
      (is (false? (sut/take-new-build! (collecting-dispatch saved) #js {:waiting nil})))
      (is (= [[:effect/save {:pwa/new-build-waiting? false}]] @saved))))

  (testing "no registration at all is the same answer"
    (let [saved (atom [])]
      (is (false? (sut/take-new-build! (collecting-dispatch saved) nil)))
      (is (= [[:effect/save {:pwa/new-build-waiting? false}]] @saved)))))

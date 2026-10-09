(ns client.runtime.system-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [runtime.system :as sut]))


(deftest startup-fails-on-a-missing-dependency
  (testing "missing component keys fail before startup"
    (is (thrown-with-msg?
         js/Error
         #"Missing dependency definitions"
         (sut/resolve-dependencies {:app/a {:requires {:b :app/b}
                                            :start    identity}})))))


(deftest startup-fails-on-a-dependency-cycle
  (testing "cyclic components fail before startup"
    (is (thrown-with-msg?
         js/Error
         #"Dependency cycle detected"
         (sut/resolve-dependencies {:app/a {:requires {:b :app/b}
                                            :start    identity}
                                    :app/b {:requires {:a :app/a}
                                            :start    identity}})))))


(deftest a-failed-startup-stops-what-had-started-in-reverse-order
  (async-testing "failed startup stops already-started components in reverse order"
    (let [events (atom [])]
      (try
        (await (sut/start!
                {:app/a {:start (fn [_]
                                  (swap! events conj :a-start)
                                  :a)
                         :stop  (fn [_]
                                  (swap! events conj :a-stop))}
                 :app/b {:requires {:a :app/a}
                         :start    (fn [_]
                                     (swap! events conj :b-start)
                                     :b)
                         :stop     (fn [_]
                                     (swap! events conj :b-stop))}
                 :app/c {:requires {:b :app/b}
                         :start    (fn [_]
                                     (swap! events conj :c-start)
                                     (throw (js/Error. "boom")))}}))
        (is false "startup should fail")
        (catch js/Error err
          (is (= "boom" (.-message err)))))
      (is (= [:a-start :b-start :c-start :b-stop :a-stop]
             @events)))))


(deftest a-failing-stop-does-not-keep-the-others-from-running
  (async-testing "all stops run even if one throws"
    (let [events (atom [])]
      (await (sut/stop!
              [(fn [] (swap! events conj :a-stop))
               (fn []
                 (swap! events conj :b-stop)
                 (throw (js/Error. "ignore")))
               (fn [] (swap! events conj :c-stop))]))
      (is (= [:c-stop :b-stop :a-stop] @events)))))



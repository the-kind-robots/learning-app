(ns backend.single-flight-test
  (:require
   [backend.support.generation :as support.generation]
   [clojure.test :refer [deftest is testing]]
   [single-flight :as sut])
  (:import
   [java.util.concurrent TimeoutException]))


(set! *warn-on-reflection* true)


(defn- in-flight?
  [task-id]
  (contains? @@#'sut/flights task-id))


(deftest a-thrown-exception-reaches-every-waiting-caller
  (support.generation/with-joins-counted
   4
   (fn [joined]
     (let [calls   (atom 0)
           release (promise)
           callers (doall
                    (repeatedly 5
                                #(future
                                  (try
                                    (sut/run ::thrown
                                             (fn []
                                               (swap! calls inc)
                                               @release
                                               (throw (ex-info "provider away" {}))))
                                    (catch Exception error
                                      error)))))]
       (is (true? (joined)) "four callers joined the one running")
       (deliver release true)
       (is (= 1 @calls))
       (is (every? #(= "provider away" (ex-message @%)) callers))))))


(deftest no-caller-receives-a-result-while-its-key-is-still-taken
  (testing "the key is freed inside the run, so whoever holds the result can start afresh"
    (support.generation/with-joins-counted
     1
     (fn [joined]
       (let [release (promise)
             started (promise)
             leader  (future
                      (let [result (sut/run ::freed
                                            (fn []
                                              (deliver started true)
                                              @release
                                              :first))]
                        [result (in-flight? ::freed)]))
             _ (deref started 2000 nil)
             waiter  (future
                      (let [result (sut/run ::freed (fn [] :never))]
                        [result (in-flight? ::freed)]))]
         (is (true? (joined)))
         (deliver release true)
         (is (= [:first false] @leader))
         (is (= [:first false] @waiter))
         (is (= :second (sut/run ::freed (fn [] :second)))
             "a caller arriving after the run starts a run of its own"))))))


(deftest the-key-is-free-once-the-run-ends
  (testing "a later caller starts afresh, after a result and after an exception"
    (let [calls (atom 0)]
      (is (= 1 (sut/run ::k #(swap! calls inc))))
      (is (thrown? Exception (sut/run ::k #(throw (ex-info "once" {})))))
      (is (= 2 (sut/run ::k #(swap! calls inc))))
      (is (not (in-flight? ::k))))))


(deftest a-waiter-gives-up-when-the-run-it-joined-never-ends
  (with-redefs-fn {#'sut/longest-join-wait-ms 100}
    (fn []
      (support.generation/with-joins-counted
       1
       (fn [joined]
         (let [wedged  (promise)
               running (promise)
               leader  (future (sut/run ::wedged
                                        (fn []
                                          (deliver running true)
                                          @wedged)))]
           (try
             (is (true? (deref running 2000 false)) "the run holds the key")
             (let [waiter (future
                           (try
                             (sut/run ::wedged (fn [] :never))
                             (catch TimeoutException _
                               ::gave-up)))]
               (is (true? (joined)))
               (is (= ::gave-up (deref waiter 2000 ::still-waiting))
                   "the waiter answers inside its own bound")
               (is (in-flight? ::wedged) "the wedged run still holds its key"))
             (finally
              (deliver wedged :late)))
           (is (= :late @leader) "the run still answers its own caller")
           (is (not (in-flight? ::wedged)) "and its end freed the key")
           (is (= :fresh (sut/run ::wedged (fn [] :fresh)))
               "a later caller starts a run of its own")))))))

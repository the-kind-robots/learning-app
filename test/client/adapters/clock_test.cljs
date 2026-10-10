(ns client.adapters.clock-test
  "`:clock/sleep`: a timer a signal can cancel, with nothing left behind."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [cljs.test :refer-macros [deftest is]]
   [ports.clock :as clock]))


(defn- sleep
  "Starts a sleep. Plain, so the promise is not awaited on the way out."
  [ms signal]
  (let [sleep (:clock/sleep (clock/start! {}))]
    (sleep ms signal)))


(defn- ^:async watching
  "Calls `f` with a controller, its signal, and atoms counting the timers and
   \"abort\" listeners alive, and awaits it. The globals are put back after."
  [f]
  (let [controller (js/AbortController.)
        signal     (.-signal controller)
        live       (atom 0)
        timers     (atom 0)
        add        (.-addEventListener signal)
        remove     (.-removeEventListener signal)
        set-t      js/setTimeout
        clear-t    js/clearTimeout]
    (set! (.-addEventListener signal)
          (fn [type & more]
            (when (= "abort" type) (swap! live inc))
            (.apply add signal (to-array (cons type more)))))
    (set! (.-removeEventListener signal)
          (fn [type & more]
            (when (= "abort" type) (swap! live dec))
            (.apply remove signal (to-array (cons type more)))))
    (set! js/setTimeout (fn [g ms] (swap! timers inc) (set-t (fn [] (swap! timers dec) (g)) ms)))
    (set! js/clearTimeout (fn [t] (swap! timers dec) (clear-t t)))
    (try
      (await (f controller signal live timers))
      (finally
        (set! js/setTimeout set-t)
        (set! js/clearTimeout clear-t)))))


(deftest sleep-resolves-after-ms-and-leaves-no-listener
  (async-testing "the timer fires: the promise resolves with nil and the abort listener is gone"
    (await
     (watching
      (^:async fn [_ signal live timers]
       (let [p (sleep 5 signal)]
         (is (= 1 @live) "listening while it waits")
         (is (= 1 @timers) "timing while it waits")
         (is (nil? (await p)))
         (is (zero? @live))
         (is (zero? @timers))))))))


(deftest sleep-ends-at-once-on-abort
  (async-testing "aborting resolves at once and clears the timer and the listener"
    (await
     (watching
      (^:async fn [controller signal live timers]
       (let [p (sleep 60000 signal)]
         (is (= 1 @timers))
         (.abort controller)
         (is (nil? (await p)))
         (is (zero? @timers))
         (is (zero? @live))))))))


(deftest sleep-with-an-aborted-signal-resolves-at-once
  (async-testing "an already aborted signal never starts a long wait"
    (let [controller (js/AbortController.)]
      (.abort controller)
      (is (nil? (await (sleep 60000 (.-signal controller))))))))


(deftest sleep-without-a-signal-is-a-plain-sleep
  (async-testing "a nil signal just waits"
    (is (nil? (await (sleep 5 nil))))))

(ns ports.clock
  (:require
   [abort :as abort]
   [utils :as utils]))


(defn- after
  "Calls `f` once `ms` milliseconds have passed. Returns a function that
   cancels the call."
  [ms f]
  (let [timer (js/setTimeout f ms)]
    #(js/clearTimeout timer)))


(defn- sleep
  "A promise that resolves with nil after `ms`, or at once when `signal`
   aborts. Neither the timer nor the listener outlives the wait."
  [ms ^js signal]
  (js/Promise.
   (fn [resolve]
     ;; Each end of the wait undoes the other, so one needs the other's handle.
     (let [unlisten (volatile! (fn []))
           cancel   (after ms (fn []
                                (@unlisten)
                                (resolve nil)))]
       (vreset! unlisten (abort/on-abort signal (fn []
                                                  (cancel)
                                                  (@unlisten)
                                                  (resolve nil))))))))


(defn start!
  [_deps]
  {:clock/after   after
   :clock/sleep   sleep
   :clock/now-iso utils/now-iso
   :clock/now-ms  utils/now-ms})

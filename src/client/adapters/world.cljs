(ns adapters.world
  "The world the app lives in, apart from its screens and its storage: the
   time, the timers and chance."
  (:require
   [abort :as abort]
   [utils :as utils]))


(def now-iso
  "The time now, as an ISO 8601 string."
  utils/now-iso)


(def now-ms
  "The time now, in milliseconds."
  utils/now-ms)


(defn random
  "A number in [0, 1)."
  []
  (js/Math.random))


(defn after
  "Calls `f` once `ms` milliseconds have passed. Returns a function that
   cancels the call."
  [ms f]
  (let [timer (js/setTimeout f ms)]
    #(js/clearTimeout timer)))


(defn sleep
  "A promise that resolves with nil after `ms`, or at once when `signal`
   aborts. Neither the timer nor the listener outlives the wait."
  [ms ^js signal]
  (js/Promise.
   (fn [resolve]
     ;; Each end of the wait undoes the other, so one needs the other's handle.
     (let [unlisten (volatile! (fn []))
           cancel   (after ms
                           (fn []
                             (@unlisten)
                             (resolve nil)))]
       (vreset! unlisten
                (abort/on-abort signal
                                (fn []
                                  (cancel)
                                  (@unlisten)
                                  (resolve nil))))))))

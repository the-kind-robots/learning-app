(ns browser
  "Giving the page a turn.")


(defonce ^:private turns
  ;; Resolvers waiting for their turn, oldest first.
  #js [])


(defonce ^:private channel
  (let [c (js/MessageChannel.)]
    (set! (.-onmessage (.-port1 c)) (fn [_] ((.shift turns))))
    c))


(def ^:private scheduler
  ;; Chosen once, at load: the standard `scheduler.yield()` where the
  ;; browser has it, else nil.
  (let [^js s (.-scheduler js/globalThis)]
    (when (and s (fn? (.-yield s))) s)))


(defn yield
  "A promise that resolves in a later task, so the page can paint and take input."
  []
  (if scheduler
    (.yield ^js scheduler)
    ;; Not setTimeout: nested timers wait at least 4 ms (HTML spec, timers).
    (js/Promise.
     (fn [resolve]
       (.push turns resolve)
       (.postMessage (.-port2 channel) nil)))))

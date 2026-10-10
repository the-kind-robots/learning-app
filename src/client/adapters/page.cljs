(ns adapters.page
  "The page as the browser reports it: shown or hidden, online or offline."
  (:require
   [abort]))


(defn visible?
  "Whether the page is shown."
  []
  (= "visible" (.-visibilityState js/document)))


(defn online?
  "Whether the device is online."
  []
  (.-onLine js/navigator))


(defn active?
  "Whether the page is visible and the device is online."
  []
  (and (visible?) (online?)))


(defn- listen
  "Calls `f` on each `event` of `target`. Returns a function that stops it."
  [target event f]
  (let [listener (fn [_] (f))]
    (.addEventListener target event listener)
    (fn stop []
      (.removeEventListener target event listener))))


(defn on-visibility-change
  "Calls `f` whenever the page is shown or hidden. Returns a function that
   stops it."
  [f]
  (listen js/document "visibilitychange" f))


(defn on-online
  "Calls `f` whenever the device comes online. Returns a function that stops
   it."
  [f]
  (listen js/window "online" f))


(defn on-offline
  "Calls `f` whenever the device goes offline. Returns a function that stops
   it."
  [f]
  (listen js/window "offline" f))


(defn on-inactive
  "Calls `f` when the page stops being active: it is hidden or the device goes
   offline. Stops listening when `signal`, an AbortSignal, aborts. Returns a
   function that stops it sooner."
  [f signal]
  (let [check (fn []
                (when-not (active?)
                  (f)))
        stops [(on-visibility-change check)
               (on-offline check)]
        stop  (fn stop []
                (run! #(%) stops))]
    (abort/on-abort signal stop)
    stop))


(defn until-active
  "A promise that resolves once the page is active, at once if it already is.
   When `signal`, an AbortSignal, aborts first, the promise stays pending and
   no listener is left."
  [signal]
  (js/Promise.
   (fn [resolve]
     (if (active?)
       (resolve nil)
       (let [stops   (volatile! [])
             release (fn []
                       (run! #(%) @stops))
             check   (fn []
                       (when (active?)
                         (release)
                         (resolve nil)))]
         ;; The listeners are recorded before the abort listener: an already
         ;; aborted signal calls `release` back at once.
         (vreset! stops [(on-visibility-change check)
                         (on-online check)])
         (vswap! stops conj (abort/on-abort signal release)))))))

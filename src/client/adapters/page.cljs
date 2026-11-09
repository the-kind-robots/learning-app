(ns adapters.page
  "The page as the browser reports it: shown or hidden, online or offline, the virtual keyboard over it, and the
   colour scheme picked on the device."
  (:require
   [abort]
   [browser]
   [clojure.string :as str]
   [colour-scheme :as colour-scheme]))


(defn visible?
  "Whether the page is shown."
  []
  (= "visible" (.-visibilityState js/document)))


(defn online?
  "Whether the device is online."
  []
  (.-onLine js/navigator))


(defn system-language
  "The language tag of the system, `de-DE` say."
  []
  (.-language js/navigator))


(defn scheme
  "The colour scheme picked on this device: `:scheme/light`, `:scheme/dark` or `:scheme/system`."
  []
  (->> (str/split js/document.cookie #";\s*")
       (some #(let [[cookie value] (str/split % #"=" 2)]
                (when (= colour-scheme/cookie cookie)
                  value)))
       colour-scheme/picked))


(defn choose-scheme!
  "Keeps `scheme` on this device, for 400 days, Chrome's longest, and shows it: the elements marked `data-night`
   take its night media. `:scheme/system` drops the pick. The new colours are computed while the page is marked
   `data-scheme-change`, under which nothing transitions, so every colour changes at once. The browser's bar cannot
   dissolve with the screen: it takes the scheme 125 ms later, halfway through the browser's 250 ms dissolve."
  [scheme]
  (set! js/document.cookie
        (if (= :scheme/system scheme)
          (str colour-scheme/cookie "=; path=/; max-age=0")
          (str colour-scheme/cookie "=" (name scheme) "; path=/; max-age=34560000; SameSite=Lax; Secure")))
  (let [^js root js/document.documentElement
        night!   (fn [selector]
                   (let [^js elements (js/document.querySelectorAll selector)]
                     (.forEach elements
                               (fn [^js element] (.setAttribute element "media" (colour-scheme/night-media scheme))))))]
    (.setAttribute root "data-scheme-change" "")
    (night! "link[data-night], style[data-night]")
    ;; Asking for a box makes the browser compute the new style now, under the mark.
    (.getBoundingClientRect js/document.body)
    (.removeAttribute root "data-scheme-change")
    (js/setTimeout #(night! "meta[data-night]") 125)))


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
         ;; The listeners are recorded before the abort listener: an already aborted signal calls `release` back at once.
         (vreset! stops
                  [(on-visibility-change check)
                   (on-online check)])
         (vswap! stops conj (abort/on-abort signal release)))))))


(defn yield
  "A promise that resolves once the page has had a turn to paint."
  []
  (browser/yield))


(defn overlay-keyboard!
  "Tells the virtual keyboard, where the browser has one, whether it overlays the content (`overlays?`, nil
   for no) or resizes it."
  [overlays?]
  (when (js-in "virtualKeyboard" js/navigator)
    (when-let [vk (.-virtualKeyboard js/navigator)]
      (set! (.-overlaysContent vk) overlays?))))

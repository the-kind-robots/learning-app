(ns ports.page
  "The page the app runs in, as the application reaches it: whether it is shown and the device online, and when
   that changes, and the colour scheme picked on the device. `adapters.page` does the work."
  (:require
   [adapters.page :as page]))


(defprotocol Page
  (active?
    [p]
    "Whether the page is visible and the device is online.")
  (online?
    [p]
    "Whether the device is online.")
  (visible?
    [p]
    "Whether the page is shown.")
  (system-language
    [p]
    "The language tag of the system, `de-DE` say.")
  (scheme
    [p]
    "The colour scheme picked on this device: `:scheme/light`, `:scheme/dark` or `:scheme/system`.")
  (choose-scheme!
    [p scheme]
    "Keeps `scheme` on this device and shows it at once.")
  (on-inactive
    [p f signal]
    "Calls `f` when the page stops being active, until `signal` aborts. Returns a function that stops it sooner.")
  (on-offline
    [p f]
    "Calls `f` whenever the device goes offline. Returns a function that stops it.")
  (on-online
    [p f]
    "Calls `f` whenever the device comes online. Returns a function that stops it.")
  (on-visibility-change
    [p f]
    "Calls `f` whenever the page is shown or hidden. Returns a function that stops it.")
  (visibility-changes
    [p]
    "How many times the page has been shown or hidden since it opened.")
  (until-active
    [p signal]
    "A promise that resolves once the page is active; it stays pending if `signal` aborts first.")
  (yield
    [p]
    "A promise that resolves once the page has had a turn to paint.")
  (overlay-keyboard!
    [p overlays?]
    "Has the virtual keyboard overlay the content when `overlays?`, otherwise resize it. A screen sets it as it is entered."))


(defn start!
  "The page. A page shown again from the back-forward cache gets the keyboard mode it last had. The device's
   scheme is kept anew: its 400 days start over, and a page the service worker cached under an older pick shows
   this one."
  []
  (let [overlays (atom false)
        changes  (atom 0)]
    (js/window.addEventListener "pageshow" (fn [_] (page/overlay-keyboard! @overlays)))
    (page/on-visibility-change #(swap! changes inc))
    (page/choose-scheme! (page/scheme))
    (reify Page
      (active? [_] (page/active?))
      (online? [_] (page/online?))
      (visible? [_] (page/visible?))
      (system-language [_] (page/system-language))
      (scheme [_] (page/scheme))
      (choose-scheme! [_ scheme] (page/choose-scheme! scheme))
      (on-inactive [_ f signal] (page/on-inactive f signal))
      (on-offline [_ f] (page/on-offline f))
      (on-online [_ f] (page/on-online f))
      (on-visibility-change [_ f] (page/on-visibility-change f))
      (visibility-changes [_] @changes)
      (until-active [_ signal] (page/until-active signal))
      (yield [_] (page/yield))
      (overlay-keyboard! [_ overlays?]
        (reset! overlays overlays?)
        (page/overlay-keyboard! overlays?)))))

(ns ports.page
  "The page the app runs in, as the application reaches it: whether it is shown and the device online, and when
   that changes. `adapters.page` does the work."
  (:require
   [adapters.page :as adapter]))


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
  "The page. A page shown again from the back-forward cache gets the keyboard mode it last had."
  []
  (let [overlays (atom false)]
    (js/window.addEventListener "pageshow" (fn [_] (adapter/overlay-keyboard! @overlays)))
    (reify Page
      (active? [_] (adapter/active?))
      (online? [_] (adapter/online?))
      (visible? [_] (adapter/visible?))
      (system-language [_] (adapter/system-language))
      (on-inactive [_ f signal] (adapter/on-inactive f signal))
      (on-offline [_ f] (adapter/on-offline f))
      (on-online [_ f] (adapter/on-online f))
      (on-visibility-change [_ f] (adapter/on-visibility-change f))
      (until-active [_ signal] (adapter/until-active signal))
      (yield [_] (adapter/yield))
      (overlay-keyboard! [_ overlays?]
        (reset! overlays overlays?)
        (adapter/overlay-keyboard! overlays?)))))

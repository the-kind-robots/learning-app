(ns adapters.browser.install-prompt
  "The browser's side of installing the app: whether it runs installed already,
   whether it is on iOS (which has no install prompt of its own), and the
   `beforeinstallprompt` event that holds the prompt back until the learner
   asks for it.")


(defonce ^:private install-prompt (atom nil))


(defn- standalone?
  []
  (or (true? (.-standalone js/navigator))
      (.. js/window (matchMedia "(display-mode: standalone)") -matches)
      (.. js/window (matchMedia "(display-mode: fullscreen)") -matches)))


(defn ios?
  "Whether the device is an iPhone, iPod or iPad."
  []
  (or (boolean (re-find #"(?i)iphone|ipod|ipad" (.-userAgent js/navigator)))
      (and (= "MacIntel" (.-platform js/navigator))
           (> (.-maxTouchPoints js/navigator) 1))))


(defn listen!
  "Unless the app runs installed already, calls `on-available` when installing
   becomes possible (at once on iOS, where the guide is the way, and when the
   browser offers its prompt) and `on-installed` when it has been done."
  [on-available on-installed]
  (when-not (standalone?)
    (when (ios?)
      (on-available))
    (js/window.addEventListener
     "beforeinstallprompt"
     (fn [e]
       (.preventDefault e)
       (reset! install-prompt e)
       (on-available)))
    (js/window.addEventListener
     "appinstalled"
     (fn [_]
       (reset! install-prompt nil)
       (on-installed)))))


(defn prompt!
  "Shows the browser's install prompt, if it offered one."
  []
  (when-let [prompt @install-prompt]
    (.prompt prompt)
    (-> (.-userChoice prompt)
        (.finally #(reset! install-prompt nil)))))

(ns install-guide.core
  (:require
   [nexus.registry :as nxr]))


(defonce ^:private install-prompt (atom nil))


(defn- standalone?
  []
  (or (true? (.-standalone js/navigator))
      (.. js/window (matchMedia "(display-mode: standalone)") -matches)
      (.. js/window (matchMedia "(display-mode: fullscreen)") -matches)))


(defn- ios?
  []
  (or (boolean (re-find #"(?i)iphone|ipod|ipad" (.-userAgent js/navigator)))
      (and (= "MacIntel" (.-platform js/navigator))
           (> (.-maxTouchPoints js/navigator) 1))))


(nxr/register-effect! :effect/pwa-init
  (fn pwa-init [{:keys [dispatch]} _]
    (when-not (standalone?)
      (when (ios?)
        (dispatch [[:effect/save {:pwa/install-available? true}]]))
      (js/window.addEventListener
       "beforeinstallprompt"
       (fn [e]
         (.preventDefault e)
         (reset! install-prompt e)
         (dispatch [[:effect/save {:pwa/install-available? true}]])))
      (js/window.addEventListener
       "appinstalled"
       (fn [_]
         (reset! install-prompt nil)
         (dispatch [[:effect/save {:pwa/install-available? false}]]))))))


(nxr/register-effect! :effect/pwa-trigger-prompt
  (fn pwa-trigger-prompt [_ _]
    (when-let [prompt @install-prompt]
      (.prompt prompt)
      (-> (.-userChoice prompt)
          (.finally #(reset! install-prompt nil))))))


(nxr/register-action! :action/pwa-install-requested
  (fn pwa-install-requested [_]
    (if (ios?)
      [[:effect/save {:pwa/show-guide? true}]]
      [[:effect/pwa-trigger-prompt]])))


(nxr/register-action! :action/dismiss-install-guide
  (fn dismiss-install-guide [_]
    [[:effect/save {:pwa/show-guide? false}]]))


(nxr/register-action! :action/dismiss-install-guide-backdrop
  (fn dismiss-install-guide-backdrop [_ self?]
    (when self?
      [[:effect/save {:pwa/show-guide? false}]])))

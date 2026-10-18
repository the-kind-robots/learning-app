(ns adapters.install.service-worker
  "The worker's registration and how a new build reaches the page (ADR-0022): one build runs at a time, so a
   release build never asks a new one to skip waiting; only the development build mark does. `start!` hands
   the registration back as a promise."
  (:require
   [lambdaisland.glogi :as log]))


(defn- when-install-ends!
  "Calls f once with \"installed\" (the new build waits) or \"redundant\" (the install failed or a
   newer build replaced it); at once when the worker is already there."
  [^js worker f]
  (letfn [(settled []
            (let [state (.-state worker)]
              (when (contains? #{"installed" "redundant"} state)
                state)))
          (on-statechange [_]
            (when-let [state (settled)]
              (.removeEventListener worker "statechange" on-statechange)
              (f state)))]
    (if-let [state (settled)]
      (f state)
      (.addEventListener worker "statechange" on-statechange))))


(defn- check-for-new-build!
  "Asks the browser for the current sw.js and settles either way: a check it could not answer
   (offline) is a debug line, not an error."
  [^js reg]
  (-> (.update reg)
      (.catch (fn [err]
                (log/debug :service-worker/update-failed {:error (str err)})))))


(defn- check-for-new-build-on-return!
  "A PWA coming back from the background makes no navigation, so the check
   the browser ties to navigations never runs; ask on visibility. A build
   found this way installs and waits for every window to close."
  [^js reg]
  (.addEventListener js/document
                     "visibilitychange"
                     (fn [_]
                       (when (= "visible" (.-visibilityState js/document))
                         (check-for-new-build! reg)))))


(defn- register!
  "Registers the worker and answers a promise of the registration. It
   resolves to nil where there is no worker to register or the registration
   failed, and never rejects, so everything downstream reads one shape."
  []
  (if-not (js-in "serviceWorker" js/navigator)
    (js/Promise.resolve nil)
    (-> (.register js/navigator.serviceWorker "/js/app/sw.js" #js {:scope "/"})
        (.then (fn [^js reg]
                 (check-for-new-build-on-return! reg)
                 reg))
        (.catch (fn [err]
                  (log/warn :service-worker/register-failed {:error (str err)})
                  nil)))))


(defn start!
  "Registers the worker and answers a promise of the registration, which
   arrives once and asynchronously (nil where there is no worker). It returns
   at once: the page does not wait on the registration."
  []
  (register!))


(defn get-newest-build!
  "The build mark's tap, given the `registration` promise `start!` answered. It checks for a new build, asks a
   waiting one to serve now and reloads onto it, or reloads plainly; other tabs keep the code they loaded.
   Only a development build has a mark."
  [registration]
  (letfn [(reload! []
            (js/location.reload))
          (take-or-reload! [^js reg]
            (if-let [^js waiting (.-waiting reg)]
              ;; The page reloads once: on activation, on redundancy, or at the timeout. A reload while the
              ;; worker is still activating can hang the navigation (measured, 2 runs of 4).
              (let [reloaded? (atom false)
                    reload-once! (fn []
                                   (when-not @reloaded?
                                     (reset! reloaded? true)
                                     (reload!)))]
                (.addEventListener waiting
                                   "statechange"
                                   #(when (contains? #{"activated" "redundant"} (.-state waiting))
                                      (reload-once!)))
                ;; How long the build mark waits for the worker to activate before it reloads anyway.
                (js/setTimeout reload-once! 3000)
                ;; Makes the receiving worker call skipWaiting; only a development build sends it.
                (.postMessage waiting #js {:type "activate-waiting"}))
              (reload!)))]
    (-> registration
        (.then (fn [^js reg]
                 (if reg
                   (-> (check-for-new-build! reg)
                       (.then (fn [_]
                                ;; `update()` resolves when the new worker starts installing, not when it waits; wait the install out,
                                ;; or the page reloads onto the build it already runs.
                                (if-let [^js installing (.-installing reg)]
                                  (when-install-ends! installing (fn [_] (take-or-reload! reg)))
                                  (take-or-reload! reg)))))
                   (reload!))))
        (.catch (fn [_] (reload!))))))

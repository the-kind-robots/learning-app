(ns service-worker
  "The worker's registration, and how a new build reaches the page
   (ADR-0017).

   One build runs at a time. Two builds may hold different data models, and
   both would write to the same local databases, so a release build never
   asks a new build to skip waiting. A new build installs and waits; the
   browser activates it once every window of the app is closed, and the next
   open runs it. A page opened while another is still open gets the build
   that is already running. Nothing moves at activation, because no page is
   open then, so no page reloads when its controller changes.

   A development build has one more way in: the build mark. A recompile
   would otherwise never reach a tab that stays open, so a tap on the mark
   checks for a new build, asks the waiting one to serve now and reloads
   onto it, or reloads plainly when nothing is new. That path, and the
   message it sends, exist only under goog/DEBUG.

   The registration belongs to the running system, not to this namespace:
   `start!` hands it back as a promise, `:app/render` takes it as a
   dependency, and so every effect finds it under `:service-worker` in the
   system Nexus passes it."
  (:require
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]))


(def ^:private serve-now-request
  "The message that makes the worker receiving it call skipWaiting. Only a
   development build sends it."
  #js {:type "activate-waiting"})


(defn- ask-to-serve-now!
  [^js worker]
  (.postMessage worker serve-now-request))


(defn- when-install-ends!
  "Calls f once with \"installed\" — the new build is now waiting — or with
   \"redundant\" — the install failed or a newer build replaced it. Runs at
   once when the worker is already there, and drops its own listener when it
   fires, which is what makes it once."
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
  "Asks the browser for the current sw.js, and settles either way: a check
   the browser could not answer — offline, most often — is a debug line and
   not an error. What follows is the caller's: the registration afterwards
   says what, if anything, arrived."
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


(defn- registration
  "The registration the system holds, as a promise. Before `register!`
   settles there is nothing to act on, so an effect that fires in that window
   acts when there is; a page with no worker resolves to nil rather than
   leaving the effect a promise that never settles."
  [system]
  (or (some-> system :service-worker :registration)
      (js/Promise.resolve nil)))


(defn start!
  "Registers the worker and hands the rest of the system the one thing it
   needs from it: the registration, which arrives once and asynchronously.
   Depends on nothing, which is what lets it start before render. Returns at
   once: the page does not wait on the registration."
  [_]
  {:registration (register!)})


;; The build mark's tap, and only a development build has a mark: registered
;; under goog/DEBUG so a release bundle carries neither the effect nor the
;; message it sends. A check for a new build first, so one that landed since
;; the page loaded gets to install; then whatever waits is asked to serve now,
;; and the page reloads once it is activated; and a plain reload behind every
;; other outcome — nothing waiting, an install gone redundant, a check the
;; browser refused, no registration at all. The tap ends in a page either way.
;; Other tabs of the origin are not reloaded: they keep the code they loaded
;; until they are reloaded themselves.
(when ^boolean goog/DEBUG
  (nxr/register-effect! :effect/get-newest-build
    (fn get-newest-build [_ system]
      (letfn [(reload! []
                (js/location.reload))
              (take-or-reload! [^js reg]
                (if-let [^js waiting (.-waiting reg)]
                  ;; A reload while the worker is still activating can hang
                  ;; the navigation: measured on #515, it did in 2 runs of 4.
                  (do (.addEventListener waiting
                                         "statechange"
                                         #(when (= "activated" (.-state waiting))
                                            (reload!)))
                      (ask-to-serve-now! waiting))
                  (reload!)))]
        (-> (registration system)
            (.then (fn [^js reg]
                     (if reg
                       (-> (check-for-new-build! reg)
                           (.then (fn [_]
                                    ;; update() resolves once the new worker
                                    ;; has started installing, not once it is
                                    ;; waiting, so the install is waited out
                                    ;; here rather than reloading onto the
                                    ;; build the page already runs.
                                    (if-let [^js installing (.-installing reg)]
                                      (when-install-ends! installing (fn [_] (take-or-reload! reg)))
                                      (take-or-reload! reg)))))
                       (reload!))))
            (.catch (fn [_] (reload!))))))))

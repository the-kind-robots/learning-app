(ns service-worker
  "The worker's registration and what happens when a new build arrives
   (ADR-0014).

   One build serves the page its files. sw.js never skips waiting on its own:
   a build that starts serving deletes the cache bucket of the build before
   it, which open pages are still being served from (#278). So a new build is
   announced instead — it waits, the old one keeps serving, and only when the
   user asks does the new one serve; every page whose serving build changes
   then reloads itself once, which is what makes the deletion safe. The ask is
   always the user's: «Обновить» in the shell, or a tap on the build mark in a
   development build.

   Both controls take the new build the same way: whatever waits is asked to
   serve now, and the announcement comes back down — a control that outlived
   its build would do nothing when tapped. The build mark's tap puts a check
   for a new build in front of that and a plain reload behind it, so the tap
   always ends in a page; it is registered only in a development build, which
   is the only build that draws the mark.

   The registration belongs to the running system, not to this namespace:
   `start!` hands it back as a promise, `:app/render` takes it as a
   dependency, and so every effect finds it under `:service-worker` in the
   system Nexus passes it. The announcement is the other direction — it needs
   `dispatch`, which is render's — so it is a component of its own,
   `announce-new-builds!`, started after both."
  (:require
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]))


(def ^:private serve-now-request
  "The one message sw.js answers: the worker receiving it calls skipWaiting."
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


(defn take-new-build!
  "Asks whatever `reg` has waiting to serve now, and takes the announcement
   down, which the ask has spent either way: a build that was asked for is on
   its way in, and one that is no longer there — an announcement left over
   from a page that never reloaded — would not answer the next tap either.
   Answers whether there was a new build to ask."
  [dispatch ^js reg]
  (let [^js waiting (some-> reg .-waiting)]
    (dispatch [[:effect/save {:pwa/new-build-waiting? false}]])
    (when waiting
      (ask-to-serve-now! waiting))
    (some? waiting)))


(defn- watch-for-arriving-builds!
  "Every build that installs while another one is serving ends up waiting:
   announce it once installed. With nothing serving yet, the first build
   starts serving by itself and there is nothing to announce."
  [announce! ^js reg ^js container]
  (let [watched (volatile! nil)
        watch!  (fn [^js worker]
                  (when (and worker (not (identical? worker @watched)))
                    (vreset! watched worker)
                    (when-install-ends!
                     worker
                     (fn [state]
                       (when (and (= "installed" state) (.-controller container))
                         (announce!))))))]
    (.addEventListener reg "updatefound" (fn [_] (watch! (.-installing reg))))
    (watch! (.-installing reg))))


(defn- reload-when-the-serving-build-changes!
  "A page that loaded with a build serving it reloads once when another build
   takes its place — `controllerchange` is how the browser says so — leaving
   no page needing files the new build has deleted. A page that loaded with
   nothing serving it is only being claimed by the first build: nothing to
   reload, but from then on it counts as served."
  [^js container]
  (let [served?   (volatile! (some? (.-controller container)))
        reloaded? (volatile! false)]
    (.addEventListener container
                       "controllerchange"
                       (fn [_]
                         (if @served?
                           (when-not @reloaded?
                             (vreset! reloaded? true)
                             (js/location.reload))
                           (vreset! served? true))))))


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
   the browser ties to navigations never runs; ask on visibility."
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
    (let [^js container js/navigator.serviceWorker]
      (reload-when-the-serving-build-changes! container)
      (-> (.register container "/js/app/sw.js" #js {:scope "/"})
          (.then (fn [^js reg]
                   (check-for-new-build-on-return! reg)
                   reg))
          (.catch (fn [err]
                    (log/warn :service-worker/register-failed {:error (str err)})
                    nil))))))


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
   Depends on nothing — announcing is somebody else's component — which is
   what lets it start before render. Returns at once: the page does not wait
   on the registration."
  [_]
  {:registration (register!)})


(defn announce-new-builds!
  "Raises the flag «Обновить» reads whenever the worker says a build is
   waiting. Its own component, and started after render, because this is the
   half of the worker's work that needs `dispatch` — and needing dispatch is
   what the worker itself must not do, or render could not depend on it.
   Starting late loses nothing: the registration carries the state rather
   than a callback, so a build that arrived while it was still settling is
   `waiting` or `installing` when this reads it, and every later one arrives
   on `updatefound`."
  [{:keys [render worker]}]
  (let [announce! (fn announce-new-build []
                    ;; A new build is announced, never served unasked — in a
                    ;; development build too: the watch writes one on every
                    ;; recompile, and letting it serve would reload every open
                    ;; page and throw away the hot reload.
                    ((:dispatch render) [[:effect/save {:pwa/new-build-waiting? true}]]))]
    (.then (:registration worker)
           (fn [^js reg]
             (when reg
               (when (.-waiting reg)
                 (announce!))
               (watch-for-arriving-builds! announce! reg js/navigator.serviceWorker)))))
  nil)


(nxr/register-effect! :effect/take-new-build
  (fn take-new-build [{:keys [dispatch]} system]
    (.then (registration system)
           (fn [^js reg]
             (take-new-build! dispatch reg)))))


;; The build mark's tap, and only a development build has a mark: registered
;; under goog/DEBUG so a release bundle carries neither. A check for a new
;; build first, so one that landed since the page loaded gets to install;
;; then the new build is taken the way «Обновить» takes it, and the page
;; reloads when it starts serving; and a plain reload behind every other
;; outcome — nothing waiting, an install gone redundant, a check the browser
;; refused, no registration at all. The tap ends in a page either way.
(when ^boolean goog/DEBUG
  (nxr/register-effect! :effect/get-newest-build
    (fn get-newest-build [{:keys [dispatch]} system]
      (letfn [(reload! []
                (js/location.reload))
              (take-or-reload! [^js reg]
                (when-not (take-new-build! dispatch reg)
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

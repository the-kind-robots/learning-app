(ns service-worker
  "The worker's registration and the update path around it (ADR-0014).

   sw.js never skips waiting on its own: activating deletes the cache bucket
   an open page still loads from (#278). So a new build is offered instead —
   the worker is told to activate on request, and every page that started
   under a controller reloads itself once when the controller changes, which
   is what makes the deletion safe. The request is always the user's:
   «Обновить» in the shell."
  (:require
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]))


(def ^:private activate-message
  "The one message sw.js answers: the worker receiving it calls skipWaiting."
  #js {:type "activate-waiting"})


(defonce ^:private registration
  (atom nil))


(defn- activate!
  [^js worker]
  (.postMessage worker activate-message))


(defn- once-install-settled!
  "Calls f once with \"installed\" — the worker is now waiting — or with
   \"redundant\" — the install failed or a newer worker replaced it. Runs at
   once when the worker is already there."
  [^js worker f]
  (let [settled (fn [state]
                  (when (contains? #{"installed" "redundant"} state)
                    state))
        done?   (volatile! false)
        report! (fn [state]
                  (when-not @done?
                    (vreset! done? true)
                    (f state)))]
    (if-let [state (settled (.-state worker))]
      (report! state)
      (.addEventListener worker
                         "statechange"
                         (fn [_]
                           (some-> (settled (.-state worker)) report!))))))


(defn- offer!
  "A waiting worker is shown, never taken — in a development build too: the
   watch writes a new worker on every recompile, and activating it would
   reload every open page and throw away the hot reload."
  [dispatch]
  (dispatch [[:effect/save {:pwa/update-waiting? true}]]))


(defn- offer-installs!
  "Every worker that starts installing while a controller exists ends up
   waiting: offer it once installed. Without a controller the first worker
   activates by itself and there is nothing to offer."
  [dispatch ^js reg ^js container]
  (let [watched (volatile! nil)
        watch!  (fn [^js worker]
                  (when (and worker (not (identical? worker @watched)))
                    (vreset! watched worker)
                    (once-install-settled!
                     worker
                     (fn [state]
                       (when (and (= "installed" state) (.-controller container))
                         (offer! dispatch))))))]
    (.addEventListener reg "updatefound" (fn [_] (watch! (.-installing reg))))
    (watch! (.-installing reg))))


(defn- reload-on-controller-change!
  "A page that started under a controller reloads once when it changes, so
   no page keeps running on a bucket the new worker deleted. A page that
   started uncontrolled is only being claimed by the first worker: nothing
   to reload, but from then on it counts as controlled."
  [^js container]
  (let [controlled? (volatile! (some? (.-controller container)))
        reloaded?   (volatile! false)]
    (.addEventListener container
                       "controllerchange"
                       (fn [_]
                         (if @controlled?
                           (when-not @reloaded?
                             (vreset! reloaded? true)
                             (js/location.reload))
                           (vreset! controlled? true))))))


(defn- update!
  "Asks the browser for the current sw.js. Rejects offline; that is not an
   error worth more than a debug line."
  [^js reg]
  (-> (.update reg)
      (.catch (fn [err]
                (log/debug :service-worker/update-failed {:error (str err)})))))


(defn- update-on-visible!
  "A PWA coming back from the background makes no navigation, so the update
   check the browser ties to navigations never runs; ask on visibility."
  [^js reg]
  (.addEventListener js/document
                     "visibilitychange"
                     (fn [_]
                       (when (= "visible" (.-visibilityState js/document))
                         (update! reg)))))


(nxr/register-effect! :effect/activate-waiting-worker
  (fn activate-waiting-worker [_ _]
    (when-let [waiting (some-> ^js @registration .-waiting)]
      (activate! waiting))))


(defn start!
  "Registers the worker, offers whatever is or becomes waiting, and keeps
   the page honest about its controller."
  [{:keys [render]}]
  (when (js-in "serviceWorker" js/navigator)
    (let [dispatch      (:dispatch render)
          ^js container js/navigator.serviceWorker]
      (reload-on-controller-change! container)
      (-> (.register container "/js/app/sw.js" #js {:scope "/"})
          (.then (fn [^js reg]
                   (reset! registration reg)
                   (when (.-waiting reg)
                     (offer! dispatch))
                   (offer-installs! dispatch reg container)
                   (update-on-visible! reg)))
          (.catch (fn [err]
                    (log/warn :service-worker/register-failed {:error (str err)})))))))

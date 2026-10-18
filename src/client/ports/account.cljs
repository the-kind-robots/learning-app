(ns ports.account
  "The learner's account, as the app reaches it: whether this device has one, replicating the learner's data with
   it, and the link that joins another device to it. The page decides when it replicates (online, shown). `adapters.account` does the work."
  (:refer-clojure :exclude [exists?])
  (:require
   [adapters.account :as adapter]
   [lambdaisland.glogi :as log]
   [ports.learner :as learner]
   [ports.page :as page]
   [ports.world :as world]))


(defprotocol Account
  (exists?
   [a]
   "Whether this device has an account.")
  (sync!
   [a reason]
   "Asks for a replication pass with the account, if there is one and the device is online.")
  (sync-link
   [a]
   [a nonce]
   "A promise of the link another device opens to join this account, nil without one. With a `nonce` the
     opener echoes it back, so whoever showed the link can tell it joined.")
  (on-device-joined
   [a nonce f]
   "Calls `f` once the device that opened the link carrying `nonce` has joined.")
  (initialized
   [a]
   "A promise that resolves once the learner's memory is loaded and the first replication pass is done, or a
     minute after memory loaded."))


(defn as-account
  "The Account over what the adapter started with (`adapters.account/start!`) and `initialized`, its promise."
  [sync initialized]
  (reify Account
    (exists? [_] (:exists? sync))
    (sync! [_ reason] ((:sync! sync) reason))
    (sync-link [_] ((:sync-link sync)))
    (sync-link [_ nonce] ((:sync-link sync) nonce))
    (on-device-joined [_ nonce f] ((:on-device-joined sync) nonce f))
    (initialized [_] initialized)))


(defn check-incoming-auth!
  "Handles an incoming `#key=` or `#invite=` credential in the address. It runs before the learner's databases
   open: a key from another account wipes the local data, which then opens fresh."
  []
  (adapter/check-incoming-auth! nil))


(def ^:private first-pass-wait-ms
  "How long the first replication pass is waited for once memory is loaded."
  60000)


(defn- loaded
  "A promise that resolves once the learner's memory is loaded, at once if it is."
  [learner]
  (js/Promise.
   (fn [resolve]
     (if (= :status/loaded (learner/status learner))
       (resolve nil)
       (let [stop (volatile! nil)]
         (vreset! stop
                  (learner/on-change learner
                                     (fn []
                                       (when (= :status/loaded (learner/status learner))
                                         (@stop)
                                         (resolve nil))))))))))


;; Plain, not async: an async function would await the promise the clock's `:sleep` returns (docs/dev/cljs-async-pitfalls.md).
(defn initialization
  "The promise `initialized` answers: memory loaded, then the first pass or `first-pass-wait-ms`, whichever
   comes first. The wait's timer is called off however it ends."
  [learner world first-pass]
  (let [waited (js/AbortController.)]
    (-> (loaded learner)
        (.then (fn []
                 (js/Promise.race #js [first-pass
                                       (.then (world/clock world :sleep first-pass-wait-ms (.-signal waited))
                                              (constantly ::timed-out))])))
        (.then (fn [won]
                 (when (= ::timed-out won)
                   (log/info :account/gave-up-waiting-for-sync {:waited-ms first-pass-wait-ms}))
                 (.abort waited)
                 nil)))))


(defn- follow-page!
  "Has the adapter's replication follow the page: no pass while the device is offline, a pass when it comes back,
   and the poke socket open while the page is shown and online."
  [page {:keys [connect-push! disconnect-push! pause! resume! sync!]}]
  (let [connect! (fn []
                   (when (page/active? page)
                     (connect-push!)))]
    (if (page/online? page)
      (resume!)
      (pause!))
    (page/on-online page
                    (fn []
                      (resume!)
                      (sync! nil)
                      ;; A socket that survived a network change may be a zombie; recycling is free, the reconnect pulls anyway.
                      (disconnect-push!)
                      (connect!)))
    (page/on-offline page
                     (fn []
                       (pause!)
                       (disconnect-push!)))
    (page/on-visibility-change page
                               (fn []
                                 (if (page/visible? page)
                                   (connect!)
                                   (disconnect-push!))))
    (connect!)))


(defn ^:async start!
  "Starts the account over the learner's storage (`adapters.account/start!`) and resolves with its Account. The
   adapter replicates when the device has an account; `page` tells it when."
  [{:keys [learner page world]}]
  (let [sync (await (adapter/start! {:after-pull #(learner/resolve-conflicts! learner (:pulled-ids %))
                                     :storage    (learner/storage learner)}))]
    (when (:exists? sync)
      (follow-page! page sync))
    (as-account sync (initialization learner world (:first-pass sync)))))

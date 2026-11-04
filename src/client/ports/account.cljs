(ns ports.account
  "The learner's account, as the app reaches it: whether this device has one, replicating the learner's data with
   it, and the link that joins another device to it. The page decides when it replicates (online, shown). `adapters.account` does the work."
  (:refer-clojure :exclude [exists?])
  (:require
   [adapters.account :as account]
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
  (on-status
    [a f]
    "Calls `f` with the sync's status now and on every change: `:sync/online`, `:sync/offline`, or
     `:sync/no-account` once, without an account.")
  (request-sync!
    [a request]
    "Asks us to turn the sync on for a device without an account. `request` is `{:note :words :days}`: what the
     learner wrote, how many words they have and on how many days they practised. Resolves once it is stored; it
     is sent and followed in the background, and once granted the account opens in place and `on-status` hears
     it. A declined one is forgotten, so the next asks anew.")
  (initialized
    [a]
    "A promise that resolves once the learner's memory is loaded and the first replication pass is done, or a
     minute after memory loaded."))


(defn check-incoming-auth!
  "Handles an incoming `#key=` or `#invite=` credential in the address. It runs before the learner's databases
   open: a key from another account wipes the local data, which then opens fresh."
  []
  (account/check-incoming-auth! nil))


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
  "Tells the account whether the device is online and the page visible, now and on every change."
  [page account]
  (let [tell! #(account/page-changed! account
                                      {:online?  (page/online? page)
                                       :visible? (page/visible? page)})]
    (page/on-online page tell!)
    (page/on-offline page tell!)
    (page/on-visibility-change page tell!)
    (tell!)))


(def ^:private request-check-ms
  "How often a device without an account asks what became of its request, while the page is active."
  60000)


(defn ^:async start!
  "Starts the account over the learner's storage (`adapters.account/start!`) and resolves with its Account. The
   adapter replicates when the device has an account; `page` tells it when. Without one, a stored request is
   followed: the account asks what became of it when the page is active and then every `request-check-ms`. A
   granted one starts the account in place, and `on-status` hears it; a declined one ends the asking until the
   next request."
  [{:keys [learner page world]}]
  (let [account (await (account/start! {:after-pull #(learner/resolve-conflicts! learner (:pulled-ids %))
                                        :storage    (learner/storage learner)}))
        following? (atom false)
        follow-request!
        (fn ^:async follow-request! []
          (when (compare-and-set! following? false true)
            (loop []
              (await (page/until-active page nil))
              (case (await (account/check-request! account))
                :request/granted
                (when (await (account/open! account))
                  (follow-page! page account))

                (nil :request/declined)
                nil

                (do (await (world/clock world :sleep request-check-ms nil))
                    (recur))))
            (reset! following? false)))]
    (if (account/open? account)
      (follow-page! page account)
      ;; Not awaited: it lasts until a stored request is decided, and ends at once without one.
      (follow-request!))
    (reify Account
      (exists? [_] (account/open? account))
      (sync! [_ reason] (account/sync! account reason))
      (sync-link [_] (account/link account))
      (sync-link [_ nonce] (account/link account nonce))
      (on-device-joined [_ nonce f] (account/on-device-joined account nonce f))
      (on-status [_ f] (account/on-status account f))
      ;; Resolves once the request is stored; the following lasts until it is decided, so it is not waited for.
      (request-sync! [_ request]
        (.then (account/save-request! account request)
               (fn []
                 (follow-request!)
                 nil)))
      ;; The first sync since the account opened, so after a granted request the wait is for its own.
      (initialized [_] (initialization learner world (account/first-sync account))))))

(ns adapters.account
  "The learner's account: the identity this device holds, and the replication of the learner's databases with the
   account's, pass by pass, with a push socket that pokes when another device wrote."
  (:require
   [adapters.account.identity :as identity]
   [db :as db]
   [db.pouch :as pouch]
   [goog.functions :as gfn]
   [lambdaisland.glogi :as log]))


(defn- all!
  "One promise for a sequence of promises."
  [promises]
  (js/Promise.all (into-array promises)))


(defn ^:async sync-once!
  "Runs one replication pass and, when it pulled anything, hands its result to `after-pull`, which resolves
   with the conflicts it brought home resolved. Resolves with `{:pulled :pushed :pulled-ids :pulled-revs}`, or
   nil when it failed; it never rejects, so triggers can fire it freely."
  [databases account-id after-pull]
  (when-let [{:keys [pulled] :as result} (await (pouch/sync-once! databases :user/db account-id))]
    (when (pos? pulled)
      (await (after-pull result)))
    result))


(defn stop!
  "Stops pushing on local writes. A pass already in flight finishes on its own."
  [{:keys [unwatch]}]
  (some-> @unwatch (apply [])))


(def ^:private unsent-retry-ms
  "The shortest wait before a request the server did not take is sent again."
  60000)


(def ^:private request-retry-max-ms
  "The longest wait before a request the server did not take is sent again, whatever `Retry-After` asks."
  3600000)


(defn ^:async check-request!
  "Acts on what became of this device's request and resolves with it. `:request/granted` once the device has an
   account, opened here or in another window; a declined one is forgotten, so the next gets a new secret; an
   expired one, or one the server does not have, is sent again (#689), and one it refuses outright is forgotten
   too, while a limit, a mail that did not go or no answer at all holds the next try for as long as
   `Retry-After` asks, between `unsent-retry-ms` and `request-retry-max-ms`; `:request/unanswered` when the
   server could not be asked. Nil without a request. The request is the device's, in device-db, so `_account`
   gives it nothing more."
  [_account]
  (try
    (if (await (identity/load-identity!))
      :request/granted
      (when-let [{:keys [secret] :as request} (await (identity/load-request))]
        (let [status (-> (identity/request-status secret) await (or :request/unanswered))]
          (case status
            :request/granted
            (try
              (await (identity/save-identity! (await (identity/provision! secret))))
              (await (identity/drop-request!))
              (log/info :sync/request-granted {})
              status
              (catch js/Error err
                (if-let [refused (:status (ex-data err))]
                  ;; The invite is spent, perhaps by a try whose answer was lost: only a new request helps.
                  (do (log/warn :sync/request-grant-refused {:status refused})
                      (await (identity/drop-request!))
                      nil)
                  (do (log/warn :sync/request-grant-failed {:error (ex-message err)})
                      :request/unanswered))))

            :request/declined
            (do (await (identity/drop-request!))
                status)

            (:request/expired :request/missing)
            (let [{:keys [retry-after-ms] sent :status} (await (identity/send-request! request))]
              (cond
                (#{200 201} sent)
                nil

                (and sent (<= 400 sent 499) (not= 429 sent))
                (do (log/warn :sync/request-refused {:status sent})
                    (await (identity/drop-request!)))

                :else
                (await (js/Promise.
                        (fn [resolve]
                          (js/setTimeout resolve
                                         (-> (or retry-after-ms unsent-retry-ms)
                                             (max unsent-retry-ms)
                                             (min request-retry-max-ms)))))))
              status)

            status))))
    (catch js/Error err
      (log/warn :sync/request-follow-failed {:error (ex-message err)})
      :request/unanswered)))


(defn ^:async link
  "The link another device opens to join this device's account, or to recover it
   (ADR-0006). With a `nonce` the opener echoes it back, so whoever showed the link can
   tell it joined. Resolves nil without a stored identity; the link is the identity's, so `_account` gives it
   nothing more."
  [_account & [nonce]]
  (when-let [{:keys [token]} (await (identity/load-identity!))]
    (cond-> (str (.. js/window -location -origin) "/#key=" token)
      nonce (str "&pair=" nonce))))


(defn- merged-passes
  "A run of passes told as one: counts summed, revisions united. Failed passes (nil) add
   nothing; a run with none that succeeded is nil."
  [a b]
  (cond
    (nil? a) b
    (nil? b) a
    :else    {:pulled      (+ (:pulled a) (:pulled b))
              :pulled-ids  (merge-with into {} (:pulled-ids a) (:pulled-ids b))
              :pulled-revs (into (:pulled-revs a) (:pulled-revs b))
              :pushed      (+ (:pushed a) (:pushed b))}))


(defn- ^:async joined?
  "True when the proof the device that opened a link wrote for `nonce` has
   arrived with a pull. Checking clears every proof — including strays from
   dialogs abandoned before their echo came home."
  [databases nonce]
  (try
    (let [user-db      (:user/db databases)
          {rows :rows} (await (db/all-docs user-db
                                           {:endkey       "pairing:￰"
                                            :include-docs true
                                            :startkey     "pairing:"}))
          receipts     (map :doc rows)]
      (when (some #(= (str "pairing:" nonce) (:_id %)) receipts)
        (await (all! (map #(db/remove user-db %) receipts)))
        true))
    (catch js/Error err
      (log/warn :sync/pairing-check-failed {:error (ex-message err)})
      false)))


(defn open?
  "Whether this device has an account open."
  [{:keys [user-id]}]
  (some? @user-id))


(defn- local-writes?
  "Whether the change feed reported a write the passes did not pull. What it reported is forgotten."
  [{:keys [pulled written]}]
  (let [[seen _] (reset-vals! written #{})
        theirs   (filter @pulled seen)]
    (swap! pulled #(reduce disj % theirs))
    (< (count theirs) (count seen))))


(defn- run-passes!
  "Runs passes while one is wanted, and resolves with them told as one over `done` (`merged-passes`)."
  [{:keys [after-pull databases passed! passes-wait-for pulled running user-id wanted] :as account} done]
  (if-not @wanted
    (do (reset! running nil)
        (js/Promise.resolve done))
    ;; `wanted` is cleared a microtask later, so requests of the same turn are in this pass.
    (-> (js/Promise.resolve passes-wait-for)
        (.then (fn []
                 (reset! wanted false)
                 (sync-once! databases @user-id after-pull)))
        (.catch (fn [err]
                  (log/warn :sync/pass-failed {:error (ex-message err)})
                  nil))
        (.then (fn [result]
                 (when result
                   (@passed! nil))
                 (swap! pulled into (:pulled-revs result))
                 (when (local-writes? account)
                   (reset! wanted true))
                 (run-passes! account (merged-passes done result)))))))


(defn sync!
  "Syncs now, if the account is open and the device online. Syncs run one at a time: asking during one gets one
   more. Resolves with what they did, told as one; nil when none ran."
  [{:keys [online pulled-hooks running wanted] :as account} _reason]
  (when (and (open? account) @online)
    (reset! wanted true)
    (or @running
        (reset! running
          (.then (run-passes! account nil)
                 (fn [result]
                   (when (pos? (:pulled result 0))
                     (doseq [f @pulled-hooks]
                       (f)))
                   result))))))


(defn first-sync
  "A promise that resolves once the first sync since the account opened has completed; at once without one."
  [account]
  @(:first-pass account))


(defn on-status
  "Calls `f` with the sync's status now and whenever it changes: `:sync/online` or `:sync/offline` while the
   device has an account, `:sync/no-account` without one."
  [{:keys [online user-id] :as account} f]
  (let [told (volatile! nil)
        tell (fn [& _]
               (let [now (cond
                           (not (open? account)) :sync/no-account
                           @online :sync/online
                           :else :sync/offline)]
                 (when (not= now @told)
                   (vreset! told now)
                   (f now))))]
    (add-watch online f tell)
    (add-watch user-id f tell)
    (tell)))


(defn on-device-joined
  "Calls `f` once the device that opened the link carrying `nonce` has joined: the proof it echoes arrives with a
   pull. The hook forgets itself then."
  [{:keys [databases pulled-hooks]} nonce f]
  (let [done (volatile! false)]
    (swap! pulled-hooks
      conj
      (fn hook []
        (-> (joined? databases nonce)
            (.then (fn [joined]
                     (when (and joined (not @done))
                       (vreset! done true)
                       (swap! pulled-hooks (fn [hooks] (filterv #(not (identical? hook %)) hooks)))
                       (f)))))))
    nil))


(defn ^:async save-request!
  "Stores `request` to turn the sync on, unless the account is open: `check-request!` then follows it."
  [account request]
  (when-not (open? account)
    (await (identity/save-request! request))))


(defn- connect-push!
  "Opens the poke WebSocket (ADR-0009) unless it is open. A poke, and every (re)connect, which covers what was
   missed, asks `account` for a pass. A socket the server closed is opened again after a flat 5 s, a poke socket
   being cheap, until `disconnect-push!`."
  [{:keys [push-retry push-socket] :as account}]
  (letfn
    [(on-poke []
       (sync! account nil))
     (url []
       (str (if (= "https:" (.. js/location -protocol)) "wss://" "ws://")
            (.. js/location -host)
            "/api/sync/updates"))
     (connect! []
       (when (nil? @push-socket)
         (let [socket (js/WebSocket. (url))]
           (reset! push-socket socket)
           (set! (.-onopen socket) (fn [_] (on-poke)))
           (set! (.-onmessage socket) (fn [_] (on-poke)))
           (set! (.-onclose socket)
                 (fn [_]
                   (when (identical? socket @push-socket)
                     (reset! push-socket nil)
                     (reset! push-retry (js/setTimeout connect! 5000))))))))]
    (connect!)))


(defn- disconnect-push!
  "Closes the poke WebSocket, and calls off a retry waiting to open it again."
  [{:keys [push-retry push-socket]}]
  (js/clearTimeout @push-retry)
  (reset! push-retry nil)
  (when-some [socket @push-socket]
    (reset! push-socket nil)
    (.close socket)))


(defn page-changed!
  "Tells the account whether the device is online and the page visible. Offline, no sync runs; back online, it
   syncs; the server's pokes are listened to while the page is visible and online."
  [{:keys [online] :as account} {:keys [online? visible?]}]
  (let [back-online? (and online? (not @online))]
    (reset! online online?)
    (when back-online?
      (sync! account nil)
      ;; A socket that survived a network change may be a zombie; recycling is free, the reconnect syncs anyway.
      (disconnect-push! account))
    (if (and online? visible?)
      (connect-push! account)
      (disconnect-push! account))))


(defn ^:async open!
  "Opens the account this device holds, unless it is open or the device holds none: from here passes run, local
   writes ask for them, and the status says so. Resolves with whether the device has an account open."
  [{:keys [databases first-pass passed! running unwatch user-id written] :as account}]
  (try
    (when-not (open? account)
      (when-let [{:keys [id] :as device-identity} (await (identity/load-identity!))]
        (identity/use-identity! device-identity)
        (reset! first-pass (js/Promise. (fn [resolve] (reset! passed! resolve))))
        ;; A pass's end judges the feed, because only then it knows what it pulled. A burst of writes (a lesson's
        ;; reviews) goes out in a few passes, not one per write.
        (let [push! (gfn/throttle #(when (and (nil? @running) (local-writes? account))
                                     (sync! account nil))
                                  3000)]
          (reset! unwatch
            (pouch/on-change databases
                             :user/db
                             (fn [change]
                               (swap! written conj (pouch/change-revision change))
                               (push!)))))
        (reset! user-id id)
        (log/info :sync/ready {:user-id id})))
    (catch js/Error err
      (log/warn :sync/start-failed {:error (ex-message err)})))
  (open? account))


(defn ^:async start!
  "The learner's account over their `storage`, open if the device holds one. What it can do, each a function
   here that takes it: open (`open!`, `open?`), sync (`sync!`), follow the page (`page-changed!`), tell about
   itself (`on-status`, `first-sync`), join another device (`link`, `on-device-joined`) and ask to turn the
   sync on (`save-request!`, `check-request!`). `after-pull` resolves the conflicts a sync brought home."
  [{:keys [after-pull storage]}]
  (let [{:keys [databases passes-wait-for]} storage
        account {:after-pull      after-pull
                 :databases       databases
                 :first-pass      (atom (js/Promise.resolve nil))
                 :online          (atom true)
                 :passed!         (atom (fn [_]))
                 :passes-wait-for passes-wait-for
                 ;; `[id rev]` the passes pulled / the change feed reported.
                 :pulled          (atom #{})
                 :pulled-hooks    (atom [])
                 ;; The poke socket (ADR-0009), and the retry waiting to open it again.
                 :push-retry      (atom nil)
                 :push-socket     (atom nil)
                 :running         (atom nil)
                 :unwatch         (atom nil)
                 :user-id         (atom nil)
                 :wanted          (atom false)
                 :written         (atom #{})}]
    (when-not (await (open! account))
      (log/info :sync/local-only {}))
    account))


(defn ^:async check-incoming-auth!
  "Handles an incoming `#key=` or `#invite=` credential (ADR-0006). A key destroys local data only when it
   belongs to another account; runs before the databases open, so a wipe leaves a fresh instance."
  [_]
  (let [;; Credentials arrive in the fragment rather than in the query string
        ;; The fragment is never sent to the server, so the credential stays out of access logs and Referer headers.
        params (js/URLSearchParams. (.. js/window -location -hash (slice 1)))
        token  (.get params "key")
        invite (.get params "invite")]
    (cond
      (some? token)
      (try
        (if-let [account-id (await (identity/account-id! token))]
          (do
            (when-let [stored (await (identity/load-identity!))]
              ;; Wiped through a throwaway handle before the databases open: a handle that existed at
              ;; destroy time hangs on every later call.
              (when (not= (:id stored) account-id)
                (await (db/destroy (db/use "user-db")))))
            (await (identity/save-identity! {:id account-id :token token}))
            ;; The echo that ends pairing (ADR-0009): the QR carried a nonce; the receipt carries it back
            ;; through replication and closes the dialog of the device that minted it.
            (when-some [nonce (.get params "pair")]
              (await (db/insert (db/use "user-db")
                                {:_id  (str "pairing:" nonce)
                                 :type "pairing"})))
            (log/info :sync/adopted {:user-id account-id}))
          (log/warn :sync/invalid-key {}))
        (catch js/Error err
          (log/warn :sync/adopt-failed {:error (ex-message err)})))

      (some? invite)
      (try
        (if (await (identity/load-identity!))
          (log/warn :sync/already-provisioned {})
          (let [provisioned (await (identity/provision! invite))]
            (await (identity/save-identity! provisioned))
            (log/info :sync/provisioned {:user-id (:id provisioned)})))
        (catch js/Error err
          (log/warn :sync/provision-failed {:error (ex-message err)}))))))


(defn open-recovery-link!
  "Opens a pasted recovery link as if it had been followed: its fragment goes into the address and the page
   starts again, so `check-incoming-auth!` adopts the key on boot. Answers false, changing nothing, for a text
   that is not a link with a `#key=`."
  [text]
  (let [fragment (try
                   (.-hash (js/URL. text))
                   (catch js/Error _ ""))]
    (if (.has (js/URLSearchParams. (subs fragment 1)) "key")
      (do
        (js/history.replaceState nil "" (str js/location.pathname fragment))
        (js/location.reload)
        true)
      false)))

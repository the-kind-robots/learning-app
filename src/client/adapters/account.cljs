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
  (when unwatch
    (unwatch)))


(def no-account
  "What `start!` returns for a device without an account. Such a device runs
   no pass, so its sync is inert, there is no first pass to wait for, and no
   device can join."
  {:connect-push! (constantly nil)
   :disconnect-push! (constantly nil)
   :exists?       false
   :first-pass    (js/Promise.resolve nil)
   :on-device-joined (fn [_ _] nil)
   :pause!        (constantly nil)
   :resume!       (constantly nil)
   :sync!         (constantly nil)
   :sync-link     (fn [& _] (js/Promise.resolve nil))})


(defn- ^:async sync-link
  "The link another device opens to join this device's account, or to recover it
   (ADR-0006). With a `nonce` the opener echoes it back, so whoever showed the link can
   tell it joined. Resolves nil without a stored identity."
  [& [nonce]]
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


(defonce ^:private push-socket (atom nil))


(defonce ^:private push-retry (atom nil))


(defn connect-push!
  "Opens the poke WebSocket (ADR-0009) unless it is open. `on-poke` runs on a poke and on every (re)connect,
   which covers what was missed. A socket the server closed is opened again after a flat 5 s, a poke socket
   being cheap, until `disconnect-push!`."
  [on-poke]
  (letfn [(url []
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


(defn disconnect-push!
  "Closes the poke WebSocket, and calls off a retry waiting to open it again."
  []
  (js/clearTimeout @push-retry)
  (reset! push-retry nil)
  (when-some [socket @push-socket]
    (reset! push-socket nil)
    (.close socket)))


(defn ^:async start!
  "Drives replication when the device has an account: requests during a pass share one more pass, and what
   a pass pulled is not a local write (ADR-0006). `storage` is the learner's, opaque but for the
   `:databases` to replicate and `:passes-wait-for`, a promise that holds every pass until the snapshot check is
   done (ADR-0018); `after-pull` takes the result of a pass that pulled and resolves once the conflicts it brought
   are resolved. Resolves with the verbs of the account: `:sync!`, `:pause!` and `:resume!` (no pass while the
   device is offline), `:connect-push!` and `:disconnect-push!`, `:sync-link`, `:on-device-joined`, `:exists?`,
   and `:first-pass`, which resolves when the first pass of this start has completed."
  [{:keys [after-pull storage]}]
  (try
    (if-let [{:keys [id] :as device-identity} (await (identity/load-identity!))]
      (do
        (identity/use-identity! device-identity)
        (let [{:keys [databases passes-wait-for]} storage
              online (atom true)
              wanted (atom false)
              running (atom nil)
              pulled-hooks (atom [])
              on-pulled
              (fn on-pulled
                [f]
                (swap! pulled-hooks conj f)
                (fn unsubscribe
                  []
                  (swap! pulled-hooks (fn [fs] (filterv #(not (identical? f %)) fs)))))
              passed! (atom nil)
              first-pass (js/Promise. (fn [resolve] (reset! passed! resolve)))
              ;; `[id rev]` the passes pulled / the change feed reported.
              pulled (atom #{})
              written (atom #{})
              local-writes?
              (fn []
                (let [[seen _] (reset-vals! written #{})
                      theirs   (filter @pulled seen)]
                  (swap! pulled #(reduce disj % theirs))
                  (< (count theirs) (count seen))))
              run!
              (fn run!
                [done]
                (if-not @wanted
                  (do (reset! running nil)
                      (js/Promise.resolve done))
                  ;; `wanted` is cleared a microtask later, so requests of the same turn are in this pass.
                  (-> (js/Promise.resolve passes-wait-for)
                      (.then (fn []
                               (reset! wanted false)
                               (sync-once! databases id after-pull)))
                      (.catch (fn [err]
                                (log/warn :sync/pass-failed {:error (ex-message err)})
                                nil))
                      (.then (fn [result]
                               (when result
                                 (@passed! nil))
                               (swap! pulled into (:pulled-revs result))
                               (when (local-writes?)
                                 (reset! wanted true))
                               (run! (merged-passes done result)))))))
              request!
              (fn [& _]
                (when @online
                  (reset! wanted true)
                  (or @running
                      (reset! running
                        (.then (run! nil)
                               (fn [result]
                                 (when (pos? (:pulled result 0))
                                   (doseq [f @pulled-hooks]
                                     (f)))
                                 result))))))
              ;; A pass's end judges the feed, because only then it knows what it pulled.
              push! (gfn/throttle #(when (and (nil? @running) (local-writes?))
                                     (request!))
                                  ;; A burst of writes (a lesson's reviews) goes out in a few passes, not one per write.
                                  3000)
              unwatch (pouch/on-change databases
                                       :user/db
                                       (fn [change]
                                         (swap! written conj (pouch/change-revision change))
                                         (push!)))]
          (log/info :sync/ready {:user-id id})
          {:exists?          true
           ;; `f` is called once the device that opened the link carrying `nonce` has joined; the proof
           ;; it echoes arrives with a pull. The hook forgets itself then.
           :on-device-joined (fn on-device-joined
                               [nonce f]
                               (let [done        (volatile! false)
                                     unsubscribe (volatile! nil)]
                                 (vreset! unsubscribe
                                          (on-pulled (fn []
                                                       (-> (joined? databases nonce)
                                                           (.then (fn [joined]
                                                                    (when (and joined (not @done))
                                                                      (vreset! done true)
                                                                      (@unsubscribe)
                                                                      (f))))))))
                                 nil))
           :sync-link        sync-link
           :sync!            request!
           ;; Push channel (ADR-0009): a poke pulls through the normal path.
           :connect-push!    #(connect-push! request!)
           :disconnect-push! disconnect-push!
           :pause!           #(reset! online false)
           :resume!          #(reset! online true)
           :first-pass       first-pass
           :unwatch          unwatch}))
      (do
        (log/info :sync/local-only {})
        no-account))
    (catch js/Error err
      (log/warn :sync/start-failed {:error (ex-message err)})
      no-account)))


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

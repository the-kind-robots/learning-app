(ns adapters.learner.sync
  (:require
   [adapters.learner.documents :as documents]
   [adapters.learner.identity :as identity]
   [clojure.string :as str]
   [db :as db]
   [db.pouch :as pouch]
   [domain.collections :as collections]
   [domain.vocabulary :as domain]
   [goog.functions :as gfn]
   [lambdaisland.glogi :as log]))


(defn- all!
  "One promise for a sequence of promises."
  [promises]
  (js/Promise.all (into-array promises)))


(defn- ^:async resolve-conflict!
  "Writes the merged word (`domain.vocabulary/merged-word`) of a document's conflicting
   revisions and deletes the losers, so the conflict is gone everywhere."
  [user-db doc]
  ;; One revision at a time on purpose: `bulkGet` is built to save HTTP round trips and, against a
  ;; local database, measures slower than the plain reads (IndexedDB, 200 revisions).
  (let [conflicting (await (all! (map #(db/get user-db (:_id doc) {:rev %})
                                      (:_conflicts doc))))
        revisions   (cons doc conflicting)
        merged      (domain/merged-word revisions)
        losers      (remove #(= (:_rev %) (:_rev merged)) revisions)]
    (await (db/insert user-db merged))
    (await (all! (map #(db/remove user-db %) losers)))))


(defn ^:async resolve-vocab-conflicts!
  "Resolves every conflicted vocabulary doc a replication pass left behind."
  [user-db]
  (try
    (let [{rows :rows} (await (db/all-docs user-db
                                       ;; Vocabulary ids share a prefix (ADR-0008), so the entries are a key range, not a scan of the
                                       ;; database; phrases live in it too. `:conflicts` is the point of the query, and only `all-docs`,
                                       ;; `get` and `changes` honour it: `find` silently answers without conflicts.
                                       {:conflicts    true
                                        :endkey       (str domain/id-prefix "￰")
                                        :include-docs true
                                        :startkey     domain/id-prefix}))
          conflicted   (->> rows
                            (map :doc)
                            (filter #(-> % :_conflicts seq)))]
      (when (seq conflicted)
        (log/info :sync/resolving-conflicts {:count (count conflicted)}))
      (await (all! (map #(resolve-conflict! user-db %) conflicted))))
    (catch js/Error err
      (log/warn :sync/conflict-resolution-failed {:error (ex-message err)}))))


(defn- revision-order
  "The revision of a stored `version` as a comparable: its generation, then
   its hash."
  [version]
  (let [[generation hash] (str/split (:_rev version) #"-" 2)]
    [(js/parseInt generation 10) hash]))


(defn- ^:async resolve-collection!
  "When a pull left a collection with several live versions, writes one that
   holds them all and removes the others."
  [databases id]
  (let [live (->> (await (pouch/doc-versions databases documents/collection-schema id))
                  (remove :_deleted))]
    (when (next live)
      (let [collection (reduce (partial collections/merged-collection revision-order) live)
            losers     (remove #(= (:_rev collection) (:_rev %)) live)
            docs       (conj (mapv documents/tombstone losers) collection)
            written    (await (pouch/bulk-docs databases documents/collection-schema docs))]
        (when (< (count written) (count docs))
          (log/warn :sync/conflict-resolution-failed
                    {:id id :refused (- (count docs) (count written))}))))))


(defn ^:async resolve-collection-conflicts!
  "Resolves the collections `ids` that have more than one live version."
  [databases ids]
  (try
    (await (all! (map #(resolve-collection! databases %) ids)))
    (catch js/Error err
      (log/warn :sync/conflict-resolution-failed {:error (ex-message err)}))))


(defn ^:async sync-once!
  "Runs one replication pass and resolves the conflicts it brought home. Resolves with
   `{:pulled :pushed :pulled-ids :pulled-revs}`, or nil when it failed; it never rejects,
   so triggers can fire it freely."
  [databases account-id]
  (when-let [{:keys [pulled pulled-ids] :as result} (await (pouch/sync-once! databases :user/db account-id))]
    (when (pos? pulled)
      (await (resolve-vocab-conflicts! (:user/db databases)))
      (await (resolve-collection-conflicts! databases (pulled-ids (:type documents/collection-schema)))))
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
  {:exists?          false
   :sync-link        (fn [& _] (js/Promise.resolve nil))
   :on-device-joined (fn [_ _] nil)
   :sync!            (constantly nil)
   :first-pass               (js/Promise.resolve nil)})


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


(defn connect-push!
  "Holds a poke WebSocket while the page is visible and online (ADR-0009). `on-poke` runs on
   a poke and on every (re)connect, which covers what was missed. Hidden pages close it, so the
   radio sleeps; offline pages resume on `online`."
  [browser on-poke]
  (letfn
    [(url []
       (str (if (= "https:" (.. js/location -protocol)) "wss://" "ws://")
            (.. js/location -host)
            "/api/sync/updates"))
     (connect! []
       (when (and (nil? @push-socket)
                  ((:active? browser)))
         (let [socket (js/WebSocket. (url))]
           (reset! push-socket socket)
           (set! (.-onopen socket) (fn [_] (on-poke)))
           (set! (.-onmessage socket) (fn [_] (on-poke)))
           (set! (.-onclose socket)
                 (fn [_]
                   (when (identical? socket @push-socket)
                     (reset! push-socket nil)
                     ;; Not a deliberate close: retry while visible. The backoff is flat; a poke socket is cheap.
                     (js/setTimeout connect! 5000)))))))
     (disconnect! []
       (when-some [socket @push-socket]
         (reset! push-socket nil)
         (.close socket)))]
    ((:on-visibility-change browser)
     (fn []
       (if ((:visible? browser))
         (connect!)
         (disconnect!))))
    ;; A socket that survived a network change may be a zombie; recycling is free, the reconnect pulls anyway.
    ((:on-online browser)
     (fn []
       (disconnect!)
       (connect!)))
    ((:on-offline browser) disconnect!)
    (connect!)))


(defn ^:async start!
  "Drives replication when the device has an account: requests during a pass share one more pass, and what
   a pass pulled is not a local write (ADR-0006). `passes-wait-for` holds every pass until the snapshot
   check is done (ADR-0018); `:first-pass` resolves when the first pass of this start has completed.
   `browser` is the page as plain functions: `:active?`, `:online?`, `:visible?`, `:on-offline`, `:on-online`,
   `:on-visibility-change`."
  [{:keys [browser databases passes-wait-for]}]
  (try
    (if-let [{:keys [id] :as device-identity} (await (identity/load-identity!))]
      (do
        (identity/use-identity! device-identity)
        (let [wanted (atom false)
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
                               (sync-once! databases id)))
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
                (when ((:online? browser))
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
          ((:on-online browser) request!)
          ;; Push channel (ADR-0009): a poke pulls through the normal path.
          (connect-push! browser request!)
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
           :first-pass               first-pass
           :unwatch                  unwatch}))
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

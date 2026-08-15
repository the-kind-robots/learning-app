(ns sync
  (:require
   [adapters.identity :as identity]
   [db :as db]
   [db.pouch :as pouch]
   [domain.vocabulary :as domain]
   [goog.functions :as gfn]
   [lambdaisland.glogi :as log]))


(defn- lww-winner
  "Returns the doc with the lexicographically higher :modified-at, or b if equal."
  [a b]
  (if (pos? (compare (:modified-at a) (:modified-at b)))
    a
    b))


(defn- all!
  "One promise for a sequence of promises."
  [promises]
  (js/Promise.all (into-array promises)))


(defn- ^:async resolve-conflict!
  "Collapses one document's conflicting revisions into a single one. Scalar
   fields come from the last write; translations are unioned across every
   revision, so the same word added on two devices keeps both meanings. The
   revisions that lost are deleted, which is what makes the conflict gone
   rather than merely resolved on this device."
  [user-db doc]
  ;; A revision at a time, on purpose. The scan hands over the revision it
  ;; serves and the ids of the ones it hides, never their contents, so they have
  ;; to be read. `bulkGet` would read them in one call, but it is built to save
  ;; HTTP round trips during replication: against a local database it loops over
  ;; the same reads and assembles a grouped answer on top, and measures slower
  ;; than the reads alone — on IndexedDB some 30 ms against 20 for 200 revisions.
  (let [conflicting (await (all! (map #(db/get user-db (:_id doc) {:rev %})
                                      (:_conflicts doc))))
        revisions   (cons doc conflicting)
        winner      (reduce lww-winner revisions)
        translation (reduce domain/merge-translations [] (map :translation revisions))
        losers      (remove #(= (:_rev %) (:_rev winner)) revisions)]
    (await (db/insert user-db (assoc winner :translation translation)))
    (await (all! (map #(db/remove user-db %) losers)))))


(def ^:private vocabulary-with-conflicts
  "Vocabulary ids are content-addressed under a `vocab:` prefix (ADR-0008), so
   the entries can be read as a key range rather than by scanning every review,
   collection and example in the database. `￰` sorts past anything an id
   can carry, which makes the range the whole prefix. Phrases live in this
   range too — they are vocabulary documents with a `:kind`.

   `:conflicts` is what this whole query is for, and only `all-docs`, `get` and
   `changes` honour it — `find` accepts the option and silently answers without
   the conflicts, which would look like a database that never conflicts."
  {:conflicts    true
   :endkey       "vocab:￰"
   :include-docs true
   :startkey     "vocab:"})


(defn ^:async resolve-vocab-conflicts!
  "Resolves every conflicted vocabulary doc a replication pass left behind."
  [user-db]
  (try
    (let [{rows :rows} (await (db/all-docs user-db vocabulary-with-conflicts))
          conflicted   (->> rows
                            (map :doc)
                            (filter #(-> % :_conflicts seq)))]
      (when (seq conflicted)
        (log/info :sync/resolving-conflicts {:count (count conflicted)}))
      (await (all! (map #(resolve-conflict! user-db %) conflicted))))
    (catch js/Error err
      (log/warn :sync/conflict-resolution-failed {:error (ex-message err)}))))


(defn ^:async sync-once!
  "Runs one replication pass and resolves the conflicts it may have brought
   home. Never rejects, so callers can fire it freely on triggers."
  [dbs account-id]
  (when (await (pouch/sync-once! dbs :user/db account-id))
    (await (resolve-vocab-conflicts! (:user/db dbs)))))


(defn stop!
  "Stops pushing on local writes. A pass already in flight finishes on its own."
  [{:sync/keys [unwatch]}]
  (when unwatch
    (unwatch)))


(def ^:private push-interval-ms
  "Throttle window for write-driven pushes, so a burst of writes (a lesson's
   reviews) coalesces into periodic passes. Placeholder; tune via battery todo."
  3000)


(defn- ^:async pairing-confirmed!
  "True when the receipt a newly paired device wrote for `nonce` has arrived
   with a pull. Confirmation clears every receipt — including strays from
   dialogs abandoned before their echo came home."
  [dbs nonce]
  (try
    (let [user-db      (:user/db dbs)
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


(defn ^:async start!
  "Drives replication by triggers when the device has an account: a throttled
   push on every local user-db change, plus a pull returned as :sync/pull!
   for the UI to call on data-page entry. Without a stored identity the device
   is local-only — no network call, inert pull (ADR-0006)."
  [{dbs :db}]
  (try
    (if-let [{:keys [id] :as identity} (await (identity/load-identity!))]
      (do
        (identity/use-identity! identity)
        (let [pull!   #(when (.-onLine js/navigator) (sync-once! dbs id))
              unwatch (pouch/on-change dbs :user/db (gfn/throttle pull! push-interval-ms))]
          (log/info :sync/ready {:user-id id})
          {:sync/account-id id
           :sync/pairing-confirmed! #(pairing-confirmed! dbs %)
           :sync/pull!      pull!
           :sync/unwatch    unwatch}))
      (do
        (log/info :sync/local-only {})
        {:sync/account-id nil
         :sync/pull!      (constantly nil)}))
    (catch js/Error err
      (log/warn :sync/start-failed {:error (ex-message err)})
      {:sync/account-id nil
       :sync/pull!      (constantly nil)})))


(defonce ^:private push-socket (atom nil))


(defn connect-push!
  "Holds a poke WebSocket while the page is visible and the network is up
   (ADR-0009). A poke means \"your data changed somewhere\" and carries
   nothing else; `on-poke` runs then, and also on every (re)connect — one
   pull covers whatever was missed while the socket was down. Hidden pages
   close the socket: the radio sleeps when the user is elsewhere. Offline
   pages don't retry: reconnection resumes on the `online` event."
  [on-poke]
  (letfn
    [(url []
       (str (if (= "https:" (.. js/location -protocol)) "wss://" "ws://")
            (.. js/location -host)
            "/api/sync/updates"))
     (connect! []
       (when (and (nil? @push-socket)
                  (.-onLine js/navigator)
                  (= "visible" (.-visibilityState js/document)))
         (let [socket (js/WebSocket. (url))]
           (reset! push-socket socket)
           (set! (.-onopen socket) (fn [_] (on-poke)))
           (set! (.-onmessage socket) (fn [_] (on-poke)))
           (set! (.-onclose socket)
                 (fn [_]
                   (when (identical? socket @push-socket)
                     (reset! push-socket nil)
                     ;; Not a deliberate close: try again soon while
                     ;; visible. The backoff is flat — a poke socket is
                     ;; cheap and reconnect already pulls.
                     (js/setTimeout connect! 5000)))))))
     (disconnect! []
       (when-some [socket @push-socket]
         (reset! push-socket nil)
         (.close socket)))]
    (.addEventListener js/document
                       "visibilitychange"
                       (fn [_]
                         (if (= "visible" (.-visibilityState js/document))
                           (connect!)
                           (disconnect!))))
    ;; A socket that survived a network change is not trusted — it may be a
    ;; zombie holding a dead connection. Recycling is free: the reconnect
    ;; pulls anyway.
    (.addEventListener js/window
                       "online"
                       (fn [_]
                         (disconnect!)
                         (connect!)))
    (.addEventListener js/window "offline" (fn [_] (disconnect!)))
    (connect!)))


(defn- fragment-params
  "The URL fragment as params. Credentials arrive there rather than in the
   query string because a fragment is never part of the request: a query lands
   in the server's access log and in Referer headers, a fragment does not."
  []
  (js/URLSearchParams. (.. js/window -location -hash (slice 1))))


(defn ^:async check-incoming-auth!
  "Handles an incoming credential: #key=TOKEN (QR pair, recovery, share) or
   #invite=TOKEN (provision a new account, ADR-0006).

   A key is validated against the server before this device adopts it, and the
   local user-db is destroyed only when the key turns out to belong to a
   different account than the one stored — otherwise the local data stays and
   merges. Must run before :db/pouch starts, so that a wipe leaves the fresh
   PouchDB instance empty. The cookie is not written here: `start!` derives it
   from the stored identity later in the same boot.

   Returns nil — side effects only. The router drops the fragment when it
   redirects the unmatched `/` to the home page."
  [_]
  (let [params (fragment-params)
        token  (.get params "key")
        invite (.get params "invite")]
    (cond
      (some? token)
      (try
        (if-let [account-id (await (identity/account-id! token))]
          (do
            (when-let [stored (await (identity/load-identity!))]
              ;; Wiped through a throwaway handle, before :db/pouch opens its
              ;; own: a handle that existed at destroy time never errors, it
              ;; hangs on every later call.
              (when (not= (:id stored) account-id)
                (await (db/destroy (db/use "user-db")))))
            (await (identity/save-identity! {:id account-id :token token}))
            ;; The echo that ends pairing (ADR-0009): the QR carried a nonce,
            ;; the receipt carries it back through ordinary replication, and
            ;; the device that minted it closes its dialog and deletes the
            ;; receipt once the pull delivers it.
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
          (let [identity (await (identity/provision! invite))]
            (await (identity/save-identity! identity))
            (log/info :sync/provisioned {:user-id (:id identity)})))
        (catch js/Error err
          (log/warn :sync/provision-failed {:error (ex-message err)}))))))

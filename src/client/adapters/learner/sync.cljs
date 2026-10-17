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
  "Collapses one document's conflicting revisions into the merged word
   (`domain.vocabulary/merged-word`), written over the revision it took its
   fields from. The revisions that lost are deleted, which is what makes the
   conflict gone rather than merely resolved on this device."
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
        merged      (domain/merged-word revisions)
        losers      (remove #(= (:_rev %) (:_rev merged)) revisions)]
    (await (db/insert user-db merged))
    (await (all! (map #(db/remove user-db %) losers)))))


(defn ^:async resolve-vocab-conflicts!
  "Resolves every conflicted vocabulary doc a replication pass left behind."
  [user-db]
  (try
    (let [{rows :rows} (await (db/all-docs user-db
                                       ;; Vocabulary ids are content-addressed under one prefix
                                       ;; (ADR-0008), which the domain owns and this asks for, so
                                       ;; the entries can be read as a key range rather than by
                                       ;; scanning every review, collection and example in the
                                       ;; database. `￰` sorts past anything an id can carry, which
                                       ;; makes the range the whole prefix. Phrases live in this
                                       ;; range too — they are vocabulary documents with a `:kind`.
                                       ;;
                                       ;; `:conflicts` is what this whole query is for, and only
                                       ;; `all-docs`, `get` and `changes` honour it — `find` accepts
                                       ;; the option and silently answers without the conflicts,
                                       ;; which would look like a database that never conflicts.
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
  "Runs one replication pass and resolves the conflicts it may have brought
   home. Resolves with what the pass did, `{:pulled n :pushed n :pulled-ids
   {type #{id}} :pulled-revs #{...}}`, or nil when it failed; never rejects,
   so callers can fire it freely on triggers.
   A pass that pulled nothing brought no conflict home, so nothing is
   resolved and — for the caller — nothing has changed."
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
  {:first-pass             (js/Promise.resolve nil)
   :has-account?           false
   :on-device-joined       (fn [_ _] nil)
   :stop-waiting-for-device (fn [_] nil)
   :sync!                  (constantly nil)
   :sync-link              (fn [& _] (js/Promise.resolve nil))})


(defn- ^:async sync-link
  "The link another device opens to join the sync of this device's account:
   as a QR code, as a link sent on, or to recover the account. The token
   rides in the fragment, which browsers never send to the server, keeping it
   out of access logs and Referer headers (ADR-0006). With a `nonce`, the
   device that opens the link also echoes it back, so that whoever showed the
   link can tell it joined (`joined?`). Resolves nil when there is no stored
   identity."
  [& [nonce]]
  (when-let [{:keys [token]} (await (identity/load-identity!))]
    (cond-> (str (.. js/window -location -origin) "/#key=" token)
      nonce (str "&pair=" nonce))))


(defn- merged-passes
  "What a run of passes did, told as one pass: counts summed, revisions
   united. A caller asks whether anything was pulled, and a run whose
   first pass pulled and whose follow-up did not has pulled. Failed passes
   (nil) add nothing; a run with none that succeeded is nil."
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
  "Holds a poke WebSocket while the page is visible and the network is up
   (ADR-0009). A poke means \"your data changed somewhere\" and carries
   nothing else; `on-poke` runs then, and also on every (re)connect — one
   pull covers whatever was missed while the socket was down. Hidden pages
   close the socket: the radio sleeps when the user is elsewhere. Offline
   pages don't retry: reconnection resumes on the `online` event."
  [browser on-poke]
  (letfn
    [(url []
       (str (if (= "https:" (.. js/location -protocol)) "wss://" "ws://")
            (.. js/location -host)
            "/api/sync/updates"))
     (connect! []
       (when (and (nil? @push-socket)
                  ((:browser/active? browser)))
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
    ((:browser/on-visibility-change browser)
     (fn []
       (if ((:browser/visible? browser))
         (connect!)
         (disconnect!))))
    ;; A socket that survived a network change is not trusted — it may be a
    ;; zombie holding a dead connection. Recycling is free: the reconnect
    ;; pulls anyway.
    ((:browser/on-online browser)
     (fn []
       (disconnect!)
       (connect!)))
    ((:browser/on-offline browser) disconnect!)
    (connect!)))


(defn ^:async start!
  "Drives replication by requests when the device has an account, returned as
   :sync! for route entry, pokes and `online`; local user-db writes request
   one too, throttled. Without a stored identity the device is local-only — no
   network call, inert pull (ADR-0006).

   One pass at a time (#319). A request while none runs starts one; requests
   during a pass ask for one more after it, however many arrive, so none is
   lost and none overlaps. Every requester gets the same promise, which
   resolves when the passes stop, with what they did together.

   A change the pass itself pulled is not a local write. The change feed
   records every revision it sees; after each pass, and when the throttle
   fires with no pass running, those the passes pulled are struck off, and
   only what remains asks for a pass. A conflict resolution writes new
   revisions, so it does ask — that pass is what pushes it.

   `:first-pass` is a promise that resolves, with nil, once the first
   pass of this start has completed; a failed pass does not count. A pass of
   an earlier start does not count either, so a sync started again for
   another account is not taken to have replicated anything yet.

   `passes-wait-for`, a promise or nil, holds back every pass of this tab
   until it resolves. The start of the learner's memory checks its snapshot
   against the databases; a pass before that check could bring back the
   change at the snapshot's position after the database lost others below
   it, and the check would take a snapshot holding documents the database
   no longer has (ADR-0018)."
  [{:keys [browser databases passes-wait-for]}]
  (try
    (if-let [{:keys [id] :as device-identity} (await (identity/load-identity!))]
      (do
        (identity/use-identity! device-identity)
        (let [wanted (atom false)
              running (atom nil)
              ;; Called after a run of passes that pulled documents.
              pulled-hooks (atom [])
              ;; The pulled hook of each nonce a device is awaited for.
              waiting (atom {})
              stop-waiting
              (fn stop-waiting
                [nonce]
                (when-let [unsubscribe (get @waiting nonce)]
                  (unsubscribe)
                  (swap! waiting dissoc nonce))
                nil)
              on-pulled
              (fn on-pulled
                [f]
                (swap! pulled-hooks conj f)
                (fn unsubscribe
                  []
                  (swap! pulled-hooks (fn [fs] (filterv #(not (identical? f %)) fs)))))
              ;; Resolves the first pass of this start once one completes.
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
                  ;; `wanted` is cleared a microtask later, as the pass
                  ;; starts, so requests made in the same turn as the one
                  ;; that started the run are in this pass, not after it.
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
                (when ((:browser/online? browser))
                  (reset! wanted true)
                  (or @running
                      (reset! running
                              (.then (run! nil)
                                     (fn [result]
                                       (when (pos? (:pulled result 0))
                                         (doseq [f @pulled-hooks]
                                           (f)))
                                       result))))))
              ;; While a pass runs, its end judges the feed: it knows then
              ;; what it pulled.
              push! (gfn/throttle #(when (and (nil? @running) (local-writes?))
                                     (request!))
                                  ;; Throttle window for write-driven requests, so a burst of
                                  ;; writes (a lesson's reviews) goes out in a few passes rather
                                  ;; than one per write. Placeholder; tune via battery todo.
                                  3000)
              unwatch (pouch/on-change databases
                                       :user/db
                                       (fn [change]
                                         (swap! written conj (pouch/change-revision change))
                                         (push!)))]
          (log/info :sync/ready {:user-id id})
          ;; A reconnect after offline flushes what was written while offline.
          ((:browser/on-online browser) request!)
          ;; Push channel (ADR-0009): a poke pulls through the normal path.
          (connect-push! browser request!)
          {:first-pass              first-pass
           :has-account?            true
           ;; `f` is called once the device that opened this device's sync
           ;; link carrying `nonce` has joined the account: the proof it
           ;; echoes arrives with a pull. The wait ends then, or when
           ;; `stop-waiting-for-device` is asked for the same nonce.
           :on-device-joined        (fn on-device-joined
                               [nonce f]
                               (let [done (volatile! false)]
                                 (swap! waiting
                                        assoc
                                        nonce
                                        (on-pulled (fn []
                                                     (-> (joined? databases nonce)
                                                         (.then (fn [joined]
                                                                  (when (and joined (not @done))
                                                                    (vreset! done true)
                                                                    (stop-waiting nonce)
                                                                    (f))))))))
                                 nil))
           :stop-waiting-for-device stop-waiting
           :sync!                   request!
           :sync-link               sync-link
           :unwatch                 unwatch}))
      (do
        (log/info :sync/local-only {})
        no-account))
    (catch js/Error err
      (log/warn :sync/start-failed {:error (ex-message err)})
      no-account)))


(defn ^:async check-incoming-auth!
  "Handles an incoming credential: #key=TOKEN (QR pair, recovery, share) or
   #invite=TOKEN (provision a new account, ADR-0006).

   A key is validated against the server before this device adopts it, and the
   local data is cleared only when the key turns out to belong to a different
   account than the one stored — otherwise the local data stays and merges.
   Clearing is user-db destroyed. Must run before the databases open, so that
   a wipe leaves the fresh PouchDB instance empty. The cookie is not written here: `start!` derives it
   from the stored identity later in the same boot.

   Returns nil — side effects only. The router drops the fragment when it
   redirects the unmatched `/` to the home page."
  [_]
  (let [;; Credentials arrive in the fragment rather than in the query string
        ;; because a fragment is never part of the request: a query lands in the
        ;; server's access log and in Referer headers, a fragment does not.
        params (js/URLSearchParams. (.. js/window -location -hash (slice 1)))
        token  (.get params "key")
        invite (.get params "invite")]
    (cond
      (some? token)
      (try
        (if-let [account-id (await (identity/account-id! token))]
          (do
            (when-let [stored (await (identity/load-identity!))]
              ;; Wiped through a throwaway handle, before the databases open their
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
          (let [provisioned (await (identity/provision! invite))]
            (await (identity/save-identity! provisioned))
            (log/info :sync/provisioned {:user-id (:id provisioned)})))
        (catch js/Error err
          (log/warn :sync/provision-failed {:error (ex-message err)}))))))

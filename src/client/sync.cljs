(ns sync
  (:require
   [adapters.identity :as identity-api]
   [db :as db]
   [domain.vocabulary :as domain]
   [goog.functions :as gfn]
   [lambdaisland.glogi :as log]))


(def ^:private identity-doc-id "identity:local")


(defn- device-db [] (db/use "device-db"))


(defn ^:async load-identity!
  "Returns {:id :secret} from device-db, or nil if not stored."
  []
  (when-let [doc (await (db/get (device-db) identity-doc-id))]
    {:id     (:user-id doc)
     :secret (:secret doc)}))


(defn ^:async save-identity!
  "Persists {:id :secret} to device-db, updating _rev if the doc already exists."
  [{:keys [id secret]}]
  (let [existing (await (db/get (device-db) identity-doc-id))
        doc      (cond-> {:_id     identity-doc-id
                          :type    "identity"
                          :user-id id
                          :secret  secret}
                   existing (assoc :_rev (:_rev existing)))]
    (await (db/insert (device-db) doc))))


(defn ^:async ensure-identity!
  "Returns stored identity, or claims a new one from the server and stores it."
  []
  (or (await (load-identity!))
      (let [identity (await (identity-api/claim!))]
        (await (save-identity! identity))
        identity)))


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
   the words can be read as a key range rather than by scanning every review,
   collection and example in the database. `￰` sorts past anything an id
   can carry, which makes the range the whole prefix.

   `:conflicts` is what this whole query is for, and only `all-docs`, `get` and
   `changes` honour it — `find` accepts the option and silently answers without
   the conflicts, which would look like a database that never conflicts."
  {:conflicts    true
   :endkey       "vocab:￰"
   :include-docs true
   :startkey     "vocab:"})


(defn ^:async resolve-vocab-conflicts!
  "Resolves every conflicted vocab doc a replication pass left behind."
  [user-db]
  (try
    (let [{rows :rows} (await (db/all-docs user-db vocabulary-with-conflicts))
          conflicted   (->> rows
                            (map :doc)
                            (filter #(and (-> % :type #{"vocab"}) (-> % :_conflicts seq))))]
      (when (seq conflicted)
        (log/info :sync/resolving-conflicts {:count (count conflicted)}))
      (await (all! (map #(resolve-conflict! user-db %) conflicted))))
    (catch js/Error err
      (log/warn :sync/conflict-resolution-failed {:error (ex-message err)}))))


(defn ^:async sync-once!
  "Runs one bidirectional replication pass, resolves vocab conflicts, and
   completes when the pass finishes. Never rejects — a failed pass logs and
   resolves nil so callers can fire it freely on triggers."
  [user-db remote-url]
  (js/Promise.
   (fn [resolve _reject]
     (doto (db/sync user-db {:live false :remote-url remote-url})
       (.on "complete"
            (fn [_]
              (resolve (resolve-vocab-conflicts! user-db))))
       (.on "error"
            (fn [err]
              (log/warn :sync/pass-failed {:error (str err)})
              (resolve nil)))))))


(def ^:private push-interval-ms
  "Throttle window for write-driven pushes, so a burst of writes (a lesson's
   reviews) coalesces into periodic passes. Placeholder; tune via battery todo."
  3000)


(defn ^:async start!
  "Ensures identity and authenticates, then drives replication by triggers:
   a throttled push on every local user-db change, plus a pull returned as
   :sync/pull! for the UI to call on data-page entry. No permanent live feed."
  [{:keys [db]}]
  (try
    (let [{:keys [id secret]} (await (ensure-identity!))]
      (await (identity-api/auth! {:id id :secret secret}))
      (let [user-db    (:user/db db)
            remote-url (str (.. js/globalThis -location -origin) "/db/userdb-" id)
            pull!      #(when (.-onLine js/navigator) (sync-once! user-db remote-url))]
        (..
         ^js user-db
         (changes #js {:since "now" :live true})
         (on "change" (gfn/throttle pull! push-interval-ms)))
        (log/info :sync/ready {:user-id id})
        {:sync/pull! pull!}))
    (catch js/Error err
      (log/warn :sync/start-failed {:error (ex-message err)})
      {:sync/pull! (constantly nil)})))


(defn ^:async check-incoming-auth!
  "Checks URL for ?recover=TOKEN (burn-on-use) or ?id=ID&secret=SECRET (QR pair).
   If found: saves identity, destroys user-db IndexedDB, clears URL params.
   Must run before :db/pouch starts so the fresh PouchDB instance is empty.
   Returns nil in all cases — called for side effects only."
  [_]
  (let [params  (js/URLSearchParams. (.. js/window -location -search))
        recover (.get params "recover")
        pair-id (.get params "id")
        secret  (.get params "secret")]
    (cond
      (some? recover)
      (try
        (let [identity (await (identity-api/redeem! recover))]
          (await (save-identity! identity))
          (await (db/destroy (db/use "user-db")))
          (js/history.replaceState nil "" "/")
          (log/info :sync/redeemed {:user-id (:id identity)}))
        (catch js/Error err
          (log/warn :sync/redeem-failed {:error (ex-message err)})
          (js/history.replaceState nil "" "/")))

      (and (some? pair-id) (some? secret))
      (try
        (let [identity {:id (js/parseInt pair-id 10) :secret secret}]
          (await (identity-api/auth! identity))
          (await (save-identity! identity))
          (await (db/destroy (db/use "user-db")))
          (js/history.replaceState nil "" "/")
          (log/info :sync/paired {:user-id (:id identity)}))
        (catch js/Error err
          (log/warn :sync/pair-failed {:error (ex-message err)})
          (js/history.replaceState nil "" "/"))))))

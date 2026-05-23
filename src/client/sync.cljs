(ns sync
  (:require
   [adapters.identity :as identity-api]
   [db :as db]
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
  "Returns the doc with the lexicographically higher :modified-at, or a if equal."
  [a b]
  (if (pos? (compare (:modified-at a) (:modified-at b)))
    a
    b))


(defn ^:async resolve-vocab-conflicts!
  "Finds conflicted vocab docs and resolves them via LWW on :modified-at."
  [user-db]
  (try
    (let [{rows :rows} (await (db/all-docs user-db {:include-docs true :conflicts true}))
          conflicted   (filter (fn [{:keys [doc]}]
                                 (and (= "vocab" (:type doc))
                                      (seq (:_conflicts doc))))
                               rows)]
      (when (seq conflicted)
        (log/info :sync/resolving-conflicts {:count (count conflicted)}))
      (await
       (js/Promise.all
        (into-array
         (map (fn [{:keys [doc]}]
                ((fn ^:async f []
                   (let [conflict-revs (:_conflicts doc)
                         candidates    (await
                                        (js/Promise.all
                                         (into-array
                                          (map #(db/get user-db (:_id doc) {:rev %})
                                               conflict-revs))))
                         winner        (reduce lww-winner doc (vec candidates))
                         loser-docs    (remove #(= (:_rev %) (:_rev winner))
                                               (cons doc (vec candidates)))]
                     (await
                      (js/Promise.all
                       (into-array (map #(db/remove user-db %) loser-docs))))))))
              conflicted)))))
    (catch js/Error err
      (log/warn :sync/conflict-resolution-failed {:error (ex-message err)}))))


(defn ^:async start!
  "Ensures identity, authenticates, starts live PouchDB↔CouchDB replication.
   Returns the PouchDB sync object (cancel it via stop!)."
  [{:keys [db]}]
  (try
    (let [{:keys [id secret]} (await (ensure-identity!))]
      (await (identity-api/auth! {:id id :secret secret}))
      (let [user-db  (:user/db db)
            origin   (.. js/globalThis -location -origin)
            sync-obj (db/sync user-db
                              {:live       true
                               :retry      true
                               :remote-url (str origin "/db/userdb-" id)})]
        (log/info :sync/started {:user-id id})
        (.on sync-obj "paused" (fn [_] (resolve-vocab-conflicts! user-db)))
        sync-obj))
    (catch js/Error err
      (log/warn :sync/start-failed {:error (ex-message err)})
      nil)))


(defn stop!
  "Cancels an active sync object."
  [sync-obj]
  (when sync-obj
    (try (.cancel sync-obj) (catch :default _ nil))))


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

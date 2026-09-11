(ns db.pouch
  (:refer-clojure :exclude [get find remove])
  (:require
   [clojure.string :as str]
   [db :as db]
   [db-migrations :as db-migrations]
   [lambdaisland.glogi :as log]
   [userdb :as userdb]))


(defn- user-db
  []
  (db/use "user-db"))


(defn- device-db
  []
  (db/use "device-db"))


(def doc-type->db
  "Maps PouchDB :type string to the dbs-map key for its owning database."
  {"collection" :user/db
   "example"    :device/db
   "lesson"     :device/db
   "review"     :user/db
   "task"       :device/db
   "vocab"      :user/db})


(defn db-for
  "Returns the database instance for a given doc type string."
  [dbs doc-type]
  (some-> doc-type doc-type->db dbs))


(def ^:private db->remote-name
  "Which databases have a copy on the server, and what that copy is called
   there. device-db is absent on purpose: it holds what belongs to this device
   alone and has nowhere to replicate to."
  {:user/db userdb/db-name})


(defn on-change
  "Calls f after every local write to db-key. Returns a function that stops
   watching."
  [dbs db-key f]
  (let [feed (.changes ^js (db-key dbs) #js {:live true :since "now"})]
    (.on ^js feed "change" f)
    #(.cancel ^js feed)))


(defn user-doc?
  "Replication filter: design documents stay on the device. CouchDB builds
   an index for every design document it receives, and the server never
   queries user-db through one."
  [doc]
  (not (str/starts-with? (.-_id ^js doc) "_design/")))


(defn sync-once!
  "Runs one bidirectional replication pass of db-key against the account's copy
   on the server. Resolves when the pass finishes and never rejects — a failed
   pass resolves nil — so a caller can fire it on a trigger without guarding
   every one."
  [dbs db-key account-id]
  (let [remote (str (.. js/globalThis -location -origin)
                    "/db/"
                    ((db->remote-name db-key) account-id))]
    (js/Promise.
     (fn [resolve _reject]
       (doto (db/sync (db-key dbs) {:filter user-doc? :live false :remote-url remote})
         (.on "complete" (fn [_] (resolve true)))
         (.on "error"
              (fn [err]
                (log/warn :db/sync-failed {:db db-key :error (str err)})
                (resolve nil))))))))


(defn insert
  "Inserts doc into the database determined by its :type field."
  [dbs doc]
  (db/insert (db-for dbs (:type doc)) doc))


(defn bulk-docs
  "Atomically writes multiple docs to the database that owns `doc-type`.
   All docs must belong to that same database — caller groups by db."
  [dbs doc-type docs]
  (db/bulk-docs (db-for dbs doc-type) docs))


(defn get
  "Fetches a document by id from the database that owns the given type string."
  [dbs type-str doc-id]
  (db/get (db-for dbs type-str) doc-id))


(defn remove
  "Removes doc from the database determined by its :type field."
  [dbs doc]
  (db/remove (db-for dbs (:type doc)) doc))


(defn find
  "Queries the database determined by [:selector :type] in the query."
  [dbs query]
  (db/find (db-for dbs (get-in query [:selector :type])) query))


(defn find-all
  "Queries (with auto-pagination) the database determined by [:selector :type] in the query."
  [dbs query]
  (db/find-all (db-for dbs (get-in query [:selector :type])) query))


(def ^:private user-db-indexes
  "Every user-db query selects on :type, and reviews are looked up by
   :word-id. Without these pouchdb-find scans the whole database and filters
   in JS on the main thread (#404). Design-document ids sort `by-type` before
   `by-type-word-id`, which is the tie-break pouchdb-find uses for a plain
   {:type ..} selector."
  [{:fields [:type] :name "by-type"}
   {:fields [:type :word-id] :name "by-type-word-id"}])


(defn- ^:async ensure-index!
  "Idempotent, so running it on every start is what gives an installation
   that predates the index its copy. A failure is logged, never raised: a
   missing index slows queries down, it does not break them."
  [db {:keys [fields] index-name :name}]
  (try
    (await (db/create-index db fields {:name index-name :ddoc index-name}))
    (catch :default err
      (log/error :db/index-error {:index index-name :error (str err)}))))


(def ^:private reviews-by-word-view
  "One row per review, keyed by word id, valued `[created_at retained]` —
   what retention needs, without fetching the review documents. The view is
   named without a dash because `clj->couch` snake-cases map keys on the way
   in."
  "reviews-by-word/reviews")


(def ^:private vocab-preview-view
  "One row per vocabulary document, keyed by id, valued
   `[kind value translation]` — what a list of words shows, without fetching
   the documents."
  "vocab-preview/preview")


(defn ^:async reviews-by-word
  "Reviews as `{:word-id :created-at :retained}`, grouped by word id. With
   `word-ids` only those words are read, by key; nil reads the whole view,
   which is the cheaper of the two when every word is wanted anyway
   (#404: 800 keys 0.8 s, all rows 1.1 s, 1500 keys 1.5 s)."
  [dbs word-ids]
  (let [{rows :rows} (await (db/query (:user/db dbs)
                                      reviews-by-word-view
                                      (cond-> {} word-ids (assoc :keys (vec word-ids)))))]
    (->> rows
         (map (fn [{word-id :key [created-at retained] :value}]
                {:created-at created-at
                 :retained   retained
                 :word-id    word-id}))
         (group-by :word-id))))


(defn ^:async vocab-previews
  "Every word and phrase as `{:_id :kind :value :translation}` — what a list
   shows — or only `word-ids` when given. Read from the vocab view, so no
   document is fetched."
  [dbs word-ids]
  (let [{rows :rows} (await (db/query (:user/db dbs) vocab-preview-view {}))
        wanted-ids   (some-> word-ids set)]
    (->> rows
         (filter (fn [{id :id}]
                   (or (nil? wanted-ids) (contains? wanted-ids id))))
         (mapv (fn [{id :id [kind value translation] :value}]
                 {:_id         id
                  :kind        kind
                  :translation translation
                  :value       value})))))


(def ^:private user-db-design-docs
  [{:_id   "_design/reviews-by-word"
    :views {:reviews
            {:map
             "function (doc) { if (doc.type === 'review') emit(doc.word_id, [doc.created_at, doc.retained]); }"}}}
   {:_id   "_design/vocab-preview"
    :views {:preview
            {:map
             "function (doc) { if (doc.type === 'vocab') emit(doc._id, [doc.kind, doc.value, doc.translation]); }"}}}])


(defn- ^:async ensure-design-doc!
  "Writes the design document when it is missing or its views differ from
   `ddoc`, so a changed map function replaces the stored one and an unchanged
   one costs one read. Failure is logged, never raised."
  [db {id :_id :as ddoc}]
  (try
    (let [stored (await (db/get db id))]
      (when (not= (:views stored) (:views ddoc))
        (await (db/insert db (cond-> ddoc stored (assoc :_rev (:_rev stored)))))))
    (catch :default err
      (log/error :db/design-doc-error {:ddoc id :error (str err)}))))


(defn prepare-user-db!
  "Indexes and views user-db carries; idempotent, run on every start."
  [db]
  (js/Promise.all (into-array (concat (map #(ensure-index! db %) user-db-indexes)
                                      (map #(ensure-design-doc! db %) user-db-design-docs)))))


(defn ^:async init!
  [_deps]
  (await (db-migrations/ensure-migrated!))
  (let [user-db (user-db)]
    (await (prepare-user-db! user-db))
    {:device/db (device-db)
     :user/db   user-db}))

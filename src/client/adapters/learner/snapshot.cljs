(ns adapters.learner.snapshot
  "A snapshot of memory, kept as one entry of the Cache API, so that a start
   does not read every document (ADR-0018). The snapshot is a cache:
   PouchDB stays the source, and a snapshot that fails a check is deleted.

   The snapshot is one string: a header line, then the body. Both are
   transit. The body is what memory took from each document
   (`adapters.learner.memory/entries`). The header is

     {:version   format-version
      :checksum  checksum of the body
      :databases {db-key {:marker   database marker
                          :position {:id :rev :seq}}}}

   The positions are those of the memory the body was taken from
   (`adapters.learner.memory/positions`). A snapshot read from the cache is
   kept as `{:header :body}`: the header read once, the body as text until
   it is decoded."
  (:require
   [adapters.learner.memory :as memory]
   [clojure.string :as str]
   [cognitect.transit :as transit]))


(def format-version
  "The version of what the body holds. It changes whenever what memory
   takes from a document changes: an entry's keys, or a conversion in
   `adapters.learner.documents`, or a document memory could not take and
   now can. A snapshot of another version is dropped, and the start reads
   every document once; a snapshot is never migrated. The test
   `client.memory-snapshot-test/the-snapshot-format-is-the-one-its-version-names`
   fails when the format changes."
  1)


(def ^:private cache-name
  "The Cache API cache that holds the snapshot."
  "learner-memory")


(def ^:private snapshot-key
  "The key the snapshot is stored under in its cache. No request goes to
   this path."
  "/learner-memory/snapshot")


(defn- checksum
  "The 32-bit FNV-1a hash of the UTF-16 code units of `text`."
  [^string text]
  (let [n (.-length text)]
    (loop [i    0
           hash 0x811c9dc5]
      (if (< i n)
        (recur (inc i) (js/Math.imul (bit-xor hash (.charCodeAt text i)) 0x01000193))
        (unsigned-bit-shift-right hash 0)))))


(defn parsed
  "The snapshot `text` as `{:header :body}`: its header read, its body as
   text."
  [text]
  (let [newline (str/index-of text "\n")]
    {:body   (subs text (inc newline))
     :header (transit/read (transit/reader :json) (subs text 0 newline))}))


(defn joined
  "The text of the snapshot `{:header :body}`; the inverse of `parsed`."
  [{:keys [body header]}]
  (str (transit/write (transit/writer :json) header) "\n" body))


(defn encode
  "The snapshot of `memory`, as text. `markers` is `{db-key marker}`, the
   marker of each database memory was read from."
  [memory markers]
  (let [body (transit/write (transit/writer :json) (memory/entries memory))]
    (joined {:body   body
             :header {:checksum  (checksum body)
                      :databases (into {}
                                       (map (fn [[db-key position]]
                                              [db-key {:marker (markers db-key) :position position}]))
                                       (memory/positions memory))
                      :version   format-version}})))


(defn positions
  "The feed positions the snapshot's `header` stored, `{db-key {:id :rev
   :seq}}`."
  [header]
  (update-vals (:databases header) :position))


(defn refusal
  "Why the snapshot with `header` cannot start memory from the databases as
   they stand, or nil when it can. `at` is where each database's change
   feed stands now (`db.pouch/feed-position`), `marked` the marker of each
   database, and `held` whether each database still holds the change at
   the stored position (`db.pouch/holds-position?`). The reasons are:

   - `:version` — the snapshot is of another format version;
   - `:marker` — a database is not the one the snapshot was taken from;
   - `:position` — a database's feed stands behind the stored position, as
     when the database lost its last writes;
   - `:change` — a database no longer holds the change at the stored
     position, as when it lost its last writes and stored others under
     their sequences.

   The body is checked when it is decoded (`decoded`)."
  [{:keys [databases version]} at marked held]
  (cond
    (not= format-version version)
    :version

    (not-every? (fn [[db-key marker]] (= marker (get-in databases [db-key :marker]))) marked)
    :marker

    (not-every? (fn [[db-key position]]
                  (some-> (get-in databases [db-key :position :seq]) (<= (:seq position))))
                at)
    :position

    (not-every? true? (vals held))
    :change))


(defn decoded
  "The entries of memory the snapshot `{:header :body}` holds. It throws,
   with `{:reason :checksum}` as data, when the body does not match the
   header's checksum."
  [{:keys [body header]}]
  (when (not= (:checksum header) (checksum body))
    (throw (ex-info "The snapshot does not match its checksum." {:reason :checksum})))
  (transit/read (transit/reader :json) body))


(defn- cache-api?
  "Whether this context has the Cache API. A page served over plain http,
   other than from localhost, and Node have none; there the app keeps no
   snapshot."
  []
  (exists? js/caches))


(defn ^:async read!
  "The stored snapshot as `{:header :body}` (`parsed`), or nil when there
   is none."
  []
  (when (cache-api?)
    (let [cache    (await (.open js/caches cache-name))
          response (await (.match cache snapshot-key))]
      (when response
        (parsed (await (.text response)))))))


(defn ^:async write!
  "Stores the snapshot of `memory` (`encode`) in place of the one stored.
   The Cache API replaces an entry whole: a write that does not finish
   leaves the old one."
  [memory markers]
  (when (cache-api?)
    (let [cache (await (.open js/caches cache-name))]
      (await (.put cache snapshot-key (js/Response. (encode memory markers)))))))


(defn ^:async delete!
  "Deletes the snapshot."
  []
  (when (cache-api?)
    (let [cache (await (.open js/caches cache-name))]
      (await (.delete cache snapshot-key)))))

(ns adapters.learner.snapshot
  "A snapshot of memory, kept as one entry of the Cache API, so that a start
   does not read every document (ADR-0018, ADR-0023). The snapshot is a cache:
   PouchDB stays the source, and a snapshot that fails a check is deleted.

   The snapshot is one string: a header line, then the body. Both are
   transit. The body is memory itself, as `adapters.learner.memory/stored`
   gives it. The header is

     {:version  format-version
      :checksum checksum of the body
      :db/id    user-db's id
      :position {:id :rev :seq}}

   The position is that of the memory the body was taken from
   (`adapters.learner.memory/position`). A snapshot read from the cache is
   kept as `{:header :body}`: the header read once, the body as text until
   it is decoded."
  (:require
   [adapters.learner.memory :as memory]
   [clojure.string :as str]
   [cognitect.transit :as transit]
   [domain.retention :as retention]))


(def format-version
  "The version of what the body holds. It changes whenever the shape of
   memory changes, or what memory takes from a document: a conversion in
   `adapters.learner.documents`, or a document memory could not take and
   now can. A snapshot of another version is dropped, and the start reads
   every document once; a snapshot is never migrated. The test
   `client.memory-snapshot-test/a-snapshot-of-this-format-version-holds-exactly-what-memory-takes`
   fails when the format changes.

   Version 4: a word's `:search` also holds its spelling without umlauts
   (#578). Version 3: the body is memory itself (ADR-0023). Version 2 held
   the entries memory took from each document."
  4)


(def ^:private cache-name
  "The Cache API cache that holds the snapshot."
  "learner-memory")


(def ^:private snapshot-key
  "The key the snapshot is stored under in its cache. No request goes to
   this path."
  "/learner-memory/snapshot")


(def ^:private body-writer
  "The transit writer of the body: a review history is three columns. The columns go as one JSON string, which transit skips whole and `JSON.parse` reads natively."
  (transit/writer :json {:handlers {retention/Reviews
                                    (transit/write-handler
                                     (constantly "reviews")
                                     (fn [{:keys [ids retained seconds]}]
                                       (js/JSON.stringify #js [(js/Array.from seconds)
                                                               (.join (js/Array.from retained) "")
                                                               ids])))}}))


(def ^:private body-reader
  "The transit reader of the body."
  (transit/reader :json {:handlers {"reviews" (fn [text]
                                                (let [columns (js/JSON.parse text)]
                                                  (retention/->Reviews (js/Float64Array.from (aget columns 0))
                                                                       (js/Uint8Array.from (.split ^string (aget columns 1) ""))
                                                                       (aget columns 2))))}}))


(defn- checksum
  "The 32-bit FNV-1a hash of the UTF-16 code units of `text`."
  [^string text]
  (let [n (.-length text)]
    (loop [i    0
           ;; int32 from the start, so V8 keeps the loop in int32 registers; same hash.
           hash (bit-or 0x811c9dc5 0)]
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
  "The snapshot of `memory`, as text. `db-id` is the id of user-db, which
   memory was read from."
  [memory db-id]
  (let [body (transit/write body-writer (memory/stored memory))]
    (joined {:body   body
             :header {:checksum (checksum body)
                      :db/id    db-id
                      :position (memory/position memory)
                      :version  format-version}})))


(defn decoded
  "The memory the snapshot `{:header :body}` holds, as `memory/stored`
   returned it. It throws, with `{:reason :checksum}` as data, when the
   body does not match the header's checksum."
  [{:keys [body header]}]
  (when (not= (:checksum header) (checksum body))
    (throw (ex-info "The snapshot does not match its checksum." {:reason :checksum})))
  (transit/read body-reader body))


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
  [memory db-id]
  (when (cache-api?)
    (let [cache (await (.open js/caches cache-name))]
      (await (.put cache snapshot-key (js/Response. (encode memory db-id)))))))


(defn ^:async delete!
  "Deletes the snapshot."
  []
  (when (cache-api?)
    (let [cache (await (.open js/caches cache-name))]
      (await (.delete cache snapshot-key)))))

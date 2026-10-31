(ns core
  (:gen-class)
  (:require
   [auth :as auth]
   [cheshire.core :as cheshire]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [db :as db]
   [examples :as examples]
   [examples.dictionary :as dictionary]
   [grants :as grants]
   [hiccup :as hiccup]
   [i18n :as i18n]
   [invite-requests :as invite-requests]
   [migrations :as migrations]
   [muuntaja.core :as muuntaja]
   [next.jdbc :as jdbc]
   [next.jdbc.prepare :as prepare]
   [next.jdbc.result-set :as result-set]
   [org.httpkit.server :as server]
   [push :as push]
   [reconciliation :as reconciliation]
   [reitit.http :as http]
   [reitit.http.interceptors.exception :as exception]
   [reitit.http.interceptors.keyword-parameters :as keyword-parameters]
   [reitit.http.interceptors.muuntaja :as format]
   [reitit.http.interceptors.parameters :as parameters]
   [reitit.interceptor.sieppari :as sieppari]
   [reitit.ring :as ring]
   [ring.middleware.cookies :as cookies]
   [ring.util.response :as response]
   [taoensso.telemere :as t]
   [userdb :as userdb]
   [utils :as utils])
  (:import
   [java.security MessageDigest]
   [java.sql PreparedStatement ResultSetMetaData]
   [java.util HexFormat]
   [javax.crypto Mac]
   [javax.crypto.spec SecretKeySpec]))


(set! *warn-on-reflection* true)


(def port (or (some-> (System/getenv "LEARNING_APP__PORT") Integer/parseInt) 8083))


(def ^:private console-log-handler-id :learning-app/console)


(defn- ensure-log-handler!
  []
  (when-not (seq (t/get-handlers))
    (t/add-handler! console-log-handler-id (t/handler:console) {:async nil})))


(def running-from-source?
  "Whether this process runs from a checkout rather than from the packaged jar.
   The jar carries compiled classes and resources but no `.clj`, so finding our
   own source on the classpath answers the question — unlike an environment
   variable, which is a promise about the environment that nothing enforces."
  (some? (io/resource "core.clj")))


(defn- configured-db-auth-secret
  [env from-source?]
  ;; GH-217 phase 2a: the unit still exports the old single-underscore name,
  ;; and unit (deb) and reader (jar) ship in different artifacts — so the jar
  ;; reads both, preferring the new name. The old-name fallback is transition
  ;; scaffolding: remove it in phase 2c, once the deb's rename (phase 2b) is
  ;; deployed. #217 tracks the removal.
  (or (get env "LEARNING_APP__DB_AUTH_SECRET")
      (get env "LEARNING_APP_DB_AUTH_SECRET")
      (when from-source? "secret")
      (throw (ex-info "LEARNING_APP__DB_AUTH_SECRET is required outside a source checkout" {}))))


(def db-auth-secret
  "Signs the proxy-auth headers `/auth/check` hands to CouchDB. A checkout may
   fall back to a well-known value; a packaged app may not, and says so instead
   of signing with something an attacker can guess."
  (configured-db-auth-secret (System/getenv) running-from-source?))


(defn- require-couchdb-password!
  "The dev fallback in lib/db's `conn` serves exactly one audience: a source
   checkout talking to the dev stand's CouchDB. For the packaged app the
   fallback is never right — its CouchDB has a real password — but with wrong
   credentials the server still boots and looks alive while provisioning,
   reconciliation and push silently 401. So the jar demands the variable up
   front and dies loudly instead (#246)."
  [env from-source?]
  (when-not (or (get env "LEARNING_APP__COUCHDB_PASSWORD") from-source?)
    (throw (ex-info "LEARNING_APP__COUCHDB_PASSWORD is required outside a source checkout" {}))))


;;
;; DB
;;


(def db-spec
  {:dbname (or (System/getenv "LEARNING_APP__DB_PATH") "app.db")
   :dbtype "sqlite"})


(defn- adopt-database!
  "One-time move of the database from its pre-#201 home into the configured
   path. Runs before anything opens the database, so no writer exists.

   Crash-safe by construction: the copy lands under a temp name and the final
   rename is atomic, so the legacy file stays authoritative until the target
   fully exists — a crash mid-copy just retries next boot. When both files
   exist the target wins and the legacy is left in place with a loud log:
   rolling back a deploy must never cost newer data, and disposal of the
   leftover is the operator's decision."
  [^java.io.File legacy {:keys [dbname]}]
  (let [target (io/file dbname)]
    ;; systemd-tmpfiles pre-creates the target as an empty file (#225), and an
    ;; empty file is not data: adopting over it loses nothing, refusing over
    ;; it strands the accounts. Only a non-empty target earns the refusal.
    (when (and (.exists target) (zero? (.length target)))
      (.delete target))
    (when (and (not= (.getCanonicalPath legacy) (.getCanonicalPath target))
               (.exists legacy))
      (if (.exists target)
        (t/event! ::legacy-database-left-behind
                  {:level :error
                   :data  {:legacy (.getPath legacy) :target (.getPath target)}})
        (let [legacy-wal (io/file (str (.getPath legacy) "-wal"))
              target-wal (io/file (str (.getPath target) "-wal"))
              tmp        (io/file (str (.getPath target) ".adopting"))]
          (some-> (.getParentFile target) .mkdirs)
          ;; io/copy and File.renameTo instead of the NIO varargs API: the
          ;; same semantics — overwriting copy, atomic same-directory rename
          ;; via rename(2) — without the array-class type hints.
          (when (.exists legacy-wal)
            (io/copy legacy-wal target-wal))
          (io/copy legacy tmp)
          (when-not (.renameTo tmp target)
            (throw (ex-info "Atomic rename failed during adoption"
                            {:target (.getPath target) :tmp (.getPath tmp)})))
          (doseq [suffix ["" "-wal" "-shm"]]
            (.delete (io/file (str (.getPath legacy) suffix))))
          (t/event! ::database-adopted
                    {:level :info
                     :data  {:from (.getPath legacy) :to (.getPath target)}}))))))


(defn adopt-legacy-database!
  "The deploy-order trap this closes: moving the database in the deploy
   pipeline and teaching the app the new path are two changes, and any deploy
   carrying only one of them strands the accounts (#201). Shipping the move
   inside the same artifact that reads the new path makes the pair atomic."
  []
  (adopt-database! (io/file "app.db") db-spec))


(defmacro on-connection
  "Runs body with a configured connection bound to sym and closes it afterwards.
   `jdbc/on-connection+options` closes only connections it opened itself, so a
   connection obtained here has to be released here — otherwise every call
   leaves one behind for the life of the process."
  [[sym connectable] & body]
  `(with-open [connection# (jdbc/get-connection ~connectable)]
     (let [~sym (-> connection#
                    (jdbc/with-logging
                     ;; The SQL text only: parameters and results carry
                     ;; tokens and user data.
                     (fn [_sym# sql-params#]
                       {:started-ms (System/currentTimeMillis)
                        :sql        (first sql-params#)})
                     (fn [_sym# state# result#]
                       (let [data# {:sql (:sql state#)
                                    :ms  (- (System/currentTimeMillis) (:started-ms state#))}]
                         (if (instance? Throwable result#)
                           (t/event! ::query-failed {:level :error :error result# :data data#})
                           (t/event! ::query {:level :debug :data data#})))))
                    (jdbc/with-options jdbc/unqualified-snake-kebab-opts))]

       ;; Enable foreign key constraints in SQLite, as they are disabled by default.
       ;; This constraint must be enabled separately for each connection.
       ;; See https://www.sqlite.org/foreignkeys.html#fk_enable
       (jdbc/execute! ~sym ["PRAGMA foreign_keys = on"])

       ~@body)))


;; This makes possible to pass clojure map or vector as a query parameter.
;; The value will be saved in a column as JSON string.
(extend-protocol prepare/SettableParameter
  clojure.lang.IPersistentMap
  (set-parameter [m ^PreparedStatement ps idx]
                 (.setObject ps idx (cheshire/generate-string m)))

  clojure.lang.IPersistentVector
  (set-parameter [m ^PreparedStatement ps idx]
                 (.setObject ps idx (cheshire/generate-string m))))


;; This makes BLOB columns to be read as a JSON value.
(extend-protocol result-set/ReadableColumn
  java.lang.String
  (read-column-by-label [v _]
                        v)
  (read-column-by-index [v ^ResultSetMetaData rsmeta idx]
                        (case (.getColumnTypeName rsmeta idx)
                          ;; It is not possible to declare "JSON" type in a table definition
                          ;; https://sqlite.org/json1.html#interface_overview
                          ;; I am going to use BLOB columns for JSON values only.
                          "BLOB" (cheshire/parse-string v true)
                          v)))


;;
;; Users
;;


(defn create-account!
  "Mints a bearer token, creates the account row holding its sha256, and its
   role-secured CouchDB userdb. Returns {:id int :token str} — the raw token
   is the user's key, stored nowhere on the server.

   The CouchDB steps run inside the transaction, like the guard below already
   does: the row and the secured userdb exist together or not at all (#254).
   A failed step throws, the insert rolls back, and the id — never handed to
   anyone — is simply minted again by the next attempt.

   Refuses an id whose userdb already exists. Account ids are row ids and
   CouchDB outlives app.db resets, so a recycled id would silently inherit —
   and sync down — the previous owner's data (seen live, #182). Refusal keeps
   the evidence; deleting the stale userdb is the operator's call."
  [db]
  (let [token (utils/random-token)
        id    (jdbc/with-transaction [tx db]
                ;; Explicit builder: the id must come back as :id no matter
                ;; which connection options the caller carries.
                (let [{:keys [id]} (jdbc/execute-one! tx
                                     ["INSERT INTO users (token_sha256) VALUES (?) RETURNING id"
                                      (utils/sha256-hex token)]
                                     {:builder-fn result-set/as-unqualified-maps})
                      dbname       (userdb/db-name id)]
                  (when (db/exists? dbname)
                    (throw (ex-info "userdb already exists for a fresh account id"
                                    {:type ::recycled-id :id id})))
                  (try
                    (db/secure (db/use dbname) {:members {:names [] :roles [(str "u:" id)]}})
                    (catch Exception e
                      ;; The rollback frees the id for reuse, and the guard
                      ;; above would refuse it for as long as a leftover
                      ;; userdb exists — so the userdb this very call created
                      ;; (the guard just proved the name was free) goes too.
                      ;; Best effort: if CouchDB is down, the delete fails
                      ;; like everything else and reconciliation reports the
                      ;; orphan.
                      (try @(db/destroy {:name dbname}) (catch Exception _))
                      (throw e)))
                  id))]
    {:id id :token token}))


(defn- authenticated-user-id
  "Returns the account id a bearer token authenticates, or nil."
  [db token]
  (when (utils/non-blank token)
    (:id (jdbc/execute-one! db
           ["SELECT id FROM users WHERE token_sha256 = ?"
            (utils/sha256-hex token)]
           {:builder-fn result-set/as-unqualified-maps}))))


(defn mint-grant!
  "Stores the sha256 of a fresh single-use grant token and returns the
   plaintext token — shown once, REPL-only (ADR-0006 phase-1 invites)."
  ([db]
   (mint-grant! db grants/ttl-ms))
  ([db ttl-ms]
   (let [token (utils/random-token)]
     (grants/store! db (utils/sha256-hex token) ttl-ms)
     token)))


(defn- configured-public-url
  "The address users reach the app at — where invite links must point. The
   server cannot infer it: behind the reverse proxy its own port says nothing
   about the public origin."
  [env]
  (str/replace (or (get env "LEARNING_APP__PUBLIC_URL") "https://sprecha.de")
               #"/+$"
               ""))


(defn- invite-url
  "The link an invited user opens. The token travels in the fragment, never in
   the query string: a fragment is not part of the request, so it stays out of
   access logs and Referer headers."
  [base-url token]
  (str base-url "/#invite=" token))


(defn- burn-grant!
  "Atomically burns an unused, unexpired grant. Returns true when burned."
  [db token]
  (some?
   (jdbc/execute-one!
     db
     ["UPDATE grants SET used_at = UNIXEPOCH()
      WHERE sha256 = ? AND used_at IS NULL AND expires_at > ?
      RETURNING sha256"
      (utils/sha256-hex token) (System/currentTimeMillis)])))


(comment
  (on-connection [db db-spec]
    (mint-grant! db)))


(def ^:private mail-operator!
  "Mails the operator an invite request's links."
  (invite-requests/mailer (System/getenv) running-from-source?))


;;
;; Auth — bearer token in a cookie, validated per request (ADR-0006)
;;


(defn- request-token
  "Reads the bearer token from the parsed cookies, or nil."
  [request]
  (get-in request [:cookies auth/token-cookie :value]))


(defn hmac-sign
  [^String user-name ^String secret]
  (.formatHex
   (HexFormat/of)
   (.doFinal
    (doto (Mac/getInstance "HmacSHA256")
      (.init (SecretKeySpec. (.getBytes secret) "HmacSHA256")))
    (.getBytes user-name))))


;;
;; Interceptors
;;


(defn- log-and-hide-exception
  "The exception goes to the log; the client gets a bare 500."
  [^Throwable e _request]
  (t/log! {:level :error :id ::request-failed :error e} "Request failed")
  {:status 500 :body ""})


(def cookie-interceptor
  "Parses the Cookie header into the request's :cookies map. The server only
   reads the bearer token — the client sets it — so there is no response leg."
  {:name  ::cookie-interceptor
   :enter (fn [ctx]
            (update ctx :request cookies/cookies-request))})


;;
;; Dictionary
;;


(def ^:private dictionary-dir (dictionary/configured-dictionary-dir (System/getenv)))


(def ^:private dictionary-manifest
  ;; Deferred, not read at load: a directory without a manifest must answer 404
  ;; the way a missing resource already does, not break the namespace.
  (delay
   (some-> (if dictionary-dir
             (let [file (io/file dictionary-dir "manifest.edn")]
               (when (.isFile file) file))
             (io/resource "dictionary/manifest.edn"))
           slurp
           edn/read-string)))


(defn- dictionary-body
  []
  (if dictionary-dir
    (response/file-response "dictionary.sqlite" {:root (str dictionary-dir)})
    (response/resource-response "dictionary/dictionary.sqlite")))


(defn- dictionary-handler
  [{:keys [path-params]}]
  (if-let [{:keys [sha256]} (get-in @dictionary-manifest [:files "dictionary.sqlite"])]
    (let [expected (str "dict." (subs sha256 0 12) ".sqlite")]
      (if (= (:filename path-params) expected)
        (-> (dictionary-body)
            (response/content-type "application/x-sqlite3")
            (response/header "ETag" (str "\"" sha256 "\""))
            (response/header "Content-Hash" sha256)
            ;; Content-addressed — gzip compression delegated to the reverse proxy.
            (response/header "Cache-Control" "public, max-age=31536000, immutable"))
        {:status 404 :body ""}))
    {:status 404 :body ""}))


(defn- wasm-handler
  [request]
  (when (str/ends-with? (:uri request) ".wasm")
    (some-> (response/resource-response (str "/public" (:uri request)))
            (response/content-type "application/wasm")
            (response/header "Cache-Control" "public, max-age=31536000, immutable"))))


;;
;; Routes
;;


(def ^:private sw-version-resource
  "Where the build leaves the service worker version. The name is repeated in
   build.clj, which writes it — the one thing the two sides must agree on."
  "sw-version")


(defn- public-dir-hash
  "Digest of the assets as they sit on disk. Only a source checkout can answer
   this: walking a directory is exactly what a packaged run cannot do, since a
   jar holds a flat list of entries and `resources/public` is not a path there."
  []
  (let [digest (MessageDigest/getInstance "SHA-256")
        dir    (io/file "resources/public")]
    (doseq [^java.io.File f (sort (file-seq dir))
            :when (.isFile f)]
      (with-open [is (io/input-stream f)]
        (let [buf (byte-array 8192)]
          (loop []
            (let [n (.read is buf)]
              (when (pos? n)
                (.update digest buf 0 n)
                (recur)))))))
    (subs (apply str (map #(format "%02x" (Byte/toUnsignedInt %)) (.digest digest))) 0 8)))


(defn- service-worker-version
  "What tells a browser that the worker changed. The build writes it into the
   artifact, where the assets were still files; a source checkout computes it
   from disk on every request, so a rebuild during development is picked up."
  []
  (if-let [built (io/resource sw-version-resource)]
    (str/trim (slurp built))
    (public-dir-hash)))


(defn- serve-now-allowed?
  "True for a source checkout, which is where a development bundle runs; a
   packaged build carries the version file and refuses the build mark's message."
  []
  (nil? (io/resource sw-version-resource)))


(def ^:private sw-precache-resource
  "Where the build leaves the asset paths, one per line. The name is repeated
   in build.clj, which writes it."
  "sw-precache")


(def ^:private shell-asset-dirs
  "Directories under `resources/public` that hold app shell assets and nothing
   else. Everything in them is precached, so a new stylesheet, font or icon
   joins the list with no edit here."
  ["/css/" "/fonts/" "/icons/"])


(def ^:private shell-asset-files
  "Shell assets named one by one, because the directory they sit in is not
   theirs alone. `/js` also holds the worker's own source and a metrics library
   only a development build loads; the root holds the manifests and the icon.
   `/` is a route and `/js/app/main.js` is compiler output — no walk can see
   either."
  ["/"
   "/dev-manifest.json"
   "/favicon.ico"
   "/icons.svg"
   "/js/app/main.js"
   "/js/sqlite3-dictionary.js"
   "/js/sqlite3-worker.js"
   "/js/sqlite3.js"
   "/js/sqlite3.wasm"
   "/manifest.json"])


(defn- public-dir-paths
  "Every file under `resources/public` as a request path. Only a source
   checkout can answer this, for the reason `public-dir-hash` gives."
  []
  (let [dir  (io/file "resources/public")
        root (.toPath dir)]
    (for [^java.io.File f (file-seq dir)
          :when (.isFile f)]
      (str "/" (.relativize root (.toPath f))))))


(defn- shell-assets
  "The rule, over whatever files a run found under `resources/public`:
   everything in a shell directory, plus the named files, and nothing else. A
   stylesheet, font or icon added to one of those directories joins with no
   edit anywhere; an old build's output under `/js`, a file downloaded into the
   checkout root, the worker's own source do not, however long they sit there.
   `cache.addAll` is atomic, so a list that simply followed the disk would be a
   worker that stops installing the day someone clears a stray directory out."
  [paths]
  (->> paths
       (filter (fn [path] (some #(str/starts-with? path %) shell-asset-dirs)))
       (concat shell-asset-files)
       distinct
       sort))


(defn- precache-paths
  "What the worker precaches. Where the files come from differs by run — the
   build writes its walk into the artifact, a source checkout walks the
   directory on every request — but the rule over them does not, so a packaged
   build of a developer's dirty checkout serves the same list as a clean one."
  []
  (shell-assets
   (if-let [built (io/resource sw-precache-resource)]
     (str/split-lines (str/trim (slurp built)))
     (public-dir-paths))))


(defn service-worker-handler
  [request]
  (when (= (:uri request) "/js/app/sw.js")
    (when-let [res (io/resource "public/js/sw.js")]
      (-> (response/response
           (str "const SW_VERSION=\""
                (service-worker-version)
                "\";\n"
                "const SERVE_NOW_ALLOWED="
                (serve-now-allowed?)
                ";\n"
                "const PRECACHE_URLS=" (cheshire/generate-string (precache-paths))
                ";\n"
                (slurp res)))
          (response/content-type "text/javascript")
          (response/header "Service-Worker-Allowed" "/")))))


(def resource-handler
  (ring/create-resource-handler
   {:path        "/"
    ;; Explicitly instruct handler not to serve any index files.
    ;; Without this, the handler may serve any index.html it finds on the classpath,
    ;; as happened once with the Datahike library.
    :index-files []}))


(defn regeneration-request
  "What a regeneration body asks: `{:params … :avoid […]}`, the subject's
   fields and the sentences to avoid, or `{:error …}` when the body cannot be
   answered. Entries of the lists that are not strings are dropped."
  [body]
  (let [strings (fn [values]
                  (filter string? (when (sequential? values) values)))
        avoid   (vec (strings (:avoid body)))
        word    (:word body)]
    (cond
      (not (map? body))
      {:error "Invalid JSON body"}

      (not (and (string? word) (not (str/blank? word))))
      {:error "Missing 'word' parameter"}

      (or (< 5 (count avoid))
          (some #(< 300 (count %)) avoid))
      {:error "Too many or too long 'avoid' sentences"}

      :else
      {:params {:context     (when (string? (:context body)) (:context body))
                :language    (when (string? (:language body)) (:language body))
                :pos         (when (string? (:pos body)) (:pos body))
                :translation (vec (strings (:translations body)))
                :word        word}
       :avoid  avoid})))


(defn- unknown-language?
  "Whether `subject` asks for a language the interface does not speak."
  [subject]
  (not (some #{(:language subject)} i18n/languages)))


(def ^:private json-responses
  "Route interceptors that send a map body as JSON and leave the request body
   alone: `/api/examples` checks the session before it reads the body."
  [(format/format-negotiate-interceptor muuntaja/instance)
   (format/format-response-interceptor muuntaja/instance)])


(defn- bad-request
  [message]
  {:status 400 :body {:error message}})


(defn- operator-link
  "The handlers of the operator's mail link that decides as `decision`. Mail
   scanners open links, so its GET only shows the button; its POST decides."
  [decision]
  (let [page (fn [invite-request]
               (if invite-request
                 {:status  200
                  :headers {"Content-Type" "text/html; charset=utf-8"}
                  :body    (hiccup/render (invite-requests/page invite-request decision))}
                 {:status 404 :body ""}))]
    {:get  (fn [{:keys [path-params]}]
             (page (on-connection [db db-spec]
                     (invite-requests/token-request db (:token path-params)))))
     :post (fn [{:keys [path-params]}]
             (page (on-connection [db db-spec]
                     (invite-requests/decide! db (:token path-params) decision))))}))


(defn- example-response
  "The HTTP answer to what `examples/get!` or `examples/regenerate!` returned."
  [result]
  (case (examples/outcome result)
    ;; A hit answers without the provider — that saving is the
    ;; whole point of the table. Authentication still ran above.
    ;; The cache holds the example as data, so a hit renders here
    ;; exactly as a fresh generation does.
    :outcome/success
    {:status 200 :body result}

    ;; The pair's failure: no candidate passed, or the input was
    ;; refused.
    :outcome/rejected
    {:status 422 :body {:error "No example could be generated for this word"}}

    ;; The provider's own delay; no delay sends no header.
    :outcome/throttled
    (let [seconds (some-> (:retry-after-ms result) (/ 1000.0) Math/ceil long)]
      (cond-> {:status 429 :body {:error "Examples are temporarily unavailable"}}
        (and seconds (pos? seconds))
        (assoc :headers {"Retry-After" (str seconds)})))

    ;; The service's problem, never the session's: the
    ;; provider's 401 or 403 does not reach the client.
    :outcome/unavailable
    {:status  503
     :headers {"Retry-After" "30"}
     :body    {:error "Examples are temporarily unavailable"}}))


(defn- page-language
  "The language the page speaks to `request`: that of its first `Accept-Language` tag. The learner's pick is
   in their user-db, which the server does not read; the client switches to it once it renders."
  [request]
  (i18n/system-language (some-> (get-in request [:headers "accept-language"])
                                (str/split #"[,;]")
                                first
                                str/trim)))


(def app-handler
  (http/ring-handler
   ;; Main handler
   (http/router
    [;; The manifest is JSON; the file next to it is not, so it stays clear of
     ;; muuntaja's negotiation.
     ["/dictionary/manifest"
      {:conflicting true
       :get (fn [_request]
              (if-let [{:keys [sha256]} (get-in @dictionary-manifest [:files "dictionary.sqlite"])]
                {:status  200
                 :headers {"Cache-Control" "no-cache"}
                 :body    {:filename (str "dict." (subs sha256 0 12) ".sqlite")
                           :hash     sha256}}
                {:status 404 :body ""}))
       :interceptors json-responses}]

     ["/dictionary/:filename"
      {:conflicting true
       :get dictionary-handler}]

     ["/auth/check"
      {:get
       (fn [request]
         (on-connection [db db-spec]
           (if-let [user-id (authenticated-user-id db (request-token request))]
             ;; Tell the CouchDB proxy which account + role to act as.
             (-> {:status 200}
                 (response/header "X-Auth-UserName" user-id)
                 (response/header "X-Auth-Roles" (str "u:" user-id))
                 (response/header "X-Auth-Token" (hmac-sign (str user-id) db-auth-secret)))
             {:status 401})))}]

     ;; Every answer here costs money at the provider, so the token is checked
     ;; before the parameters are: an anonymous caller does not even get told
     ;; which parameter it forgot.
     ["/api/examples"
      {:interceptors json-responses
       :get
       (fn [request]
         ;; One connection for the whole answer — the session, the lookup and
         ;; the write. There is no pool, so each `on-connection` was a file
         ;; opened and closed; the request holds one instead, generation
         ;; included, and SQLite readers do not block each other.
         (on-connection [db db-spec]
           (let [{:keys [word]} (:params request)]
             (cond
               (nil? (authenticated-user-id db (request-token request)))
               {:status 401 :body ""}

               ;; A repeated parameter arrives as a vector; neither the subject
               ;; nor the prompt can be built from two values.
               (some vector? (map (:params request) [:word :context :language :pos]))
               (bad-request "Parameters take one value each")

               (str/blank? word)
               (bad-request "Missing 'word' parameter")

               :else
               ;; One subject value feeds both the cache key and the prompt.
               (let [subject (examples/subject (:params request))]
                 (if (unknown-language? subject)
                   (bad-request "Unknown 'language'")
                   (example-response (examples/get! db subject))))))))

       ;; A different example for the same subject. The body carries what the
       ;; learner already has; the answer is theirs alone and never cached.
       :post
       (fn [request]
         (on-connection [db db-spec]
           (if (nil? (authenticated-user-id db (request-token request)))
             {:status 401 :body ""}
             (let [body    (try
                             (some-> (:body request) io/reader (cheshire/parse-stream true))
                             (catch Exception _
                               nil))
                   {:keys [avoid error params]} (regeneration-request body)
                   subject (examples/subject params)]
               (cond
                 error (bad-request error)
                 (unknown-language? subject) (bad-request "Unknown 'language'")
                 :else (example-response (examples/regenerate! db subject avoid)))))))}]

     ["/api/sync/updates"
      {:get
       (fn [request]
         ;; The socket authenticates like every other request: the bearer
         ;; cookie resolves to an account, or the upgrade is refused. A poke
         ;; carries no payload, so a hijacked socket learns only "something
         ;; changed".
         (let [token (get-in request [:cookies auth/token-cookie :value])
               id    (on-connection [db db-spec] (authenticated-user-id db token))]
           (if id
             (server/as-channel request
                                {:on-open  (fn [channel] (push/subscribe! id channel))
                                 :on-close (fn [channel _status] (push/unsubscribe! id channel))})
             {:status 401 :body ""})))}]

     ["/api/identity/provision"
      {:muuntaja muuntaja/instance
       :post
       (fn [{{:keys [invite]} :body-params}]
         (on-connection [db db-spec]
           (if (and (string? invite) (utils/non-blank invite) (burn-grant! db invite))
             (try
               {:status 200 :body (select-keys (create-account! db) [:id :token])}
               (catch Exception e
                 ;; No account was opened, so the invite is given back: one
                 ;; granted from a request cannot be granted again.
                 (jdbc/execute! db ["UPDATE grants SET used_at = NULL WHERE sha256 = ?" (utils/sha256-hex invite)])
                 (if (= ::recycled-id (:type (ex-data e)))
                   (do (t/event! ::recycled-id-refused {:level :error :data (ex-data e)})
                       {:status 503 :body ""})
                   (throw e))))
             {:status 403 :body ""})))}]

     ;; Adopting a key means holding a token and nothing else. The token comes
     ;; in the body rather than the cookie so a device can find out whether it
     ;; is valid, and whose it is, before making it this device's identity.
     ["/api/identity/account"
      {:muuntaja muuntaja/instance
       :post
       (fn [{{:keys [token]} :body-params}]
         (on-connection [db db-spec]
           (if-let [user-id (when (string? token) (authenticated-user-id db token))]
             {:status 200 :body {:id user-id}}
             {:status 401 :body ""})))}]

     ;; JSON in and out through muuntaja: bodies arrive as `:body-params` and
     ;; maps leave encoded.
     ["/api/invite-requests" {:muuntaja muuntaja/instance}
      ;; A device without an account asks the operator for an invite. It
      ;; sends a hash and two counts, nothing else, and gets no CouchDB
      ;; database.
      [""
       {:post
        (fn [request]
          (let [invite-request (invite-requests/invite-request (:body-params request))]
            (cond
              (nil? invite-request)
              {:status 400 :body {:error "Expected sha256, words and days"}}

              ;; nginx's X-Real-IP replaces whatever the client sent; the
              ;; socket's peer answers when nothing stands in front.
              (not (invite-requests/admit! (or (get-in request [:headers "x-real-ip"]) (:remote-addr request))
                                           (System/currentTimeMillis)))
              {:status 429 :headers {"Retry-After" "3600"} :body ""}

              :else
              (let [location (str "/api/invite-requests/" (:sha256 invite-request))
                    outcome  (on-connection [db db-spec]
                               (invite-requests/request! db
                                                         (configured-public-url (System/getenv))
                                                         mail-operator!
                                                         invite-request))]
                (case outcome
                  :outcome/created {:status 201 :headers {"Location" location} :body {:status "pending"}}
                  :failure/full    {:status 429 :headers {"Retry-After" "3600"} :body ""}
                  :failure/mail    {:status 503 :headers {"Retry-After" "60"} :body ""}
                  ;; Already requested: the answer points at the request
                  ;; that exists.
                  {:status 200 :headers {"Location" location} :body {:status (name outcome)}})))))}]

      ;; A request is read with the secret it was made with, in the header:
      ;; knowing a request's hash is not owning it, and a header stays out of
      ;; access logs. The answer links the next step.
      ["/:sha256"
       {:get
        (fn [{:keys [headers path-params]}]
          (let [secret (some->> (get headers "authorization") (re-matches #"Bearer (\S+)") second)
                status (when secret
                         (on-connection [db db-spec]
                           (invite-requests/secret-status db (:sha256 path-params) secret)))]
            (if status
              {:status  200
               :headers {"Cache-Control" "no-store"}
               :body    {:links  (case status
                                   :request/granted {:provision "/api/identity/provision"}
                                   :request/expired {:request "/api/invite-requests"}
                                   {})
                         :status (name status)}}
              {:status 404 :body ""})))}]

      ;; The operator's links. Under /api/ so the service worker never
      ;; answers them with the app shell.
      ["/:token"
       ["/grant" (operator-link :request/granted)]
       ["/decline" (operator-link :request/declined)]]]]

    {:data {:interceptors [(exception/exception-interceptor
                            {::exception/default log-and-hide-exception
                             ;; A body muuntaja cannot read is the client's
                             ;; mistake, not the server's.
                             :muuntaja/decode (fn [_error _request] {:status 400 :body ""})
                             :muuntaja/request-charset-negotiation
                             (fn [_error _request] {:status 400 :body ""})
                             :muuntaja/response-charset-negotiation
                             (fn [_error _request] {:status 400 :body ""})
                             :muuntaja/response-format-negotiation
                             (fn [_error _request] {:status 400 :body ""})})
                           ;; Mounts only where route data names `:muuntaja`.
                           (format/format-interceptor)
                           cookie-interceptor
                           (parameters/parameters-interceptor)
                           (keyword-parameters/keyword-parameters-interceptor)]}})

   ;; Default handler
   (fn [request]
     (hiccup/render-response
      {:html/layout
       (fn layout
         [{:html/keys [body]}]
         [:html {:lang (page-language request)}
          [:head
           [:meta {:charset "UTF-8"}]
           [:meta {:name "viewport" :content "width=device-width, initial-scale=1, interactive-widget=resizes-content"}]
           [:title "Sprecha"]
           [:link {:rel "icon" :href "/favicon.ico"}]
           ;; Dev installs carry a red-D-badged icon set so a device can hold
           ;; both the dev and the production PWA without confusing them.
           [:link
            {:rel  "manifest"
             :href (if running-from-source?
                     "/dev-manifest.json"
                     "/manifest.json")}]
           ;; The first screen's fonts are fetched with the document, not after
           ;; styles.css is parsed; the splash waits for them.
           (for [file ["nunito-v32-cyrillic-variable" "nunito-v32-latin-variable"]]
             [:link
              {:rel         "preload"
               :as          "font"
               :type        "font/woff2"
               :crossorigin true
               :href        (str "/fonts/Nunito/" file ".woff2")}])
           [:link {:rel "stylesheet" :href "/css/styles.css"}]
           [:script {:src "/js/app/main.js" :defer true}]]
          [:body
           (i18n/interpolate (page-language request) body)]])
       :html/body
       [:<>
        [:style
         "
                    .splash{display:flex;flex-direction:column;align-items:center;justify-content:center;height:100dvh;margin:0;font-family:Nunito,system-ui,sans-serif}
                    .splash-title{font-size:2rem;font-weight:700;color:#222;margin-bottom:1.5rem}
                    .splash-face circle{transform-box:fill-box;transform-origin:center}
                    .dot{animation:pulse 1.4s ease-in-out infinite alternate}
                    .sparkle{opacity:0;animation:flash 5s ease-out infinite}
                    @keyframes pulse{from{opacity:.3;transform:scale(.78)}to{opacity:1;transform:scale(1.1)}}
                    @keyframes flash{0%{opacity:0;transform:scale(.5)}5%{opacity:.65;transform:scale(1.3)}30%{opacity:.15;transform:scale(.8)}100%{opacity:0;transform:scale(.5)}}
                    .splash-sub{font-size:.95rem;color:#666;margin-top:2rem}
                  "]
        [:div.splash
         [:div.splash-title "Sprecha"]
         [:svg.splash-face {:viewBox "0 0 156 156" :width "156" :height "156"}
          [:circle.dot {:cx 55 :cy 32 :r 9 :fill "hsl(100 65% 72%)" :style "animation-delay:0s"}]
          [:circle.dot {:cx 101 :cy 32 :r 9 :fill "hsl(89 69% 71%)" :style "animation-delay:.15s"}]
          [:circle.dot {:cx 32 :cy 78 :r 9 :fill "hsl(78 73% 70%)" :style "animation-delay:.3s"}]
          [:circle.dot {:cx 124 :cy 78 :r 9 :fill "hsl(66 76% 69%)" :style "animation-delay:.45s"}]
          [:circle.dot {:cx 32 :cy 101 :r 9 :fill "hsl(55 80% 68%)" :style "animation-delay:.6s"}]
          [:circle.dot {:cx 124 :cy 101 :r 9 :fill "hsl(66 76% 69%)" :style "animation-delay:.75s"}]
          [:circle.dot {:cx 55 :cy 124 :r 9 :fill "hsl(78 73% 70%)" :style "animation-delay:.9s"}]
          [:circle.dot {:cx 78 :cy 124 :r 9 :fill "hsl(89 69% 71%)" :style "animation-delay:1.05s"}]
          [:circle.dot {:cx 101 :cy 124 :r 9 :fill "hsl(100 65% 72%)" :style "animation-delay:1.2s"}]
          [:circle.sparkle
           {:cx 25 :cy 18 :r 3 :fill "hsl(280 50% 65%)" :style "animation-delay:2s;animation-duration:4s"}]
          [:circle.sparkle
           {:cx 133 :cy 48 :r 2.5 :fill "hsl(280 50% 65%)" :style "animation-delay:3.5s;animation-duration:5.5s"}]
          [:circle.sparkle
           {:cx 78 :cy 56 :r 3 :fill "hsl(280 50% 65%)" :style "animation-delay:5s;animation-duration:6s"}]
          [:circle.sparkle
           {:cx 18 :cy 112 :r 2.5 :fill "hsl(280 50% 65%)" :style "animation-delay:1s;animation-duration:4.5s"}]
          [:circle.sparkle
           {:cx 142 :cy 128 :r 3 :fill "hsl(280 50% 65%)" :style "animation-delay:4.2s;animation-duration:5s"}]]
         [:div.splash-sub [:i18n :shell/loading]]]]
       :status 200}
      request))

   ;; interceptor queue executor
   {:executor sieppari/executor}))


(def ring-handler
  (let [ring-handler #(ring/routes service-worker-handler wasm-handler resource-handler app-handler)]
    (if running-from-source?
      (ring/reloading-ring-handler ring-handler)
      (ring-handler))))


(defonce server (atom nil))


(defn start-server!
  [app port]
  (ensure-log-handler!)
  (push/start!)
  ;; Loopback only: nginx stands in front and writes X-Real-IP, which the
  ;; invite limit trusts, so nothing may reach the server around it. More
  ;; threads than http-kit's 4: an invite request waits on the mail Worker,
  ;; and a few slow mails must not stall every other route.
  (reset! server (server/run-server app
                                    {:ip     "127.0.0.1"
                                     :legacy-return-value? false
                                     :port   port
                                     :thread 16})))


(def ^:private drain-timeout-ms
  "How long a stopping server keeps serving in-flight requests after the
   listening socket has closed."
  5000)


(defn stop-server!
  []
  (push/stop!)
  (when-some [running @server]
    (when-some [stopping (server/server-stop! running {:timeout drain-timeout-ms})]
      @stopping
      (reset! server nil))))


#_{:clj-kondo/ignore [:unused-private-var]}


(defonce ^:private shutdown-hook
  ;; systemd stop is SIGTERM; without this hook the JVM dies mid-request.
  ;; Stopping the server closes the listening socket first, then drains
  ;; in-flight requests — and draining is all the cleanup there is: SQLite
  ;; connections are per-request (`with-open` in `on-connection`), so the last
  ;; response closes the last one. `defonce` keeps the dev reload path from
  ;; registering a second hook; the hook itself only ever runs at JVM
  ;; shutdown, so it cannot fight `restart-server!`.
  (let [hook (Thread. ^Runnable
                      (fn []
                        (stop-server!)
                        (println "Server stopped"))
                      "graceful-shutdown")]
    (.addShutdownHook (Runtime/getRuntime) hook)
    hook))


(defn restart-server!
  [app port]
  (stop-server!)
  (start-server! app port))


(defn- serve!
  []
  (let [url (str "http://localhost:" port "/")]
    (require-couchdb-password! (System/getenv) running-from-source?)
    ;; Adopt, then migrate, then serve: the app never runs against an
    ;; outdated schema or a stranded database.
    (adopt-legacy-database!)
    (migrations/ensure-migrated! db-spec)
    ;; Report orphan userdbs; CouchDB being down logs a warning, never blocks boot.
    (reconciliation/report! db-spec)
    ;; Example generation reads the dictionary file; a missing or broken one
    ;; stops the boot rather than reading as words nobody knows.
    (dictionary/open! (System/getenv))
    (restart-server! #'ring-handler port)
    ;; Examples stored under the old key are re-keyed once the server listens:
    ;; the table's size is unbounded and each row costs a dictionary read. A
    ;; request meanwhile misses on a waiting row and generates; the re-key
    ;; keeps the older row. Failures are logged and never reach the server.
    (Thread/startVirtualThread
     (fn []
       (try (examples/rekey-cache! db-spec)
            (catch Throwable error
              (t/log! {:level :warn :id ::rekey-failed :data {:error (ex-message error)}}
                      "Example cache re-key failed")))))
    (println "Serving" url)))


(defn mint-invite!
  "Operator command (#262): mints a single-use grant against the configured
   database and prints the invite URL. Adopts and migrates first, exactly like
   serving does — the grant must land in the same database the server reads.
   Touches only SQLite, so no CouchDB credentials are demanded and no server
   comes up."
  []
  (adopt-legacy-database!)
  (migrations/ensure-migrated! db-spec)
  (on-connection [db db-spec]
    (println (invite-url (configured-public-url (System/getenv))
                         (mint-grant! db)))))


(defn -main
  [& args]
  ;; Logging first: adoption, migrations and the reconciliation report all
  ;; speak before the server starts, and events without a handler are
  ;; dropped silently (#218 — the dev alias ships none).
  (ensure-log-handler!)
  (case (first args)
    nil (serve!)
    "mint-invite" (mint-invite!)
    (binding [*out* *err*]
      (println "Unknown command:" (first args))
      (System/exit 2))))


(comment
  ;; Mint an operator invite (ADR-0006 phase-1)
  (on-connection [db db-spec]
    (mint-grant! db)))

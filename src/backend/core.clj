(ns core
  (:gen-class)
  (:require
   [cheshire.core :as cheshire]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [db :as db]
   [examples :as examples]
   [hiccup :as hiccup]
   [migrations :as migrations]
   [next.jdbc :as jdbc]
   [next.jdbc.prepare :as prepare]
   [next.jdbc.result-set :as result-set]
   [org.httpkit.server :as server]
   [reconciliation :as reconciliation]
   [reitit.http :as http]
   [reitit.http.interceptors.keyword-parameters :as keyword-parameters]
   [reitit.http.interceptors.parameters :as parameters]
   [reitit.interceptor.sieppari :as sieppari]
   [reitit.ring :as ring]
   [ring.middleware.cookies :as cookies]
   [ring.util.response :as response]
   [taoensso.telemere :as t]
   [utils :as utils])
  (:import
   [java.security MessageDigest SecureRandom]
   [java.sql PreparedStatement ResultSetMetaData]
   [java.util HexFormat]
   [javax.crypto Mac]
   [javax.crypto.spec SecretKeySpec]))


(set! *warn-on-reflection* true)


(def port (or (some-> (System/getenv "LEARNING_APP_PORT") Integer/parseInt) 8083))


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


(def db-auth-secret
  "Signs the proxy-auth headers `/auth/check` hands to CouchDB. A checkout may
   fall back to a well-known value; a packaged app may not, and says so instead
   of signing with something an attacker can guess."
  (or (System/getenv "LEARNING_APP_DB_AUTH_SECRET")
      (when running-from-source? "secret")
      (throw (ex-info "LEARNING_APP_DB_AUTH_SECRET is required outside a source checkout" {}))))


;;
;; DB
;;


(def db-spec
  {:dbtype "sqlite" :dbname "app.db"})


(defmacro on-connection
  "Runs body with a configured connection bound to sym and closes it afterwards.
   `jdbc/on-connection+options` closes only connections it opened itself, so a
   connection obtained here has to be released here — otherwise every call
   leaves one behind for the life of the process."
  [[sym connectable] & body]
  `(with-open [connection# (jdbc/get-connection ~connectable)]
     (let [~sym (-> connection#
                    (jdbc/with-logging
                     (fn [_sym# sql-params#]
                       {:time  (System/currentTimeMillis)
                        :query sql-params#})
                     (fn [_sym# state# result#]
                       (let [data#      {:time   (str (- (System/currentTimeMillis) (:time state#))
                                                      " ms")
                                         :query  (:query state#)
                                         :result result#}
                             log-level# (if (instance? Throwable result#)
                                          :error
                                          :debug)])))
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


(defn- sha256-hex
  [^String s]
  (.formatHex
   (HexFormat/of)
   (.digest (doto (MessageDigest/getInstance "SHA-256")
              (.update (.getBytes s "UTF-8"))))))


(def ^:private ^SecureRandom secure-random (SecureRandom.))


(defn- random-token
  "A 160-bit URL-safe bearer token (40 hex chars)."
  []
  (let [bytes (byte-array 20)]
    (.nextBytes secure-random bytes)
    (.formatHex (HexFormat/of) bytes)))


(defn create-account!
  "Mints a bearer token, creates the account row holding its sha256, and its
   role-secured CouchDB userdb. Returns {:id int :token str} — the raw token
   is the user's key, stored nowhere on the server.

   Refuses an id whose userdb already exists. Account ids are row ids and
   CouchDB outlives app.db resets, so a recycled id would silently inherit —
   and sync down — the previous owner's data (seen live, #182). Refusal keeps
   the evidence; deleting the stale userdb is the operator's call."
  [db]
  (let [token (random-token)
        id    (jdbc/with-transaction [tx db]
                ;; Explicit builder: the id must come back as :id no matter
                ;; which connection options the caller carries.
                (let [{:keys [id]} (jdbc/execute-one! tx
                                     ["INSERT INTO users (token_sha256) VALUES (?) RETURNING id"
                                      (sha256-hex token)]
                                     {:builder-fn result-set/as-unqualified-maps})]
                  (when (db/exists? (str "userdb-" id))
                    (throw (ex-info "userdb already exists for a fresh account id"
                                    {:type ::recycled-id :id id})))
                  id))]
    (db/secure (db/use (str "userdb-" id)) {:members {:names [] :roles [(str "u:" id)]}})
    {:id id :token token}))


(defn- authenticated-user-id
  "Returns the account id a bearer token authenticates, or nil."
  [db token]
  (when (utils/non-blank token)
    (:id (jdbc/execute-one! db
           ["SELECT id FROM users WHERE token_sha256 = ?"
            (sha256-hex token)]
           {:builder-fn result-set/as-unqualified-maps}))))


(defn mint-grant!
  "Stores the sha256 of a fresh single-use grant token and returns the
   plaintext token — shown once, REPL-only (ADR-0006 phase-1 invites)."
  ([db]
   (mint-grant! db (* 30 24 60 60 1000)))
  ([db ttl-ms]
   (let [token (random-token)]
     (jdbc/execute! db
       ["INSERT INTO grants (sha256, expires_at) VALUES (?, ?)"
        (sha256-hex token) (+ (System/currentTimeMillis) ttl-ms)])
     token)))


(defn- burn-grant!
  "Atomically burns an unused, unexpired grant. Returns true when burned."
  [db token]
  (some?
   (jdbc/execute-one!
     db
     ["UPDATE grants SET used_at = UNIXEPOCH()
      WHERE sha256 = ? AND used_at IS NULL AND expires_at > ?
      RETURNING sha256"
      (sha256-hex token) (System/currentTimeMillis)])))


(comment
  (on-connection [db db-spec]
    (mint-grant! db)))


;;
;; Auth — bearer token in a cookie, validated per request (ADR-0006)
;;


(def ^:private token-cookie "sprecha-token")


(defn- request-token
  "Reads the bearer token from the parsed cookies, or nil."
  [request]
  (get-in request [:cookies token-cookie :value]))


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


(def cookie-interceptor
  "Parses the Cookie header into the request's :cookies map. The server only
   reads the bearer token — the client sets it — so there is no response leg."
  {:name  ::cookie-interceptor
   :enter (fn [ctx]
            (update ctx :request cookies/cookies-request))})


;;
;; Dictionary
;;


(def ^:private dictionary-manifest
  (some-> (io/resource "dictionary/manifest.edn")
          slurp
          edn/read-string))


(defn- dictionary-handler
  [{:keys [path-params]}]
  (if-let [{:keys [sha256]} (get-in dictionary-manifest [:files "dictionary.sqlite"])]
    (let [expected (str "dict." (subs sha256 0 12) ".sqlite")]
      (if (= (:filename path-params) expected)
        (-> (response/resource-response "dictionary/dictionary.sqlite")
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


(defn service-worker-handler
  [request]
  (when (= (:uri request) "/js/app/sw.js")
    (when-let [res (io/resource "public/js/sw.js")]
      (-> (response/response
           (str "const SW_VERSION=\"" (service-worker-version) "\";\n" (slurp res)))
          (response/content-type "text/javascript")
          (response/header "Service-Worker-Allowed" "/")))))


(def resource-handler
  (ring/create-resource-handler
   {:path        "/"
    ;; Explicitly instruct handler not to serve any index files.
    ;; Without this, the handler may serve any index.html it finds on the classpath,
    ;; as happened once with the Datahike library.
    :index-files []}))


(def app-handler
  (http/ring-handler
   ;; Main handler
   (http/router
    [["/dictionary/:filename"
      {:get (fn [{:keys [path-params] :as req}]
              (if (= (:filename path-params) "manifest")
                (if-let [{:keys [sha256]} (get-in dictionary-manifest [:files "dictionary.sqlite"])]
                  (let [hash12 (subs sha256 0 12)]
                    {:status  200
                     :headers {"Content-Type"  "application/json"
                               "Cache-Control" "no-cache"}
                     :body    (cheshire/generate-string {:hash     sha256
                                                         :filename (str "dict." hash12 ".sqlite")})})
                  {:status 404 :body ""})
                (dictionary-handler req)))}]

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

     ["/api/examples"
      {:get
       (fn [request]
         (let [{:keys [context word translation]} (:params request)
               context (utils/non-blank context)]
           (cond
             (str/blank? word)
             {:status  400
              :headers {"Content-Type" "application/json"}
              :body    (cheshire/generate-string {:error "Missing 'word' parameter"})}

             :else
             (let [result (examples/generate-one! {:context     context
                                                   :translation translation
                                                   :word        word})]
               (if (examples/valid-example? result)
                 {:status  200
                  :headers {"Content-Type" "application/json"}
                  :body    (cheshire/generate-string result)}
                 {:status  (or (:status result) 502)
                  :headers (cond-> {"Content-Type" "application/json"}
                             (:retry-after-ms result)
                             (assoc "Retry-After"
                                    (str (max 1 (long (Math/ceil (/ (:retry-after-ms result) 1000.0)))))))
                  :body    (cheshire/generate-string
                            {:error "Examples are temporarily unavailable"})})))))}]

     ["/api/identity/provision"
      {:post
       (fn [request]
         (on-connection [db db-spec]
           (let [{:keys [invite]} (some-> (:body request) io/reader (cheshire/parse-stream true))]
             (if (and (utils/non-blank invite) (burn-grant! db invite))
               (try
                 (let [{:keys [id token]} (create-account! db)]
                   {:status  200
                    :headers {"Content-Type" "application/json"}
                    :body    (cheshire/generate-string {:id id :token token})})
                 (catch clojure.lang.ExceptionInfo e
                   (if (= ::recycled-id (:type (ex-data e)))
                     (do (t/event! ::recycled-id-refused {:level :error :data (ex-data e)})
                         {:status 503 :body ""})
                     (throw e))))
               {:status 403 :body ""}))))}]

     ;; Adopting a key means holding a token and nothing else. The token comes
     ;; in the body rather than the cookie so a device can find out whether it
     ;; is valid, and whose it is, before making it this device's identity.
     ["/api/identity/account"
      {:post
       (fn [request]
         (on-connection [db db-spec]
           (let [{:keys [token]} (some-> (:body request) io/reader (cheshire/parse-stream true))]
             (if-let [user-id (authenticated-user-id db token)]
               {:status  200
                :headers {"Content-Type" "application/json"}
                :body    (cheshire/generate-string {:id user-id})}
               {:status 401 :body ""}))))}]]

    {:data {:interceptors [cookie-interceptor
                           (parameters/parameters-interceptor)
                           (keyword-parameters/keyword-parameters-interceptor)]}})

   ;; Default handler
   (fn [request]
     (hiccup/render-response
      {:html/layout
       (fn layout
         [{:html/keys [body]}]
         [:html
          [:head
           [:meta {:charset "UTF-8"}]
           [:meta {:name "viewport" :content "width=device-width, initial-scale=1, interactive-widget=resizes-content"}]
           [:title "Sprecha"]
           [:link {:rel "icon" :href "/favicon.ico"}]
           [:link {:rel "manifest" :href "/manifest.json"}]
           [:link {:rel "stylesheet" :href "/css/styles.css"}]
           [:script {:src "/js/app/main.js" :defer true}]]
          [:body
           body]])
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
         [:div.splash-sub "Загружаем..."]]]
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
  (reset! server (server/run-server app {:port port :legacy-return-value? false})))


(def ^:private drain-timeout-ms
  "How long a stopping server keeps serving in-flight requests after the
   listening socket has closed."
  5000)


(defn stop-server!
  []
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


(defn -main
  []
  (let [url (str "http://localhost:" port "/")]
    ;; Migrate before serving: the app never runs against an outdated schema.
    (migrations/ensure-migrated! db-spec)
    ;; Report orphan userdbs; CouchDB being down logs a warning, never blocks boot.
    (reconciliation/report! db-spec)
    (restart-server! #'ring-handler port)
    (println "Serving" url)))


(comment
  ;; Mint an operator invite (ADR-0006 phase-1)
  (on-connection [db db-spec]
    (mint-grant! db)))

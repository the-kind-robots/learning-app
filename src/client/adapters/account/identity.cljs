(ns adapters.account.identity
  (:require
   [auth :as auth]
   [clojure.string :as str]
   [db :as db]
   [goog.crypt :as crypt]))


(def ^:private identity-doc-id "identity:local")


(def ^:private request-doc-id
  "The request to turn the sync on: its secret, which becomes the invite once granted, and what it said."
  "sync-request:local")


(defonce ^:private device-db
  ;; Held, not reopened: `db/use` constructs a PouchDB object, so asking twice builds two over one store.
  ;; Opened here because an incoming credential is handled before the databases open.
  (delay (db/use "device-db")))


(defn ^:async load-identity!
  "Returns {:id :token} for this device, or nil if it has none."
  []
  (when-let [doc (await (db/get @device-db identity-doc-id))]
    {:id    (:user-id doc)
     :token (:token doc)}))


(defn ^:async save-identity!
  "Persists {:id :token}, updating _rev if the doc already exists. device-db is
   the durable home of the token; the cookie below is derived from it on every
   boot, which is why losing device-db is what loses the account."
  [{:keys [id token]}]
  (let [existing (await (db/get @device-db identity-doc-id))
        doc      (cond-> {:_id     identity-doc-id
                          :token   token
                          :type    "identity"
                          :user-id id}
                   existing (assoc :_rev (:_rev existing)))]
    (await (db/insert @device-db doc))))


(defn use-identity!
  "Makes the server authenticate requests as this identity by writing its
   token to the cookie `/auth/check` reads. Not HttpOnly — the client owns the
   token, it builds the sync link from it. Kept for 400 days, the most a
   browser keeps a cookie: on iOS it is what an app added to the home screen
   brings along from Safari."
  [{:keys [token]}]
  (set! js/document.cookie
        (str auth/token-cookie "=" token "; Max-Age=34560000; path=/; SameSite=Lax; Secure")))


(defn cookie-token
  "The token of the cookie `use-identity!` writes, or nil."
  []
  (some (fn [pair]
          (let [[cookie-name value] (str/split pair #"=" 2)]
            (when (= auth/token-cookie cookie-name)
              value)))
        (str/split js/document.cookie #"; ")))


(defn ^:async account-id!
  "Returns the account id the token authenticates, or nil. Lets a device that
   was handed a key find out whether it is valid — and whose — before adopting
   it, which is why the token travels in the body instead of the cookie."
  [token]
  (let [response (await (js/fetch "/api/identity/account"
                                  #js {:method  "POST"
                                       :headers #js {"Content-Type" "application/json"}
                                       :body    (js/JSON.stringify #js {:token token})}))]
    (when (.-ok response)
      (:id (js->clj (await (.json response)) :keywordize-keys true)))))


(defn ^:async load-request
  "The stored request to turn the sync on, `{:secret :note :words :days}`, or nil."
  []
  (when-let [doc (await (db/get @device-db request-doc-id))]
    (select-keys doc [:days :note :secret :words])))


(defn ^:async save-request!
  "Stores `request`, `{:note :words :days}`, with a secret of its own, made once: a request written again keeps
   the secret it had until it is dropped."
  [request]
  (let [existing (await (db/get @device-db request-doc-id))
        secret   (or (:secret existing)
                     (crypt/byteArrayToHex (js/crypto.getRandomValues (js/Uint8Array. 32))))
        doc      (cond-> (assoc request
                                :_id    request-doc-id
                                :secret secret
                                :type   "sync-request")
                   existing (assoc :_rev (:_rev existing)))]
    (await (db/insert @device-db doc))))


(defn ^:async drop-request!
  "Forgets the request, so the next one gets a new secret."
  []
  (when-let [doc (await (db/get @device-db request-doc-id))]
    (await (db/remove @device-db doc))))


(defn- ^:async secret-hash
  "The lowercase hex SHA-256 of `secret`, the name the backend knows a request by."
  [secret]
  (crypt/byteArrayToHex
   (js/Uint8Array. (await (js/crypto.subtle.digest "SHA-256" (.encode (js/TextEncoder.) secret))))))


(defn ^:async send-request!
  "Posts `request` (#689). Resolves with the response's `:status` code and `:retry-after-ms`, the wait its
   `Retry-After` asks for in whole seconds (anything else is ignored); a request that never reached the server
   resolves with no status."
  [{:keys [days note secret words]}]
  (try
    (let [sha256   (await (secret-hash secret))
          response (await (js/fetch "/api/invite-requests"
                                    #js {:method  "POST"
                                         :headers #js {"Content-Type" "application/json"}
                                         :body    (js/JSON.stringify
                                                   #js {:days   days
                                                        :note   note
                                                        :sha256 sha256
                                                        :words  words})}))]
      {:retry-after-ms (some->> (.. response -headers (get "Retry-After"))
                                (re-matches #"\d+")
                                js/parseInt
                                (* 1000))
       :status (.-status response)})
    (catch js/Error _
      {})))


(defn ^:async request-status
  "What became of the request with `secret`: `:request/pending`, `:request/granted`, `:request/declined`,
   `:request/expired`, `:request/missing` when the server has none, or nil when it could not be asked or
   answered with a status it does not know."
  [secret]
  (try
    (let [sha256   (await (secret-hash secret))
          response (await (js/fetch (str "/api/invite-requests/" sha256)
                                    #js {:headers #js {"Authorization" (str "Bearer " secret)}}))]
      (cond
        (= 404 (.-status response)) :request/missing
        (.-ok response) (get {"declined" :request/declined
                              "expired"  :request/expired
                              "granted"  :request/granted
                              "pending"  :request/pending}
                             (.-status (await (.json response))))))
    (catch js/Error _
      nil)))


(defn ^:async provision!
  "Creates a server account against a single-use invite (ADR-0006).
   Returns the new identity {:id int :token str}."
  [invite]
  (let [response (await (js/fetch "/api/identity/provision"
                                  #js {:method  "POST"
                                       :headers #js {"Content-Type" "application/json"}
                                       :body    (js/JSON.stringify #js {:invite invite})}))]
    (if (.-ok response)
      (js->clj (await (.json response)) :keywordize-keys true)
      (throw (ex-info "Provision failed" {:status (.-status response)})))))

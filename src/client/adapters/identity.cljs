(ns adapters.identity
  (:require
   [db :as db]))


(def ^:private token-cookie "sprecha-token")


(def ^:private identity-doc-id "identity:local")


(defonce ^:private device-db
  ;; Held rather than reopened: in ClojureScript `db/use` is a PouchDB
  ;; constructor call, so asking for the database twice builds two objects over
  ;; the same store. Opened here instead of taken from the system's db map
  ;; because an incoming credential is handled before `:db/pouch` starts, when
  ;; that map does not exist yet.
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
   token, it builds the QR and the recovery link from it."
  [{:keys [token]}]
  (set! js/document.cookie
        (str token-cookie "=" token "; path=/; SameSite=Lax; Secure")))


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

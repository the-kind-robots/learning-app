(ns adapters.identity)


(defn ^:async claim!
  "Creates a new anonymous identity on the server. Returns {:id int :secret str}."
  []
  (let [response (await (js/fetch "/api/identity/claim"
                                  #js {:method "POST"}))]
    (if (.-ok response)
      (js->clj (await (.json response)) :keywordize-keys true)
      (throw (ex-info "Identity claim failed" {:status (.-status response)})))))


(defn ^:async auth!
  "Authenticates with {:id :secret}, establishing a session cookie."
  [{:keys [id secret]}]
  (let [response (await (js/fetch "/api/identity/auth"
                                  #js {:method  "POST"
                                       :headers #js {"Content-Type" "application/json"}
                                       :body    (js/JSON.stringify #js {:id id :secret secret})}))]
    (when-not (.-ok response)
      (throw (ex-info "Identity auth failed" {:status (.-status response) :id id})))))


(defn ^:async recovery-token!
  "Generates a burn-on-use recovery token. Returns {:token str}."
  []
  (let [response (await (js/fetch "/api/identity/recovery-token"
                                  #js {:method "POST"}))]
    (if (.-ok response)
      (js->clj (await (.json response)) :keywordize-keys true)
      (throw (ex-info "Recovery token request failed" {:status (.-status response)})))))


(defn ^:async redeem!
  "Redeems a recovery token. Returns {:id int :secret str} and sets session."
  [token]
  (let [response (await (js/fetch "/api/identity/redeem"
                                  #js {:method  "POST"
                                       :headers #js {"Content-Type" "application/json"}
                                       :body    (js/JSON.stringify #js {:token token})}))]
    (if (.-ok response)
      (js->clj (await (.json response)) :keywordize-keys true)
      (throw (ex-info "Recovery token redemption failed" {:status (.-status response)})))))

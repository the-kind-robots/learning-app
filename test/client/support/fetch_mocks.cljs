(ns client.support.fetch-mocks)


(defn- mock-headers
  [headers]
  #js {:get (fn [name]
              (get headers name))})


(defn mock-fetch-success
  "Returns a mock fetch that resolves with given data."
  [data]
  (fn [_url]
    (js/Promise.resolve
     #js {:ok   true
          :json (fn [] (js/Promise.resolve (clj->js data)))})))


(defn mock-fetch-success-invalid-json
  "Returns a mock fetch that resolves with ok=true but rejects while parsing JSON."
  []
  (fn [_url]
    (js/Promise.resolve
     #js {:ok   true
          :json (fn [] (js/Promise.reject (js/SyntaxError. "Unexpected token")))})))


(defn mock-fetch-error
  "Returns a mock fetch that resolves with error status and an empty body."
  [status]
  (fn [_url]
    (js/Promise.resolve #js {:ok     false
                             :status status
                             :json   (fn [] (js/Promise.reject (js/SyntaxError. "Unexpected end of JSON input")))})))


(defn mock-fetch-broken-body
  "Returns a mock fetch whose success response breaks off while its body
   arrives."
  []
  (fn [_url]
    (js/Promise.resolve
     #js {:ok     true
          :status 200
          :json   (fn [] (js/Promise.reject (js/TypeError. "network error")))})))


(defn mock-fetch-error-with-body
  "Returns a mock fetch that resolves with error status and JSON body."
  ([status data]
   (mock-fetch-error-with-body status data nil))
  ([status data headers]
   (fn [_url]
     (js/Promise.resolve
      #js {:ok      false
           :status  status
           :headers (mock-headers headers)
           :json    (fn [] (js/Promise.resolve (clj->js data)))}))))


(defn mock-fetch-network-error
  "Returns a mock fetch that rejects with network error."
  []
  (fn [_url]
    (js/Promise.reject (js/Error. "Network error"))))


(defn mock-fetch-until-aborted
  "Returns a mock fetch that answers nothing until the request's signal
   aborts it, and then rejects as a browser does."
  []
  (fn [_url ^js options]
    (js/Promise.
     (fn [_resolve reject]
       (let [signal  (.-signal options)
             aborted #(reject (js/DOMException. "The operation was aborted." "AbortError"))]
         ;; A signal aborted before the call rejects at once, as fetch does.
         (if (.-aborted signal)
           (aborted)
           (.addEventListener signal "abort" aborted)))))))

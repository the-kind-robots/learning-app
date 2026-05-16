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
          :json (fn [] (js/Promise.reject (js/Error. "Invalid JSON response")))})))


(defn mock-fetch-error
  "Returns a mock fetch that resolves with error status."
  [status]
  (fn [_url]
    (js/Promise.resolve #js {:ok false :status status})))


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

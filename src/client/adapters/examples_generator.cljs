(ns adapters.examples-generator
  "The browser's side of fetching examples: the request to the backend,
   whether the device is online, and whether the tab is visible. What to
   ask for, and what a failure does, is decided in `use-cases.examples`."
  (:require
   [utils :as utils]))


(defn- url
  "The address that asks the backend for an example of `subject`. Throws a URIError when a part cannot be
   encoded, as half of a character written as two UTF-16 units cannot."
  [{:keys [collection-name pos translations word]}]
  (cond-> (reduce (fn [url translation]
                    (str url "&translation=" (js/encodeURIComponent translation)))
                  (str "/api/examples?word=" (js/encodeURIComponent word))
                  translations)
    (utils/non-blank pos)
    (str "&pos=" (js/encodeURIComponent pos))

    (utils/non-blank collection-name)
    (str "&context=" (js/encodeURIComponent collection-name))))


(defn- retry-after-ms
  "The wait the `Retry-After` header of `response` asks for, in
   milliseconds, or nil. Only a whole number of seconds counts; anything
   else, such as an HTTP date or `30 seconds`, is ignored."
  [^js response]
  (when-let [seconds (some->> (some-> (.-headers response) (.get "Retry-After"))
                              (re-matches #"\d+"))]
    (* 1000 (js/parseInt seconds 10))))


(defn- failure-kind
  "The kind of failure an answer with the status `status` is
   (`specs/example-fetch-error-clarity/spec.md`)."
  [status]
  (cond
    (#{401 403} status) :failure/unauthorized
    (= 429 status)      :failure/throttled
    ;; 408 and 425: the server timed out or refused early data. That says nothing about the subject, so they count as 5xx.
    (#{408 425} status) :failure/unavailable
    (<= 500 status)     :failure/unavailable
    :else               :failure/rejected))


(defn- ^:async body
  "The JSON body of `response` as data, or `::not-json` when the body
   arrived and is not JSON. A body that breaks off while it arrives throws,
   as a request that never arrived does."
  [^js response]
  (try
    (js->clj (await (.json response)) :keywordize-keys true)
    (catch js/SyntaxError _
      ::not-json)))


(defn- ^:async answer
  "What the backend's `response` says, in the shape `fetch-one` resolves with; `signal` tells an abort from a
   failure."
  [^js response signal]
  (let [status  (.-status response)
        content (await (body response))]
    (cond
      (.-aborted signal)
      {:failure :failure/aborted}

      (not (.-ok response))
      (let [wait-ms (retry-after-ms response)]
        (cond-> {:failure (failure-kind status) :status status}
          (:error content) (assoc :message (:error content))
          wait-ms (assoc :retry-after-ms wait-ms)))

      (and (map? content) (:value content) (:translation content))
      {:example content}

      :else
      {:failure :failure/invalid-response :status status})))


(defn- failure
  "The failure an error `err` of a request is, aborted by `signal` or not."
  [err ^js signal]
  (if (.-aborted signal)
    {:failure :failure/aborted}
    {:failure :failure/network :message (ex-message err)}))


(defn ^:async fetch-one
  "Asks the backend for an example of `subject`; `signal` aborts the request. Never rejects: resolves with
   `{:example example}`, or `{:failure kind}` with `:status`, `:message` and `:retry-after-ms` when the
   backend answered (`:failure/invalid-subject` sends nothing)."
  [subject signal]
  (if-let [address (try (url subject) (catch js/URIError _ nil))]
    (try
      ;; The endpoint authenticates by the session cookie. "include" keeps it attached if the URL ever gains an
      ;; origin, where the default would stop sending it and every example would come back 401.
      (await (answer (await (js/fetch address #js {:credentials "include" :signal signal})) signal))
      (catch :default err
        (failure err signal)))
    {:failure :failure/invalid-subject}))


(defn ^:async request-another
  "Asks the backend for an example of `subject` that is none of the sentences in `avoid`, the answer for this
   learner alone. Never rejects: resolves as `fetch-one` does."
  [{:keys [collection-name pos translations word]} avoid signal]
  (try
    (await (answer (await (js/fetch "/api/examples"
                                    #js {:body        (js/JSON.stringify
                                                       #js {:avoid        (clj->js avoid)
                                                            :context      (utils/non-blank collection-name)
                                                            :pos          (utils/non-blank pos)
                                                            :translations (clj->js translations)
                                                            :word         word})
                                         :credentials "include"
                                         :headers     #js {"Content-Type" "application/json"}
                                         :method      "POST"
                                         :signal      signal}))
                   signal))
    (catch :default err
      (failure err signal))))

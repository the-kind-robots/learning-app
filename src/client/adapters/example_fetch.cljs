(ns adapters.example-fetch
  "Fetching an example sentence from the backend, by a task in the queue,
   and keeping it in device-db."
  (:require
   [adapters.learner.documents :as documents]
   [db.pouch :as dbs]
   [lambdaisland.glogi :as log]
   [tasks :as tasks]
   [utils :as utils]))


(def invalid-response-message
  "Invalid example response from backend")


(defn- russian-translations
  "Collect user-confirmed Russian translations as a vector of strings."
  [word-doc]
  (->> (:translation word-doc)
       (filter #(= "ru" (:lang %)))
       (map :value)
       (filter utils/non-blank)
       vec))


(defn- retry-after-ms
  [response]
  (some-> response
          (.-headers)
          (.get "Retry-After")
          (js/parseFloat)
          (* 1000)
          (js/Math.ceil)
          (long)))


(defn ^:async fetch-one
  "Fetches an example sentence for the given German word from the backend.
   Returns a promise resolving to the example map."
  [word translations collection-name]
  (let [base     (str "/api/examples?word=" (js/encodeURIComponent word))
        url      (->> translations
                      (filter utils/non-blank)
                      (map #(str "&translation=" (js/encodeURIComponent %)))
                      (reduce str base))
        url      (if (utils/non-blank collection-name)
                   (str url "&context=" (js/encodeURIComponent collection-name))
                   url)
        ;; The endpoint authenticates by the session cookie, so the request
        ;; must carry it. Relative and same-origin, the browser would attach it
        ;; under the default anyway; "include" keeps it attached even if the URL
        ;; ever gains an origin, where the default would silently stop sending
        ;; it and every example would come back 401.
        response (await (js/fetch url #js {:credentials "include"}))]
    (if (.-ok response)
      (let [json (try
                   (await (.json response))
                   (catch js/Error _ ::invalid-json))]
        (if (= ::invalid-json json)
          (throw (ex-info invalid-response-message
                          {:word word :status 502 :error-kind :invalid-json}))
          (let [example (js->clj json :keywordize-keys true)]
            (if (and (:value example) (:translation example))
              example
              (throw (ex-info invalid-response-message
                              {:word       word
                               :status     502
                               :error-kind :invalid-response
                               :example    example}))))))
      (let [error-body (try (js->clj (await (.json response)) :keywordize-keys true)
                            (catch js/Error _ nil))
            status     (.-status response)
            retry-ms   (retry-after-ms response)
            message    (or (:error error-body) "Server error fetching example")]
        (throw (ex-info message
                        {:word           word
                         :status         status
                         :retry-after-ms retry-ms
                         :error-body     error-body}))))))


(defn save-example!
  "Saves an example document for a vocabulary word. When collection-id is
   nil the field is omitted — the doc belongs to the implicit main card."
  [dbs clock word-id word collection-id example]
  (when-not (and (:value example) (:translation example))
    (throw (ex-info "Invalid example: missing required fields"
                    {:word-id word-id :example example})))
  (let [example-doc (cond-> {:created-at  ((:clock/now-iso clock))
                             :structure   (:structure example)
                             :translation (:translation example)
                             :value       (:value example)
                             :word        word
                             :word-id     word-id}
                      collection-id (assoc :collection-id collection-id))]
    (dbs/insert dbs documents/example-schema example-doc)))


(def fetch-task-type
  "The task type an example fetch is queued under. Public because clearing what
   an account left behind has to name it (`sync/forget-account-data!`)."
  "example-fetch")


(defn- fetch-data
  "What a fetch needs to run without reading the entry back."
  [word collection-id collection-name]
  {:collection-id collection-id
   :collection-name collection-name
   :translations  (russian-translations word)
   :word          (:value word)
   :word-id       (:id word)})


(defn- fetch-id
  "The id of the fetch for one pair. The pair is the work, so the pair is the
   identity: asking twice writes the same id twice, and the second is a
   conflict the database refuses — nobody has to read the queue to find out
   what is already in it."
  [word-id collection-id]
  (str "task:" fetch-task-type ":" word-id ":" collection-id))


(defn request!
  "Queues a fetch for each of `requests` — `{:collection-id :collection-name
   :word}`, where the word is `{:id :value :translation}` — in one write. One
   form for one and for a vocabulary's worth: a device catching up asks for as
   many pairs as it is missing, and adding a word asks for one."
  [dbs clock requests]
  (tasks/create-tasks! dbs
                       clock
                       fetch-task-type
                       (mapv (fn [{:keys [collection-id collection-name word]}]
                               {:id   (fetch-id (:id word) collection-id)
                                :data (fetch-data word collection-id collection-name)})
                             requests)))


(defmethod tasks/execute-task fetch-task-type
  [{:keys [data]} {:keys [clock dbs]}]
  (let [{:keys [collection-id collection-name translations word word-id]} data]
    ((fn ^:async f
       []
       (try
         (let [example (await (fetch-one word translations collection-name))]
           (await (save-example! dbs clock word-id word collection-id example))
           true)
         (catch js/Error err
           (log/warn :example-fetch/failed {:word-id word-id :error (ex-message err)})
           (if-let [retry-ms (:retry-after-ms (ex-data err))]
             {:retry-after-ms retry-ms}
             false)))))))

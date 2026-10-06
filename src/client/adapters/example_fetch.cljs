(ns adapters.example-fetch
  "Fetching an example sentence from the backend, by a task in the queue,
   and keeping it in user-db, where it replicates with the account."
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
  "Saves `example` as an example of the entry `word-id`, whose text is
   `word`, in the collection `collection-id`, under the id its content
   gives it (`documents/example-doc`). When collection-id is nil the field
   is omitted — the doc belongs to the implicit main card. When the same
   example is stored already, this keeps the stored one and resolves nil."
  [dbs word-id word collection-id example]
  (when-not (and (:value example) (:translation example))
    (throw (ex-info "Invalid example: missing required fields"
                    {:word-id word-id :example example})))
  (dbs/insert-if-absent dbs
                        documents/example-schema
                        (documents/example-doc word-id word collection-id example)))


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
  "The id of the fetch for one pair, `pair-key` (`documents/pair-key`).
   The pair is the work, so the pair is the identity: asking twice writes
   the same id twice, and the second is a conflict the database refuses —
   nobody has to read the queue to find out what is already in it."
  [pair-key]
  (str tasks/id-prefix fetch-task-type ":" pair-key))


(defn request!
  "Queues a fetch for each of `requests` — `{:collection-id :collection-name
   :word :delay-ms}`, where the word is `{:id :value :translation}` — in one
   write. One form for one and for a vocabulary's worth: a device catching
   up asks for as many pairs as it is missing, and adding a word asks for
   one. A fetch is due `:delay-ms` from now, or now without it."
  [dbs clock requests]
  (tasks/create-tasks! dbs
                       clock
                       fetch-task-type
                       (mapv (fn [{:keys [collection-id collection-name delay-ms word]}]
                               {:id       (fetch-id (documents/pair-key (:id word) collection-id))
                                :data     (fetch-data word collection-id collection-name)
                                :delay-ms delay-ms})
                             requests)))


(defn- pair-key-of
  "The pair an example id names (`documents/example-doc`): the id without
   its `example:` prefix and its content hash."
  [example-id]
  (let [pair (subs example-id (count "example:"))]
    (subs pair 0 (.lastIndexOf pair ":"))))


(defn ^:async cancel-answered!
  "Deletes the queued fetches of the pairs that `example-ids`, the ids of
   examples that just arrived, answer. The fetch ids follow from the
   example ids, so the queue is not searched. Resolves with how many it
   deleted."
  [dbs example-ids]
  (let [queued (await (dbs/read-ids dbs
                                    (:db tasks/schema)
                                    (into []
                                          (comp (map pair-key-of)
                                                (distinct)
                                                (map fetch-id))
                                          example-ids)))
        live   (into [] (comp (remove :_deleted) (map documents/tombstone)) queued)]
    (count (await (dbs/bulk-docs dbs tasks/schema live)))))


(defonce ^:private runs-after
  ;; What every fetch waits for before it asks whether its pair is answered
  ;; (`hold-until!`). Nothing, until something holds them.
  (atom (js/Promise.resolve nil)))


(defn hold-until!
  "Holds every example fetch back until `ready`, a promise, resolves: a
   fetch then asks whether its pair is answered, and sends its request only
   when it is not. The queue itself is not held."
  [ready]
  (reset! runs-after ready))


(defn- ^:async answered?
  "Whether user-db holds an example of the entry `word-id` that a read in
   the collection `collection-id` sees (`documents/example-id-prefix`). One
   read of ids."
  [dbs word-id collection-id]
  (await (dbs/id-with-prefix? dbs documents/example-schema (documents/example-id-prefix word-id collection-id))))


(defmethod tasks/execute-task fetch-task-type
  [{:keys [data]} {:keys [dbs]}]
  (let [{:keys [collection-id collection-name translations word word-id]} data]
    ((fn ^:async f
       []
       (try
         (await @runs-after)
         ;; An example may have arrived since the task was queued: by
         ;; replication, or by the move from device-db. Then the pair is
         ;; answered, and the task is done without a request.
         (when-not (await (answered? dbs word-id collection-id))
           (let [example (await (fetch-one word translations collection-name))]
             (await (save-example! dbs word-id word collection-id example))))
         true
         (catch js/Error err
           (log/warn :example-fetch/failed {:word-id word-id :error (ex-message err)})
           (if-let [retry-ms (:retry-after-ms (ex-data err))]
             {:retry-after-ms retry-ms}
             false)))))))

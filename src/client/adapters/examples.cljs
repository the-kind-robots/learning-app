(ns adapters.examples
  "Client module for fetching example sentences from the backend."
  (:refer-clojure :exclude [list find])
  (:require
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
        response (await (js/fetch url))]
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
                             :type        "example"
                             :value       (:value example)
                             :word        word
                             :word-id     word-id}
                      collection-id (assoc :collection-id collection-id))]
    (dbs/insert dbs example-doc)))


(defn- collection-selector
  "Selector clause for example queries. Themed cards see only their own
   collection-scoped examples. The main card (nil collection-id) acts as
   a union view — no filter is applied, so any example for the word
   surfaces regardless of which collection generated it."
  [collection-id]
  (when collection-id
    {:collection-id collection-id}))


(defn ^:async find
  "Retrieves the example document for a given word-id and collection-id, or nil."
  [dbs word-id collection-id]
  (let [selector (merge {:type "example" :word-id word-id}
                        (collection-selector collection-id))
        {examples :docs} (await (dbs/find dbs {:selector selector}))]
    (first examples)))


(defn ^:async list
  "Retrieves example documents for the given word-ids and collection-id."
  [dbs word-ids collection-id]
  (let [selector (merge {:type "example" :word-id {:$in word-ids}}
                        (collection-selector collection-id))
        {examples :docs} (await (dbs/find dbs {:selector selector}))]
    examples))


(defn ^:async remove!
  "Deletes an example document by its _id. No-op if document doesn't exist."
  [dbs example-id]
  (let [example (await (dbs/get dbs "example" example-id))]
    (when example
      (await (dbs/remove dbs example)))))


(defn ^:async purge-by-collection!
  "Tombstones every example doc bound to `collection-id` in one atomic
   device-db bulk write. Called when a collection is deleted to keep
   example storage from accumulating orphans."
  [dbs collection-id]
  (let [{examples :docs} (await (dbs/find dbs
                                          {:selector {:type "example"
                                                      :collection-id collection-id}}))]
    (when (seq examples)
      (await (dbs/bulk-docs dbs "example" (mapv #(assoc % :_deleted true) examples))))))


(defn request!
  [dbs clock word-id collection-id collection-name]
  (tasks/create-task! dbs
                      clock
                      "example-fetch"
                      {:collection-id   collection-id
                       :collection-name collection-name
                       :word-id         word-id}))


(defmethod tasks/execute-task "example-fetch"
  [{:keys [data]} {:keys [clock dbs]}]
  (let [{:keys [collection-id collection-name word-id]} data]
    ((fn ^:async f
       []
       (let [word-doc (await (dbs/get dbs "vocab" word-id))]
         (if-not word-doc
           (do
             (log/warn :example-fetch/word-not-found {:word-id word-id})
             true)
           (try
             (let [example (await (fetch-one (:value word-doc)
                                             (russian-translations word-doc)
                                             collection-name))]
               (await (save-example! dbs clock word-id (:value word-doc) collection-id example))
               true)
             (catch js/Error err
               (log/warn :example-fetch/failed {:word-id word-id :error (ex-message err)})
               (if-let [retry-ms (:retry-after-ms (ex-data err))]
                 {:retry-after-ms retry-ms}
                 false)))))))))

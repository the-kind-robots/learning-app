(ns repo.examples
  "Client module for fetching example sentences from the backend."
  (:refer-clojure :exclude [list find])
  (:require
   [dbs :as dbs]
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
  ([word]
   (fetch-one word []))
  ([word translations]
   (let [url      (->> translations
                       (filter utils/non-blank)
                       (map #(str "&translation=" (js/encodeURIComponent %)))
                       (reduce str (str "/api/examples?word=" (js/encodeURIComponent word))))
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
                          :error-body     error-body})))))))


(defn save-example!
  "Saves an example document for a vocabulary word."
  [dbs word-id word example]
  (when-not (and (:value example) (:translation example))
    (throw (ex-info "Invalid example: missing required fields"
                    {:word-id word-id :example example})))
  (let [example-doc {:type        "example"
                     :word-id     word-id
                     :word        word
                     :value       (:value example)
                     :translation (:translation example)
                     :structure   (:structure example)
                     :created-at  (utils/now-iso)}]
    (dbs/insert dbs example-doc)))


(defn ^:async find
  "Retrieves the example document for a given word-id, or nil if none exists."
  [dbs word-id]
  (let [{examples :docs} (await (dbs/find dbs {:selector {:type "example" :word-id word-id}}))]
    (first examples)))


(defn ^:async list
  "Retrieves example documents for the given word-ids."
  [dbs word-ids]
  (let [{examples :docs} (await (dbs/find dbs {:selector {:type "example" :word-id {:$in word-ids}}}))]
    examples))


(defn ^:async remove!
  "Deletes an example document by its _id. No-op if document doesn't exist."
  [dbs example-id]
  (let [example (await (dbs/get dbs "example" example-id))]
    (when example
      (await (dbs/remove dbs example)))))


(defn create-fetch-task!
  "Creates a task to fetch an example for the given word-id."
  [word-id]
  (tasks/create-task! "example-fetch" {:word-id word-id}))


(defmethod tasks/execute-task "example-fetch"
  [{:keys [data]} dbs]
  (let [{:keys [word-id]} data]
    ((fn ^:async f []
       (let [word-doc (await (dbs/get dbs "vocab" word-id))]
         (if-not word-doc
           (do
             (log/warn :example-fetch/word-not-found {:word-id word-id})
             true)
           (try
             (let [example (await (fetch-one (:value word-doc)
                                             (russian-translations word-doc)))]
               (await (save-example! dbs word-id (:value word-doc) example))
               true)
             (catch js/Error err
               (log/warn :example-fetch/failed {:word-id word-id :error (ex-message err)})
               (if-let [retry-ms (:retry-after-ms (ex-data err))]
                 {:retry-after-ms retry-ms}
                 false)))))))))

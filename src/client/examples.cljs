(ns examples
  "Client module for fetching example sentences from the backend."
  (:refer-clojure :exclude [list find])
  (:require
   [dbs :as dbs]
   [lambdaisland.glogi :as log]
   [promesa.core :as p]
   [tasks :as tasks]
   [utils :as utils]))


(def invalid-response-message
  "Invalid example response from backend")


(defn- primary-translation
  [word-doc]
  (or (->> (:translation word-doc)
           (filter #(= "ru" (:lang %)))
           first
           :value)
      (some-> (:translation word-doc) first :value)))


(defn- retry-after-ms
  [response]
  (some-> response
          (.-headers)
          (.get "Retry-After")
          (js/parseFloat)
          (* 1000)
          (js/Math.ceil)
          (long)))


(defn fetch-one
  "Fetches an example sentence for the given German word from the backend.
   Returns a promise that resolves to the example map.
   Rejects on network or server errors."
  ([word]
   (fetch-one word nil))
  ([word translation]
   (let [url (str "/api/examples?word=" (js/encodeURIComponent word)
                  (when (utils/non-blank translation)
                    (str "&translation=" (js/encodeURIComponent translation))))]
     (p/let [response (js/fetch url)]
       (if (.-ok response)
         ;; Successful HTTP still needs validation: the body may be invalid JSON
         ;; or a malformed example payload.
         (p/let [json (-> (.json response)
                          (p/catch (fn [_error] ::invalid-json)))]
           (if (= ::invalid-json json)
             (p/rejected
              (ex-info invalid-response-message
                       {:word word
                        :status 502
                        :error-kind :invalid-json}))
             (let [example (js->clj json :keywordize-keys true)]
               (if (and (:value example) (:translation example))
                 example
                 (p/rejected
                  (ex-info invalid-response-message
                           {:word word
                            :status 502
                            :error-kind :invalid-response
                            :example example}))))))
         ;; On non-OK responses, prefer the backend-provided error message when available.
         (p/let [error-body (-> (.json response)
                                (p/catch (constantly nil)))
                 error-body (some-> error-body (js->clj :keywordize-keys true))
                 status     (.-status response)
                 retry-after-ms (retry-after-ms response)
                 message    (or (:error error-body) "Server error fetching example")]
           (p/rejected
            (ex-info message
                     {:word word
                      :status status
                      :retry-after-ms retry-after-ms
                      :error-body error-body}))))))))


(defn save-example!
  "Saves an example document for a vocabulary word.
   `dbs` - the databases map
   `word-id` - the _id of the vocab document
   `word` - the German word (denormalized for convenience)
   `example` - map with :value, :translation, :structure from the backend

   Returns a promise. Throws if example is invalid."
  [dbs word-id word example]
  (when-not (and (:value example) (:translation example))
    (throw (ex-info "Invalid example: missing required fields"
                    {:word-id word-id :example example})))
  (let [example-doc {:type          "example"
                     :word-id       word-id
                     :word          word
                     :value         (:value example)
                     :translation   (:translation example)
                     :structure     (:structure example)
                     :gloss-mismatch (:glossMismatch example)
                     :created-at    (utils/now-iso)}]
    (dbs/insert dbs example-doc)))


(defn find
  "Retrieves the example document for a given word-id, or nil if none exists."
  [dbs word-id]
  (p/let [{examples :docs} (dbs/find dbs {:selector {:type "example" :word-id word-id}})]
    (first examples)))


(defn list
  "Retrieves example documents for the given word-ids."
  [dbs word-ids]
  (p/let [{examples :docs} (dbs/find dbs {:selector {:type "example" :word-id {:$in word-ids}}})]
    examples))


(defn remove!
  "Deletes an example document by its _id. No-op if document doesn't exist."
  [dbs example-id]
  (p/let [example (dbs/get dbs "example" example-id)]
    (when example
      (dbs/remove dbs example))))


(defn create-fetch-task!
  "Creates a task to fetch an example for the given word-id."
  [word-id]
  (tasks/create-task! "example-fetch" {:word-id word-id}))


(defmethod tasks/execute-task "example-fetch"
  [{:keys [data]} dbs]
  (let [{:keys [word-id]} data]
    (p/let [word-doc (dbs/get dbs "vocab" word-id)]
      (if-not word-doc
        (do
          (log/warn :example-fetch/word-not-found {:word-id word-id})
          ;; Word deleted, consider task done
          true)

        (p/catch
          (p/let [example (fetch-one (:value word-doc)
                                     (primary-translation word-doc))]
            (save-example! dbs word-id (:value word-doc) example)
            true)

          (fn [err]
            (log/warn :example-fetch/failed {:word-id word-id :error (ex-message err)})
            (if-let [retry-after-ms (:retry-after-ms (ex-data err))]
              {:retry-after-ms retry-after-ms}
              false)))))))

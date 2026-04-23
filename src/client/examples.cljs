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


(defn source
  "Missing source is legacy AI."
  [example]
  (or (:source example) "ai"))


(defn ai-example?
  [example]
  (= "ai" (source example)))


(defn user-example?
  [example]
  (= "user" (source example)))


(defn- structure-item?
  [item]
  (and (utils/non-blank (:usedForm item))
       (utils/non-blank (:dictionaryForm item))
       (utils/non-blank (:translation item))
       (int? (:wordIndex item))))


(defn valid-structure?
  [structure]
  (and (vector? structure)
       (seq structure)
       (every? structure-item? structure)))


(defn usable?
  [example]
  (and (utils/non-blank (:value example))
       (utils/non-blank (:translation example))
       (case (source example)
         "ai"   (valid-structure? (:structure example))
         "user" true
         false)))


(defn- valid-generated-example?
  [example]
  (and (utils/non-blank (:value example))
       (utils/non-blank (:translation example))
       (valid-structure? (:structure example))))


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


(defn fetch-one
  "Fetches an example sentence for the given German word from the backend.
   `translations` is a vector of Russian glosses the user has confirmed; all
   are sent as repeated `translation` query params so the backend prompt can
   weigh every sense. Returns a promise resolving to the example map.
   Rejects on network or server errors."
  ([word]
   (fetch-one word []))
  ([word translations]
   (let [url (->> translations
                  (filter utils/non-blank)
                  (map #(str "&translation=" (js/encodeURIComponent %)))
                  (reduce str (str "/api/examples?word=" (js/encodeURIComponent word))))]
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
               (if (valid-generated-example? example)
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
  (when-not (valid-generated-example? example)
    (throw (ex-info "Invalid example: missing required fields"
                    {:word-id word-id :example example})))
  (let [now-iso     (utils/now-iso)
        example-doc {:type        "example"
                     :word-id     word-id
                     :word        word
                     :value       (:value example)
                     :translation (:translation example)
                     :source      "ai"
                     :structure   (:structure example)
                     :created-at  now-iso
                     :modified-at now-iso}]
    (dbs/insert dbs example-doc)))


(defn find
  "Retrieves the example document for a given word-id, or nil if none exists."
  [dbs word-id]
  (p/let [{examples :docs} (dbs/find dbs {:selector {:type "example" :word-id word-id}})]
    (first examples)))


(defn list
  "Retrieves example documents for the given word-ids."
  [dbs word-ids]
  (if (empty? word-ids)
    (p/resolved [])
    (p/let [{examples :docs} (dbs/find dbs {:selector {:type "example" :word-id {:$in word-ids}}})]
      examples)))


(defn- list-fetch-tasks
  [dbs]
  (p/let [{tasks :docs} (dbs/find-all dbs {:selector {:type      "task"
                                                      :task-type "example-fetch"}})]
    tasks))


(defn- task-word-id
  [task]
  (get-in task [:data :word-id]))


(defn- failed-task?
  [task]
  (= "failed" (:status task)))


(defn- active-task?
  [task]
  (not (failed-task? task)))


(defn- state-for
  [word-id examples tasks]
  (let [word-examples (filter #(= word-id (:word-id %)) examples)
        word-tasks    (filter #(= word-id (task-word-id %)) tasks)]
    (cond
      (some usable? word-examples)
      {:state :ready}

      (some active-task? word-tasks)
      {:state :generating}

      (some failed-task? word-tasks)
      {:state      :failed
       :last-error (:last-error (first (filter failed-task? word-tasks)))}

      :else
      {:state :no-example})))


(defn states
  "Returns example state by word id: ready, generating, failed, or no-example."
  [dbs word-ids]
  (p/let [examples (list dbs word-ids)
          tasks    (list-fetch-tasks dbs)]
    (into {}
          (map (fn [word-id]
                 [word-id (state-for word-id examples tasks)]))
          word-ids)))


(defn remove!
  "Deletes an example document by its _id. No-op if document doesn't exist."
  [dbs example-id]
  (p/let [example (dbs/get dbs "example" example-id)]
    (when example
      (dbs/remove dbs example))))


(defn fetch!
  "Schedule an example fetch for the given word-id. Idempotent per word."
  [dbs word-id]
  (tasks/ensure! dbs "example-fetch" {:word-id word-id}))


(defn- fetch-and-save!
  [dbs word-id word-doc]
  (p/let [example (fetch-one (:value word-doc)
                             (russian-translations word-doc))]
    (save-example! dbs word-id (:value word-doc) example)
    true))


(defn- fetch-failure-retry
  [word-id err]
  (log/warn :example-fetch/failed {:word-id word-id :error (ex-message err)})
  (tasks/retry
   (cond-> {:last-error (ex-message err)}
     (:retry-after-ms (ex-data err))
     (assoc :retry-after-ms (:retry-after-ms (ex-data err))))))


(defmethod tasks/execute-task "example-fetch"
  [{:keys [data]} dbs]
  (let [{:keys [word-id]} data]
    (p/let [word-doc (dbs/get dbs "vocab" word-id)]
      (if-not word-doc
        (do
          (log/warn :example-fetch/word-not-found {:word-id word-id})
          true)
        (p/catch
          (fetch-and-save! dbs word-id word-doc)
          #(fetch-failure-retry word-id %))))))

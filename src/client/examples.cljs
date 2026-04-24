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
  "Missing source means fetched/non-user example."
  [example]
  (:source example "fetched"))


(defn user-example?
  [example]
  (= "user" (source example)))


(defn fetched-example?
  [example]
  (= "fetched" (source example)))


(defn- structure-item?
  [item]
  (and (utils/non-blank (:usedForm item))
       (utils/non-blank (:dictionaryForm item))
       (utils/non-blank (:translation item))
       (int? (:wordIndex item))))


(defn valid-structure?
  [structure]
  (and
   (vector? structure)
   (seq structure)
   (every? structure-item? structure)))


(defn usable?
  [example]
  (and
   (utils/non-blank (:value example))
   (utils/non-blank (:translation example))
   (case (source example)
     "fetched" (valid-structure? (:structure example))
     "user"    true
     false)))


(defn usable-fetched?
  [example]
  (and (fetched-example? example)
       (usable? example)))


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
   are sent as repeated `translation` query params so the example source can
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
         ;; The client is a tolerant reader: parse JSON here, then decide at
         ;; render time whether this client knows enough fields to use it.
         (p/let [json (-> (.json response)
                          (p/catch (fn [_error] ::invalid-json)))]
           (if (= ::invalid-json json)
             (p/rejected
              (ex-info invalid-response-message
                       {:word word
                        :status 502
                        :error-kind :invalid-json}))
             (js->clj json :keywordize-keys true)))
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
   `example` - map from the backend

   Returns a promise. Preserves unknown backend fields for future clients."
  [dbs word-id word example]
  (when-not (map? example)
    (throw (ex-info "Invalid example: expected map"
                    {:word-id word-id :example example})))
  (let [now-iso     (utils/now-iso)
        example-doc (merge example
                           {:type        "example"
                            :word-id     word-id
                            :word        word
                            :source      "fetched"
                            :created-at  now-iso
                            :modified-at now-iso})]
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
      {:state :fetching}

      (some failed-task? word-tasks)
      {:state      :failed
       :last-error (:last-error (first (filter failed-task? word-tasks)))}

      :else
      {:state :no-example})))


(defn states
  "Returns example state by word id: {word-id state}."
  [dbs word-ids]
  (p/let [examples (list dbs word-ids)
          tasks    (list-fetch-tasks dbs)]
    (into
     {}
     (for [word-id word-ids]
       [word-id (state-for word-id examples tasks)]))))


(defn state
  "Returns example state for one word id:
   {:state :ready|:fetching|:failed|:no-example, :last-error ...}."
  [dbs word-id]
  (p/let [states (states dbs [word-id])]
    (get states word-id)))


(defn fields-valid?
  [value translation]
  (and (utils/non-blank value)
       (utils/non-blank translation)))


(defn add!
  "Add a user-owned example to a word. User examples are saved as entered
   and intentionally have no sentence structure."
  [dbs word-id value translation]
  (if-not (fields-valid? value translation)
    (p/resolved {:error :invalid})
    (p/let [word-doc (dbs/get dbs "vocab" word-id)]
      (if word-doc
        (let [now-iso (utils/now-iso)
              doc     {:type        "example"
                       :word-id     word-id
                       :word        (:value word-doc)
                       :value       value
                       :translation translation
                       :source      "user"
                       :created-at  now-iso
                       :modified-at now-iso}]
          (p/let [{:keys [id rev]} (dbs/insert dbs doc)]
            (assoc doc :_id id :_rev rev)))
        {:error :not-found}))))


(defn update!
  "Update a user-owned example. Fetched examples are not editable here."
  [dbs example-id value translation]
  (if-not (fields-valid? value translation)
    (p/resolved {:error :invalid})
    (p/let [example (dbs/get dbs "example" example-id)]
      (cond
        (nil? example)
        {:error :not-found}

        (not (user-example? example))
        {:error :not-user-example}

        :else
        (let [updated (assoc example
                             :value value
                             :translation translation
                             :modified-at (utils/now-iso))]
          (p/let [{:keys [rev]} (dbs/insert dbs updated)]
            (assoc updated :_rev rev)))))))


(defn delete!
  "Deletes an example document by its _id. No replacement work is created."
  [dbs example-id]
  (p/let [example (dbs/get dbs "example" example-id)]
    (if example
      (p/let [_ (dbs/remove dbs example)]
        {:deleted? true :word-id (:word-id example)})
      {:error :not-found})))


(defn refetch!
  "Deletes a fetched example and schedules replacement work for its word."
  [dbs example-id]
  (p/let [example (dbs/get dbs "example" example-id)]
    (cond
      (nil? example)
      {:error :not-found}

      (user-example? example)
      {:error :user-example}

      :else
      (p/do
        (dbs/remove dbs example)
        (tasks/retry! dbs "example-fetch" {:word-id (:word-id example)})
        {:word-id (:word-id example)}))))


(defn remove!
  "Deletes an example document by its _id. No-op if document doesn't exist."
  [dbs example-id]
  (p/let [example (dbs/get dbs "example" example-id)]
    (when example
      (dbs/remove dbs example))))


(defn fetch!
  "Fetch an example for the word.
   If fetched example is ready, no-op. Otherwise creates/reuses work or retries failed work."
  [dbs word-id]
  (p/let [word-examples (list dbs [word-id])]
    (if (some usable-fetched? word-examples)
      {:state :ready}
      (p/do
        (tasks/retry! dbs "example-fetch" {:word-id word-id})
        {:state :fetching}))))


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

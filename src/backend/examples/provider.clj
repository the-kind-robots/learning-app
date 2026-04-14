(ns examples.provider
  (:require
   [cheshire.core :as cheshire]
   [clojure.string :as str]
   [org.httpkit.client :as client]))


(defn- env
  ([name]
   (env name nil))
  ([name default]
   (or (System/getenv name) default)))


(defn- retry-after-from-header
  "Parses retry delay from the standard Retry-After response header."
  [response]
  (when-let [v (get-in response [:headers "retry-after"])]
    (some-> v parse-double (* 1000) Math/ceil long)))


(defn- models-from-env
  []
  (some-> (env "OPENROUTER_MODELS")
          (str/split #"\s*,\s*")
          (->> (remove str/blank?))
          seq
          vec))


(defn config
  "OpenRouter request configuration assembled from env."
  []
  {:api-url        (env "OPENROUTER_API_URL" "https://openrouter.ai/api/v1/chat/completions")
   :api-key        (env "OPENROUTER_API_KEY")
   :model          (env "OPENROUTER_MODEL" "google/gemini-2.5-flash-lite")
   :models         (models-from-env)
   :provider-prefs {:require_parameters true
                    :sort               {:by "price" :partition "none"}
                    :data_collection    "deny"}
   :retry-after-ms retry-after-from-header})


(defn request-options
  [payload timeout-ms]
  (let [{:keys [api-url api-key model models provider-prefs]} (config)
        body (cond-> (assoc payload :model model)
               (seq models)         (assoc :models models)
               (seq provider-prefs) (assoc :provider provider-prefs))]
    {:url     api-url
     :method  :post
     :headers {"Authorization" (str "Bearer " api-key)
               "Content-Type"  "application/json"}
     :timeout timeout-ms
     :body    (cheshire/generate-string body)}))


(defn request
  [payload timeout-ms]
  (client/request (request-options payload timeout-ms)))

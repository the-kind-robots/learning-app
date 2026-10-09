(ns examples.provider
  (:require
   [cheshire.core :as cheshire]
   [clojure.string :as str]
   [org.httpkit.client :as client])
  (:import
   [java.time Duration Instant ZonedDateTime]
   [java.time.format DateTimeFormatter DateTimeFormatterBuilder DateTimeParseException]
   [java.time.temporal ChronoField]
   [java.util Locale]))


(set! *warn-on-reflection* true)


(defn- env
  ([name]
   (env name nil))
  ([name default]
   (or (System/getenv name) default)))


(def ^:private longest-retry-after-ms
  "The longest delay a Retry-After header is taken at."
  (* 60 60 1000))


(def ^:private http-date-formats
  "The three HTTP-date forms a recipient must read under RFC 9110."
  [DateTimeFormatter/RFC_1123_DATE_TIME
   (-> (DateTimeFormatterBuilder.)
       (.appendPattern "EEEE, dd-MMM-")
       (.appendValueReduced ChronoField/YEAR 2 2 1970)
       (.appendPattern " HH:mm:ss zzz")
       (.toFormatter Locale/US))
   (.withZone (DateTimeFormatter/ofPattern "EEE MMM ppd HH:mm:ss yyyy" Locale/US)
              java.time.ZoneOffset/UTC)])


(defn- http-date-instant
  "The instant an HTTP-date names, or nil when `text` is no HTTP-date."
  [^String text]
  (some
   (fn [format]
     (try
       (.toInstant (ZonedDateTime/parse text format))
       (catch DateTimeParseException _
         nil)))
   http-date-formats))


(defn- delay-seconds-ms
  "The delta-seconds delay in `text`, in milliseconds, or nil when it is not a
   number of seconds."
  [text]
  (when-let [seconds (parse-double text)]
    (when (and (Double/isFinite seconds) (<= 0.0 seconds))
      (long (Math/ceil (* 1000.0 (min seconds (/ longest-retry-after-ms 1000.0))))))))


(defn retry-after-ms
  "The delay the provider's Retry-After header asks for, in milliseconds and
   at most `longest-retry-after-ms`. Nil when the header is missing or
   unreadable; a date in the past is 0."
  ([response]
   (retry-after-ms response (Instant/now)))
  ([response ^Instant now]
   (when-let [text (some-> response :headers :retry-after str str/trim not-empty)]
     (or (delay-seconds-ms text)
         (when-let [at (http-date-instant text)]
           (-> (Duration/between now at)
               (.toMillis)
               (max 0)
               (min longest-retry-after-ms)))))))


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
                    :data_collection    "deny"}})


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

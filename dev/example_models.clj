(ns example-models
  "Dev tool, not production code. Runs a fixed word set through candidate
   models over OpenRouter with the production request body, provider
   preferences, retry loop and validator, and reports per model what an
   example costs and how often the validator takes it.

   Needs OPENROUTER_API_KEY in the environment; nothing here prints or stores
   it. Every request is paid.

     clojure -M:dev -m example-models --dry-run
     clojure -M:dev -m example-models --limit 3
     clojure -M:dev -m example-models
     clojure -M:dev -m example-models --models openai/gpt-5-nano@minimal --raw /tmp/run.edn

   A candidate is `model` or `model@effort`; the second adds
   `reasoning: {effort}` to the request, which production does not send."
  (:require
   [cheshire.core :as cheshire]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [examples :as examples]
   [examples.provider :as provider]
   [taoensso.telemere :as t]))


(def candidates
  "The default candidates. Reasoning models run twice: as production would
   send them, and with the effort turned down so `max_tokens` 300 is not
   spent on thinking."
  ["google/gemini-2.5-flash-lite"
   "google/gemini-3.1-flash-lite"
   "openai/gpt-5-nano"
   "openai/gpt-5-nano@minimal"
   "openai/gpt-4.1-nano"
   "mistralai/mistral-small-3.2-24b-instruct"
   "openai/gpt-oss-120b"
   "openai/gpt-oss-120b@low"])


(def ^:dynamic *candidate*
  "The candidate the current thread is measuring: {:model :effort}."
  nil)


(def ^:dynamic *word*
  "The German target the current thread is generating for."
  nil)


(def ^:dynamic *requests*
  "An atom collecting one record per provider request of the current word."
  nil)


(defn- candidate-spec
  [label]
  (let [[model effort] (str/split label #"@" 2)]
    {:effort effort
     :label  label
     :model  model}))


(defn- parsed-usage
  [body]
  (let [usage (:usage body)]
    {:cost (:cost usage)
     :completion-tokens (:completion_tokens usage)
     :prompt-tokens (:prompt_tokens usage)
     :reasoning-tokens (get-in usage [:completion_tokens_details :reasoning_tokens])}))


(defn- request-record
  "What one provider response says, judged by the production validator."
  [word response latency-ms]
  (let [status (:status response)
        body   (#'examples/parsed-body (:body response))
        choice (when (= 200 status) (#'examples/first-choice body))
        text   (when choice (#'examples/choice-text choice))
        issue  (cond
                 (not= 200 status) :http-error
                 (nil? text)       :no-text
                 :else             (:issue (#'examples/example-issue word (#'examples/candidate text))))]
    (merge (parsed-usage body)
           {:error         (or (some-> (:error response) str)
                               (when-not (= 200 status)
                                 (or (get-in body [:error :message])
                                     (some-> (:body response) str (subs 0 (min 300 (count (str (:body response)))))))))
            :finish-reason (:finish_reason choice)
            :issue         issue
            :latency-ms    latency-ms
            :provider      (:provider body)
            :status        status})))


(defn- measured-request
  "Stands in for `examples.provider/request`: the same call, with the
   candidate's reasoning effort and usage accounting added, and a record of
   the response kept for the report."
  [original]
  (fn [payload timeout-ms]
    (let [{:keys [effort]} *candidate*
          word     *word*
          sink     *requests*
          entry    (atom nil)
          started  (System/nanoTime)
          sent     (cond-> (assoc payload :usage {:include true})
                     effort (assoc :reasoning {:effort effort}))
          response (original sent timeout-ms)]
      (swap! sink conj entry)
      (future
       (let [answer  @response
             elapsed (quot (- (System/nanoTime) started) 1000000)]
         (reset! entry (request-record word answer elapsed))
         answer)))))


(defn- measured-config
  [original]
  (fn []
    (assoc (original)
           :model  (:model *candidate*)
           :models nil)))


(defn- word-result
  "One word through `examples/generate-one!`, with the records of the
   requests it took."
  [candidate {:keys [group word] :as entry}]
  (let [subject   {:context      nil
                   :translations (vec (:translation entry))
                   :word         word}
        word-meta {:cefrLevel    (:cefr-level entry)
                   :partOfSpeech (:part-of-speech entry)}
        requests  (atom [])
        result    (binding [*candidate* candidate
                            *word*      word
                            *requests*  requests]
                    (examples/generate-one! subject word-meta))
        records   (mapv (fn [entry] (or @entry {:issue :unanswered})) @requests)]
    {:accepted? (boolean (examples/valid-example? result))
     :first-attempt-ok? (boolean (and (examples/valid-example? result)
                                      (= 1 (count records))))
     :group     group
     :requests  records
     :word      word}))


(defn- percentile
  [fraction sorted-values]
  (when (seq sorted-values)
    (nth sorted-values
         (min (dec (count sorted-values))
              (long (Math/ceil (* fraction (dec (count sorted-values)))))))))


(defn- share
  [part whole]
  (if (pos? whole)
    (/ (double part) whole)
    0.0))


(defn- summary
  [label results]
  (let [requests  (mapcat :requests results)
        accepted  (count (filter :accepted? results))
        latencies (vec (sort (keep :latency-ms requests)))
        costs     (keep :cost requests)]
    {:accepted       accepted
     :accepted-first (count (filter :first-attempt-ok? results))
     :attempts-total (count requests)
     :cost-total     (when (seq costs) (reduce + costs))
     :errors         (->> requests (keep :error) distinct (take 3))
     :finish-length  (count (filter #(= "length" (:finish-reason %)) requests))
     :issues         (->> requests (keep :issue) frequencies)
     :label          label
     :p50            (percentile 0.5 latencies)
     :p95            (percentile 0.95 latencies)
     :providers      (->> requests (keep :provider) frequencies)
     :reasoning-tokens (->> requests (keep :reasoning-tokens) (reduce + 0))
     :words          (count results)
     :by-group       (->> results
                          (group-by :group)
                          (map (fn [[group rows]]
                                 [group (str (count (filter :accepted? rows)) "/" (count rows))]))
                          (into (sorted-map)))}))


(defn- pct
  [value]
  (format "%.0f%%" (* 100.0 value)))


(defn- print-summary
  [{:keys [accepted accepted-first attempts-total by-group cost-total errors
           finish-length issues label p50 p95 providers reasoning-tokens words]}]
  (println)
  (println label)
  (println (format "  accepted after retries : %d/%d (%s)" accepted words (pct (share accepted words))))
  (println (format "  accepted on 1st attempt: %d/%d (%s)" accepted-first words (pct (share accepted-first words))))
  (println (format "  attempts per accepted  : %s (all attempts / accepted)"
                   (if (pos? accepted) (format "%.2f" (/ (double attempts-total) accepted)) "n/a")))
  (println (format "  latency p50 / p95      : %s / %s ms" (or p50 "n/a") (or p95 "n/a")))
  (println (format "  cost per accepted      : %s"
                   (if (and cost-total (pos? accepted))
                     (format "$%.6f (total $%.5f over %d requests)" (/ cost-total accepted) cost-total attempts-total)
                     "n/a (no usage.cost in responses or nothing accepted)")))
  (println (str "  by group               : " by-group))
  (println (str "  issues per request     : " issues))
  (println (str "  finish_reason=length   : " finish-length "; reasoning tokens: " reasoning-tokens))
  (println (str "  providers              : " providers))
  (when (seq errors)
    (println (str "  sample errors          : " (vec errors)))))


(defn- measured
  "The summary of one candidate over `entries`."
  [label entries]
  (let [candidate (candidate-spec label)
        results   (mapv #(word-result candidate %) entries)]
    {:results results
     :summary (summary label results)}))


(defn- option
  [args flag]
  (second (drop-while #(not= flag %) args)))


(defn- words
  []
  (with-open [reader (java.io.PushbackReader. (io/reader (io/resource "example_models_words.edn")))]
    (edn/read reader)))


(defn -main
  [& args]
  (let [dry-run? (some #{"--dry-run"} args)
        labels   (if-let [csv (option args "--models")]
                   (str/split csv #"\s*,\s*")
                   candidates)
        limit    (some-> (option args "--limit") parse-long)
        raw-path (option args "--raw")
        entries  (cond->> (words)
                   limit (take limit)
                   true  vec)]
    (t/set-min-level! :error)
    (cond
      dry-run?
      (do (println (count entries) "words;" (count labels) "candidates:" labels)
          (println "request body of the first word, as production builds it (messages elided):")
          (println (-> (#'examples/request-body
                        (:word (first entries))
                        (:translation (first entries))
                        nil
                        {:cefrLevel    (:cefr-level (first entries))
                         :partOfSpeech (:part-of-speech (first entries))}
                        nil)
                       (update :messages
                               (fn [messages]
                                 (mapv #(update % :content (fn [c] (str (subs c 0 (min 60 (count c))) "...")))
                                       messages)))
                       (assoc :model "<candidate>" :provider (:provider-prefs (provider/config)))
                       (cheshire/generate-string {:pretty true}))))

      (str/blank? (System/getenv "OPENROUTER_API_KEY"))
      (do (println "OPENROUTER_API_KEY is not set in this environment; nothing was sent.")
          (System/exit 2))

      :else
      (let [original-request provider/request
            original-config provider/config
            runs (with-redefs [provider/request (measured-request original-request)
                               provider/config  (measured-config original-config)]
                   (->> labels
                        (mapv (fn [label] [label (future (measured label entries))]))
                        (mapv (fn [[label run]] [label @run]))))]
        (println (count entries)
                 "words," (count labels)
                 "candidates; production request body and validator;"
                 "one word at a time per candidate, candidates in parallel.")
        (doseq [[_ run] runs]
          (print-summary (:summary run)))
        (when raw-path
          (spit raw-path
                (with-out-str (doseq [[label run] runs]
                                (prn {:label label :results (:results run)})))))))
    (shutdown-agents)
    (System/exit 0)))

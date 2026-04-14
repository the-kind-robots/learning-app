(ns examples
  (:require
   [cheshire.core :as cheshire]
   [clojure.string :as str]
   [examples.dictionary :as dictionary]
   [examples.provider :as provider]
   [malli.core :as m]
   [malli.error :as me]
   [malli.json-schema :as mjs]
   [malli.util :as mu]
   [taoensso.telemere :as t]
   [utils :as utils]))


(defn- example-generation-timeout-ms
  []
  (some-> (or (System/getenv "EXAMPLE_GENERATION_TIMEOUT_MS") "30000")
          parse-long))


(defn valid-example?
  [example]
  (and (map? example)
       (not (str/blank? (:value example)))
       (not (str/blank? (:translation example)))))


(defn- cyrillic-text?
  [text]
  (boolean (re-find #"[А-Яа-яЁё]" (or text ""))))


(defn- latin-text?
  [text]
  (boolean (re-find #"[A-Za-zÄÖÜẞäöüß]" (or text ""))))


(defn- single-sentence-text?
  [text]
  (boolean
   (re-matches #"(?su)^[^.!?]+[.!?]$"
               (str/trim (or text "")))))


(defn- plain-sentence?
  [text]
  (not (re-find #"(?s)[\r\n`#\[\]{}*_:]" (or text ""))))


(defn- german-sentence-text?
  [text]
  (and (single-sentence-text? text)
       (plain-sentence? text)
       ;; German sentences start with a capital letter, optionally preceded by an opening quote/bracket
       (boolean (re-find #"(?u)^[\"'«„(]*[A-ZÄÖÜ]" (str/trim (or text ""))))
       (not (cyrillic-text? text))))


(defn- russian-sentence-text?
  [text]
  (and (single-sentence-text? text)
       (plain-sentence? text)
       ;; Russian sentences start with a capital Cyrillic letter, optionally preceded by an opening quote/bracket
       (boolean (re-find #"(?u)^[\"'«„(]*[А-ЯЁ]" (str/trim (or text ""))))
       (cyrillic-text? text)
       (not (latin-text? text))))


(defn- russian-text?
  [text]
  (and (cyrillic-text? text) (not (latin-text? text))))


(defn- with-constraint
  [schema pred message]
  [:and schema [:fn {:error/message message} pred]])


(def ^:private example-structure-schema
  [:map {:closed true}
   [:value
    {:description "A natural German sentence using the requested word."}
    [:string {:min 1}]]
   [:translation
    {:description "Russian translation of the sentence."}
    [:string {:min 1}]]
   [:glossMismatch
    {:description "Whether the supplied Russian gloss appears mismatched to the German word."}
    :boolean]
   [:structure
    {:description "A list of JSON objects containing the used word form, its dictionary form and its translation."}
    [:vector {:min 1}
     [:map {:closed true}
      [:usedForm
       {:description "The word in its used form."}
       [:string {:min 1}]]
      [:dictionaryForm
       {:description "The dictionary form of the word."}
       [:string {:min 1}]]
      [:translation
       {:description "Russian translation of the word as used in the sentence."}
       [:string {:min 1}]]]]]])


(def ^:private generated-example-schema
  (-> example-structure-schema
      (mu/update :value       with-constraint german-sentence-text?  "German sentence required")
      (mu/update :translation with-constraint russian-sentence-text? "Russian translation required")
      (mu/update-in [:structure 0 :translation] with-constraint russian-text? "Structure translation must be Cyrillic")))


(defn- normalize-text
  [text]
  (some-> text utils/sanitize-text str/lower-case))


(defn- sentence-contains-word?
  [sentence word]
  (let [sentence (normalize-text sentence)
        word     (normalize-text word)]
    (when (and sentence word)
      (boolean
       (re-find
        (re-pattern
         (str "(?iu)(^|\\P{L})"
              (java.util.regex.Pattern/quote word)
              "($|\\P{L})"))
        sentence)))))


(defn- sentence-word-count
  [sentence]
  (count (re-seq #"\p{L}+" (or sentence ""))))


(defn- sentence-length-ok?
  [sentence]
  (<= 3 (sentence-word-count sentence) 12))


(defn- structure-matches-sentence?
  [sentence structure]
  (every? #(sentence-contains-word? sentence (:usedForm %)) structure))


(defn- log-generation-failure!
  [data]
  (t/log!
   {:level :warn
    :id    ::generation-failed
    :data  data}
   "Examples generation failed"))


(defn- example-issue
  [word translation example]
  (if-not (m/validate generated-example-schema example)
    (do
      (log-generation-failure!
       {:word    word
        :error   "Invalid generated example shape"
        :explain (me/humanize (m/explain generated-example-schema example))})
      :invalid-generated-example)
    (let [{:keys [value structure]} example]
      (cond
        (not (structure-matches-sentence? value structure))
        :structure-value-mismatch

        (not (sentence-length-ok? value))
        :sentence-length-out-of-range

        (not (dictionary/lemma-in-structure? word structure))
        :target-dictionary-form-missing

        (not (dictionary/word-gloss-valid? word translation structure))
        :target-dictionary-form-gloss-mismatch))))



(defn- retry-after-ms
  [response]
  (when-let [f (:retry-after-ms (provider/config))]
    (f response)))


(defn generation-failure?
  [result]
  (= ::generation-failure (::type result)))


(def system-prompt
  (str/join
   "\n"
   ["You generate learner-facing German example sentences for a vocabulary app."
    "Return only JSON that matches the supplied schema."
    "Input fields: word, translation, part of speech, cefrLevel, previousAttempt, previousIssues."
    "Missing cefrLevel => B1."
    "- Match the intended Russian gloss exactly. Do not switch senses."
    "- Produce one natural standard German sentence, 3-12 words, using the target lemma or a correct inflected form."
    "- German sentence only: no labels, notes, markdown, or meta commentary."
    "- `translation` must be one natural Russian sentence, not a calque."
    "- Set `glossMismatch` to true only if the supplied gloss clearly mismatches the target lemma; otherwise false."
    "- `structure` must include every noun, verb (including auxiliaries and modals), adjective, and adverb in the sentence."
    "- Each item in `structure` must be a JSON object with keys `usedForm`, `dictionaryForm`, and `translation`."
    "- Never use arrays like `[\"Fenster\", \"das Fenster\", \"окно\"]` inside `structure`."
    "- Exclude articles, pronouns, prepositions, conjunctions, and pure particles like `zu` or `nicht`."
    "- Detached prefixes of separable verbs are not particles: include them."
    "- For nouns, `dictionaryForm` includes the article."
    "- For separable verbs with detached prefixes, include one item for the verb part and one for the prefix; both use the full infinitive as `dictionaryForm` and the same Russian gloss."
    "- Reflexive verb `dictionaryForm` keeps `sich`."
    "- Ambiguous noun articles and ambiguous prefixes (`um-`, `über-`, `unter-`, `durch-`, `wieder-`) must follow the supplied Russian gloss."
    "- Example structure item: `{\"usedForm\":\"Fenster\",\"dictionaryForm\":\"das Fenster\",\"translation\":\"окно\"}`."
    "Example word=aufstehen gloss=вставать:"
    "{\"value\":\"Ich stehe jeden Morgen um sieben Uhr auf.\",\"translation\":\"Я встаю каждое утро в семь часов.\",\"glossMismatch\":false,\"structure\":[{\"usedForm\":\"stehe\",\"dictionaryForm\":\"aufstehen\",\"translation\":\"вставать\"},{\"usedForm\":\"Morgen\",\"dictionaryForm\":\"der Morgen\",\"translation\":\"утро\"},{\"usedForm\":\"auf\",\"dictionaryForm\":\"aufstehen\",\"translation\":\"вставать\"}]}"
    "Example word=das Verstehen gloss=понимание:"
    "{\"value\":\"Das Verstehen dieser Regel dauert lange.\",\"translation\":\"Понимание этого правила требует времени.\",\"glossMismatch\":false,\"structure\":[{\"usedForm\":\"Verstehen\",\"dictionaryForm\":\"das Verstehen\",\"translation\":\"понимание\"},{\"usedForm\":\"Regel\",\"dictionaryForm\":\"die Regel\",\"translation\":\"правило\"},{\"usedForm\":\"dauert\",\"dictionaryForm\":\"dauern\",\"translation\":\"длиться\"}]}"
    "Example word=die Bank gloss=скамейка:"
    "{\"value\":\"Wir sitzen auf einer Bank im Park.\",\"translation\":\"Мы сидим на скамейке в парке.\",\"glossMismatch\":false,\"structure\":[{\"usedForm\":\"sitzen\",\"dictionaryForm\":\"sitzen\",\"translation\":\"сидеть\"},{\"usedForm\":\"Bank\",\"dictionaryForm\":\"die Bank\",\"translation\":\"скамейка\"},{\"usedForm\":\"Park\",\"dictionaryForm\":\"der Park\",\"translation\":\"парк\"}]}"
    "Example word=Leiter gloss=лестница:"
    "{\"value\":\"Die Leiter steht neben der Wand.\",\"translation\":\"Лестница стоит у стены.\",\"glossMismatch\":false,\"structure\":[{\"usedForm\":\"Leiter\",\"dictionaryForm\":\"die Leiter\",\"translation\":\"лестница\"},{\"usedForm\":\"steht\",\"dictionaryForm\":\"stehen\",\"translation\":\"стоять\"},{\"usedForm\":\"Wand\",\"dictionaryForm\":\"die Wand\",\"translation\":\"стена\"}]}"
    "Do not use `der Leiter` for this meaning."
    "Example word=sich vorstellen gloss=представляться:"
    "{\"value\":\"Er stellt sich bei den neuen Kollegen vor.\",\"translation\":\"Он представляется новым коллегам.\",\"glossMismatch\":false,\"structure\":[{\"usedForm\":\"stellt\",\"dictionaryForm\":\"sich vorstellen\",\"translation\":\"представляться\"},{\"usedForm\":\"neu\",\"dictionaryForm\":\"neu\",\"translation\":\"новый\"},{\"usedForm\":\"Kollegen\",\"dictionaryForm\":\"der Kollege\",\"translation\":\"коллега\"},{\"usedForm\":\"vor\",\"dictionaryForm\":\"sich vorstellen\",\"translation\":\"представляться\"}]}"]))



(defn- user-prompt
  [word translation word-meta retry-context]
  (str/join
   "\n"
   ["Generate one example."
    "Return one JSON object matching the supplied schema."
    "If `previousAttempt` and `previousIssues` are present, fix those problems without changing the intended sense."
    (cheshire/generate-string
     (merge
      {:word         word
       :partOfSpeech (:partOfSpeech word-meta)
       :cefrLevel    (:cefrLevel word-meta)
       :translation  translation}
      (when retry-context
        {:previousAttempt (:example retry-context)
         :previousIssues  [(name (:issue retry-context))]})))]))


(defn- request-body
  [word translation word-meta retry-context]
  {:messages        [{:role    "system"
                      :content system-prompt}
                     {:role    "user"
                      :content (user-prompt word translation word-meta retry-context)}]
   :response_format {:type        "json_schema"
                     :json_schema {:name   "sentence_example"
                                   :schema (mjs/transform example-structure-schema)
                                   :strict true}}})



(defn example-api-request
  [word translation word-meta retry-context]
  (provider/request
   (request-body word translation word-meta retry-context)
   (example-generation-timeout-ms)))


(defn- parse-generated-example
  [response word]
  (if (= 200 (:status response))
    (try
      (-> response :body (cheshire/parse-string true) :choices first :message :content (cheshire/parse-string true))
      (catch Exception error
        (let [failure {::type      ::generation-failure
                       :retryable? true
                       :word       word
                       :error      (.getMessage error)
                       :body       (:body response)}]
          (log-generation-failure! failure)
          failure)))
    (let [hard?   (contains? #{401 403 429} (:status response))
          failure (merge
                   (select-keys response [:status :error :body])
                   {::type          ::generation-failure
                    :retryable?     (not hard?)
                    :word           word
                    :retry-after-ms (retry-after-ms response)})]
      (log-generation-failure! failure)
      failure)))


(defn- generate-attempt!
  [word translation word-meta retry-context]
  (try
    (-> @(example-api-request word translation word-meta retry-context)
        (parse-generated-example word))
    (catch Exception error
      (let [failure {::type      ::generation-failure
                     :retryable? true
                     :word       word
                     :error      (.getMessage error)
                     :context    :transport
                     :cause      (some-> error ex-data)}]
        (log-generation-failure! failure)
        failure))))


(defn generate-one!
  "Generates a German example sentence for word/translation.
  Returns a map with keyword keys:
  * :value — German text;
  * :translation — Russian translation;
  * :glossMismatch — true if the supplied gloss appears mismatched to the word;
  * :structure — list of maps with keyword keys :usedForm, :dictionaryForm, :translation.
  Returns a generation-failure map on a hard error (e.g. 429), nil when all attempts are exhausted."
  ([input]
   (generate-one! input 3))
  ([{:keys [word translation]} max-attempts]
   (let [word-meta (dictionary/lookup-word-meta word translation)]
     (loop [attempt 1 retry-context nil]
       (let [example  (generate-attempt! word translation word-meta retry-context)
             failure? (generation-failure? example)
             issue    (when-not failure?
                        (example-issue word translation example))]
         (cond
           (and failure? (not (:retryable? example)))
           example

           (and (not failure?) (nil? issue))
           example

           (< attempt max-attempts)
           (let [retry-ctx (when-not failure? {:example example :issue issue})]
             (when retry-ctx
               (log-generation-failure!
                {:words   [word]
                 :attempt attempt
                 :error   "Rejected generated example candidate"
                 :issue   issue
                 :example example}))
             (recur (inc attempt) retry-ctx))

           :else
           (do
             (when-not failure?
               (log-generation-failure!
                {:words   [word]
                 :attempt attempt
                 :error   "Exhausted example generation attempts"
                 :issue   issue
                 :example example}))
             nil)))))))


(comment
  (generate-one! {:word "das Entsetzen"}))

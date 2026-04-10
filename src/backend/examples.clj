(ns examples
  (:require
   [cheshire.core :as cheshire]
   [clojure.string :as str]
   [db]
   [org.httpkit.client :as client]
   [taoensso.telemere :as t]
   [utils :as utils]))


(defn- env
  [name]
  (System/getenv name))


(defn example-generation-api-key
  []
  ;; Prefer the new OpenRouter-specific variable, but keep the old one as
  ;; a rollout fallback so local/prod envs do not break mid-migration.
  (or (env "OPENROUTER_API_KEY")
      (env "OPENAI_API_KEY")))


(defn example-generation-api-url
  []
  (or (env "EXAMPLE_GENERATION_API_URL")
      "https://openrouter.ai/api/v1/chat/completions"))


(defn example-generation-model
  []
  (or (env "EXAMPLE_GENERATION_MODEL")
      "nvidia/nemotron-3-super-120b-a12b:free"))


(defn- example-generation-timeout-ms
  []
  (some-> (or (env "EXAMPLE_GENERATION_TIMEOUT_MS") "30000")
          parse-long))


(defn valid-example?
  [example]
  (and (map? example)
       (not (str/blank? (some-> example (get "value"))))
       (not (str/blank? (some-> example (get "translation"))))))


(defn- cyrillic-text?
  [text]
  (boolean (re-find #"[А-Яа-яЁё]" (or text ""))))


(defn- suspicious-meta-text?
  [text]
  (boolean
   (re-find
    #"(?i)(the example for|requested json|dictionary form|json structure|translation is straightforward|i[' ]?ll directly provide|without extra text)"
    (or text ""))))


(defn- valid-structure-item?
  [item]
  (and (map? item)
       (not (str/blank? (some-> item (get "usedForm"))))
       (not (str/blank? (some-> item (get "dictionaryForm"))))
       (not (str/blank? (some-> item (get "translation"))))
       (cyrillic-text? (get item "translation"))))


(defn- valid-example-structure?
  [structure]
  (and (sequential? structure)
       (seq structure)
       (every? valid-structure-item? structure)))


(defn- valid-generated-example?
  [example]
  (and (valid-example? example)
       (instance? Boolean (get example "glossMismatch"))
       (not (suspicious-meta-text? (get example "value")))
       (not (suspicious-meta-text? (get example "translation")))
       (cyrillic-text? (get example "translation"))
       (valid-example-structure? (get example "structure"))))


(defn- sentence-word-count
  [sentence]
  (count (re-seq #"\p{L}+" (or sentence ""))))


(defn- target-has-article?
  [word]
  (boolean (re-matches #"(?iu)(der|die|das)\s+.+"
                       (or word ""))))


(defn- normalize-dictionary-form
  [text]
  (some-> text str/trim str/lower-case))


(defn- normalize-russian-text
  [text]
  (some-> text
          utils/sanitize-text
          str/lower-case
          utils/non-blank))


(defn- strip-article
  [text]
  (some-> text
          str/trim
          (str/replace #"(?iu)^(der|die|das)\s+" "")))


(defn- target-dictionary-form?
  [word dictionary-form]
  (let [target          (normalize-dictionary-form word)
        dictionary-form (normalize-dictionary-form dictionary-form)]
    (when (and target dictionary-form)
      (if (target-has-article? word)
        (= dictionary-form target)
        (= (strip-article dictionary-form)
           (strip-article target))))))


(defn- target-dictionary-form-present?
  [word structure]
  (boolean
   (some #(target-dictionary-form? word (get % "dictionaryForm"))
         structure)))


(defn- target-dictionary-forms
  [word structure]
  (->> structure
       (keep #(get % "dictionaryForm"))
       (filter #(target-dictionary-form? word %))
       distinct))


(defn- normalize-translation-variant
  [text]
  (some-> text str/trim normalize-russian-text))


(defn- translation-variants
  [text]
  (->> (str/split (or text "") #"[;,/]")
       (map normalize-translation-variant)
       (remove nil?)
       distinct))


(defn- translation-match?
  [expected actual]
  (let [expected* (normalize-russian-text expected)
        actual*   (normalize-russian-text actual)
        actuals   (set (translation-variants actual))]
    (boolean
     (or (= expected* actual*)
         (contains? actuals expected*)))))


(def ^:private dictionary-db-name
  "dictionary-db")


(defn- log-dictionary-validation-failure!
  [data]
  (t/log!
   {:level :warn
    :id    ::dictionary-validation-failed
    :data  data}
   "Examples dictionary validation failed"))


(defn- lookup-dictionary-entries
  [dictionary-form]
  (try
    (let [response (db/request-sync
                    {:method :get
                     :url    (str dictionary-db-name "/_find")
                     :body   {:selector {:type  "dictionary-entry"
                                         :value dictionary-form}
                              :fields   [:_id :value :pos :translation]
                              :limit    10}})]
      (if (< (:status response 500) 400)
        (get-in response [:body :docs] [])
        (do
          (log-dictionary-validation-failure!
           {:dictionary-form dictionary-form
            :status          (:status response)
            :body            (:body response)})
          nil)))
    (catch Exception error
      (log-dictionary-validation-failure!
       {:dictionary-form dictionary-form
        :error           (.getMessage error)})
      nil)))


(defn- dictionary-entry-matches-gloss?
  [entry translation]
  (some #(and (= "ru" (:lang %))
              (translation-match? translation (:value %)))
        (:translation entry)))


(defn- dictionary-form-matches-target-gloss?
  [dictionary-form translation]
  (let [entries (lookup-dictionary-entries dictionary-form)]
    ;; If dictionary lookup itself is unavailable, do not turn that outage into a
    ;; hard example-generation failure. Only reject when the lookup succeeds and
    ;; the returned entries disagree with the intended gloss.
    (or (nil? entries)
        (some #(dictionary-entry-matches-gloss? % translation)
              entries))))


(defn- target-dictionary-entry-valid?
  [word translation structure]
  (or (str/blank? translation)
      (some (fn [dictionary-form]
              (dictionary-form-matches-target-gloss? dictionary-form translation))
            (target-dictionary-forms word structure))))


(defn- deterministic-example-issues
  [word translation example]
  (cond-> []
    (not (valid-generated-example? example))
    (conj :invalid-generated-example)

    (true? (get example "glossMismatch"))
    (conj :gloss-mismatch)

    (not (<= 5 (sentence-word-count (get example "value")) 12))
    (conj :sentence-length-out-of-range)

    (not (target-dictionary-form-present? word (get example "structure")))
    (conj :target-dictionary-form-missing)

    (and (target-dictionary-form-present? word (get example "structure"))
         (not (target-dictionary-entry-valid? word
                                              translation
                                              (get example "structure"))))
    (conj :target-dictionary-form-gloss-mismatch)))








(defn- log-generation-failure!
  [data]
  (t/log!
   {:level :warn
    :id    ::generation-failed
    :data  data}
   "Examples generation failed"))


(def system-prompt
  (str/join
   "\n"
   ["You generate learner-facing German example sentences for a vocabulary app."
    "Return only JSON that matches the supplied schema."
    ""
    "For each requested item you receive a German word, its part of speech, and its intended Russian meaning."
    ""
    "## Core rules"
    "- The generated example must match the intended Russian meaning exactly."
    "- Do not switch to another sense of the word, even if the German word is polysemous."
    "- Produce exactly one natural German sentence that uses the target word or a correctly inflected form of it."
    "- Keep sentences between 5 and 12 words."
    "- Prefer clear, standard, pedagogically useful German. Avoid literary, archaic, or overly colloquial phrasing."
    "- The sentence must be plain German only — no explanations, notes, or meta commentary."
    ""
    "## Difficulty"
    "- If a target CEFR level is provided, match vocabulary and grammar to that level."
    "- A1–A2: simple main clauses, common vocabulary, Präsens or Perfekt."
    "- B1–B2: subordinate clauses allowed, wider vocabulary, all common tenses."
    "- C1: complex structures allowed, precise or specialized vocabulary."
    "- If no level is provided, default to B1."
    ""
    "## Part-of-speech guidance"
    ""
    "Nouns:"
    "- Use a natural case and number form that fits the sentence."
    ""
    "Verbs:"
    "- Use a natural finite, infinitive, imperative, or participial form."
    "- Separable-prefix verbs: preserve the correct lemma. The prefix may be detached or attached depending on clause type."
    "- Inseparable-prefix verbs (`ver-`, `be-`, `er-`, `ent-`, `emp-`, `zer-`, `miss-`, `ge-` when inseparable): never detach the prefix."
    "- Some prefixes (`um-`, `über-`, `unter-`, `durch-`, `wieder-`) can be separable or inseparable depending on meaning. Use the supplied Russian gloss to determine which variant is intended."
    "- Reflexive verbs (`sich ...`): always include the reflexive pronoun in the sentence."
    "- Do not invent a separable prefix that does not belong to the target lemma."
    ""
    "Adjectives:"
    "- Use correct agreement and declension (strong / weak / mixed) matching the determiner context."
    ""
    "## Translation rules"
    "- `translation` is a natural Russian translation of the whole German sentence."
    "- It must sound like normal Russian, not a word-for-word calque."
    ""
    "## Structure rules"
    "- `structure` must include every noun, verb (including auxiliaries and modals), adjective, and adverb in the sentence."
    "- Exclude: articles, pronouns, prepositions, conjunctions, and pure grammatical particles (e.g. `zu` before infinitives, `nicht`)."
    "- Exception: detached prefixes of separable verbs are NOT particles — always include them."
    "- For nouns, `dictionaryForm` must include the article: `der Hund`, `die Katze`, `das Buch`."
    "- For a separable verb whose prefix is detached, include two entries: one for the finite verb part and one for the detached prefix. Both must share the same `dictionaryForm` (the full infinitive) and the same Russian meaning."
    ""
    "## Gloss mismatch"
    "- If you believe the supplied Russian meaning is incorrect or does not match any attested sense of the German word, set `glossMismatch` to true and generate the sentence to the best of your ability anyway."
    "- Otherwise set `glossMismatch` to false."
    ""
    "## Few-shot examples"
    ""
    "Target: `aufstehen`, meaning `вставать`."
    "Sentence: `Ich stehe jeden Morgen um sieben Uhr auf.`"
    "Structure:"
    "  * `stehe` → dictionaryForm `aufstehen` → `вставать`"
    "  * `Morgen` → dictionaryForm `der Morgen` → `утро`"
    "  * `auf` → dictionaryForm `aufstehen` → `вставать`"
    ""
    "Target: `verstehen`, meaning `понимать`."
    "Sentence: `Ich verstehe diese Aufgabe nicht.`"
    "Structure:"
    "  * `verstehe` → dictionaryForm `verstehen` → `понимать`"
    "  * `Aufgabe` → dictionaryForm `die Aufgabe` → `задача`"
    "(Note: `nicht` is excluded as a function word.)"
    ""
    "Target: `das Verstehen`, meaning `понимание`."
    "Sentence: `Das Verstehen dieser Regel dauert lange.`"
    "Structure:"
    "  * `Verstehen` → dictionaryForm `das Verstehen` → `понимание`"
    "  * `Regel` → dictionaryForm `die Regel` → `правило`"
    "  * `dauert` → dictionaryForm `dauern` → `длиться`"
    ""
    "Target: `die Bank`, meaning `скамейка` (not `банк`)."
    "Sentence: `Wir sitzen auf einer Bank im Park.`"
    "Structure:"
    "  * `sitzen` → dictionaryForm `sitzen` → `сидеть`"
    "  * `Bank` → dictionaryForm `die Bank` → `скамейка`"
    "  * `Park` → dictionaryForm `der Park` → `парк`"
    ""
    "Target: `sich vorstellen`, meaning `представляться`."
    "Sentence: `Er stellt sich bei den neuen Kollegen vor.`"
    "Structure:"
    "  * `stellt` → dictionaryForm `sich vorstellen` → `представляться`"
    "  * `neu` → dictionaryForm `neu` → `новый`"
    "  * `Kollegen` → dictionaryForm `der Kollege` → `коллега`"
    "  * `vor` → dictionaryForm `sich vorstellen` → `представляться`"
    ""
    "## Quality bar"
    "- No markdown, no text outside the JSON object."
    "- If several valid sentences exist, prefer the simplest one that still feels natural."
    "- Never include meta commentary like `The example for...` or `dictionary form`."]))


(defn- example-structure-schema
  []
  {:type "array"
   :description
   "A list of triplets containing the used word form, its dictionary form and its translation."
   :items
   {:type "object"
    :properties
    {:usedForm
     {:type        "string"
      :description "The word in its used form."}

     :dictionaryForm
     {:type        "string"
      :description "The dictionary form of the word."}

     :translation
     {:type        "string"
      :description "Russian translation of the word as used in the sentence."}}

    :additionalProperties false
    :required ["usedForm" "dictionaryForm" "translation"]}})


(defn- example-schema
  [_word]
  {:type "object"
   :properties
   {"value"
    {:type        "string"
     :description "A natural German sentence using the requested word."}

    "translation"
    {:type        "string"
     :description "Russian translation of the sentence."}

    "glossMismatch"
    {:type        "boolean"
     :description "Whether the supplied Russian gloss appears mismatched to the German word."}

    "structure" (example-structure-schema)}

   :additionalProperties false
   :required ["value" "translation" "glossMismatch" "structure"]})


(defn- retry-feedback
  [retry-context]
  (when retry-context
    {:previousAttempt (:example retry-context)
     :previousIssues  (mapv name (:issues retry-context))}))


(defn- request-item
  [word translation retry-context]
  (cond-> {:word         word
           :partOfSpeech nil
           :cefrLevel    nil
           :translation  translation}
    true
    (merge (retry-feedback retry-context))))


(defn- user-prompt
  [word translation retry-context]
  (str/join
   "\n"
   ["Generate one example for this request item."
    "The request item contains the German word and the intended Russian meaning."
    "If the request item also includes `previousAttempt` and `previousIssues`, treat them as feedback about the last rejected candidate."
    "Keep the intended meaning, fix the listed problems, and do not repeat them."
    "The response must be a single JSON object matching the supplied schema."
    (cheshire/generate-string (request-item word translation retry-context))]))


(defn- request-body
  [word translation retry-context]
  {:model    (example-generation-model)
   :messages [{:role    "system"
               :content system-prompt}
              {:role    "user"
               :content (user-prompt word translation retry-context)}]
   :response_format
   {:type "json_schema"

    :json_schema
    {:name "sentence_example"
     :schema (example-schema word)
     :strict true}}
   :top_p    1})



(defn gen-words-api-request
  [word translation retry-context]
  (client/request
   {:url     (example-generation-api-url)
    :method  :post
    :headers {"Authorization" (str "Bearer " (example-generation-api-key))
              "Content-Type"  "application/json"}
    :timeout (example-generation-timeout-ms)
    :body    (cheshire/generate-string
              (request-body word translation retry-context))}))


(defn- parse-generated-examples
  [response word]
  (if (= 200 (:status response))
    (try
      (-> response :body (cheshire/parse-string true) :choices first :message :content cheshire/parse-string)
      (catch Exception error
        (log-generation-failure!
         {:word word
          :status (:status response)
          :error (.getMessage error)
          :body (:body response)})
        nil))
    (do
      (log-generation-failure!
       {:word word
        :status (:status response)
        :error (:error response)
        :body (:body response)})
      nil)))


(defn- raw-generate-one!
  [word translation retry-context]
  (try
    (some-> @(gen-words-api-request word translation retry-context)
            (parse-generated-examples word))
    (catch Exception error
      (log-generation-failure!
       {:word    word
        :error   (.getMessage error)
        :context :transport
        :cause   (some-> error ex-data)})
      nil)))


(defn generate-one!
  "Returns a hash map with the following keys:
  * `:value` — German text;
  * `:translation` — Russian translation;
  * `:structure` — a list of pairs, where each pair has:
    * `:dictionaryForm` — the dictionary form of the word;
    * `:usedForm` — the form of the word used in the sentence;
    * `:translation`— russian translation of the word used in the sentence."
  ([input]
   (generate-one! input 3))
  ([{:keys [word translation]} max-attempts]
   (loop [attempt 1 retry-context nil]
     (let [example (raw-generate-one! word translation retry-context)
           issues  (when example
                     (deterministic-example-issues word translation example))]
       (cond
         (and example (empty? issues))
         example

         (< attempt max-attempts)
         (do
           (when example
             (log-generation-failure!
              {:words [word]
               :attempt attempt
               :error "Rejected generated example candidate"
               :issues issues
               :example example}))
           (recur (inc attempt)
                  {:example example
                   :issues  issues}))

         :else
         (do
           (log-generation-failure!
            {:words [word]
             :attempt attempt
             :error "Exhausted example generation attempts"
             :issues issues
             :example example})
           nil))))))


(comment
  (generate-one! "das Entsetzen"))

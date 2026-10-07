(ns examples
  (:require
   [cheshire.core :as cheshire]
   [clojure.string :as str]
   [examples.cache :as cache]
   [examples.dictionary :as dictionary]
   [examples.provider :as provider]
   [malli.core :as m]
   [malli.error :as me]
   [malli.json-schema :as mjs]
   [malli.util :as mu]
   [single-flight :as single-flight]
   [taoensso.telemere :as t]
   [utils :as utils]))


(def ^:private default-generation-timeout-ms 30000)


(def ^:private max-generation-attempts 3)


(def ^:private proxy-wait-ms
  "How long nginx waits for `/api/examples` (`proxy_read_timeout`), in
   production and in development."
  100000)


(def ^:private attempt-margin-ms
  "Time kept free under the proxy's wait for the dictionary read, the cache
   and the answer."
  5000)


(def ^:private longest-attempt-timeout-ms
  "The longest one attempt may wait, so that every attempt fits under the
   proxy's wait."
  (quot (- proxy-wait-ms attempt-margin-ms) max-generation-attempts))


(defn- generation-timeout-ms-from
  "The per-attempt timeout that the raw EXAMPLE_GENERATION_TIMEOUT_MS `value`
   sets. An unreadable value gives the default and a too-long one gives
   `longest-attempt-timeout-ms`; both are logged."
  [value]
  (let [parsed (some-> value str/trim parse-long)
        asked  (cond
                 (nil? value)
                 default-generation-timeout-ms

                 (and parsed (pos? parsed))
                 parsed

                 :else
                 (do
                   (t/log!
                    {:level :warn
                     :id    ::generation-timeout-unreadable
                     :data  {:value value :default default-generation-timeout-ms}}
                    "EXAMPLE_GENERATION_TIMEOUT_MS is not a positive number of milliseconds; the default applies")
                   default-generation-timeout-ms))]
    (if (<= asked longest-attempt-timeout-ms)
      asked
      (do
        (t/log!
         {:level :warn
          :id    ::generation-timeout-clamped
          :data  {:value asked :clamped-to longest-attempt-timeout-ms}}
         "EXAMPLE_GENERATION_TIMEOUT_MS would outlast the proxy over three attempts; the clamped timeout applies")
        longest-attempt-timeout-ms))))


(def ^:private generation-timeout-ms
  "How long one attempt may wait for the provider, read from the environment
   once."
  (delay (generation-timeout-ms-from (System/getenv "EXAMPLE_GENERATION_TIMEOUT_MS"))))


(defn- example-max-tokens
  []
  (-> (or (System/getenv "EXAMPLE_MAX_TOKENS") "300")
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


(def ^:private generated-example-schema
  [:map {:closed true}
   [:value
    {:description
     "A natural German sentence containing the requested target — a lemma, possibly inflected, or a whole phrase."}
    [:string {:min 1}]]
   [:translation
    {:description "Russian translation of the sentence."}
    [:string {:min 1}]]
   [:structure
    {:description
     "A list of JSON objects containing the used word form, its dictionary form, and its translation, ordered left to right as they appear in the German sentence."}
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


(def ^:private valid-generated-example-schema
  (->
   generated-example-schema
   (mu/update :value with-constraint german-sentence-text? "German sentence required")
   (mu/update :translation with-constraint russian-sentence-text? "Russian translation required")
   (mu/update-in [:structure 0 :translation] with-constraint russian-text? "Structure translation must be Cyrillic")))


(defn- normalize-text
  [text]
  (some-> text utils/sanitize-text str/lower-case))


(defn- sentence-word-count
  [sentence]
  (count (re-seq #"\p{L}+" (or sentence ""))))


(def ^:private min-sentence-words 3)


(def ^:private min-max-sentence-words 12)


(def ^:private words-around-target
  "How much room a sentence gets beyond the target itself. A six-word phrase
   cannot fit a flat twelve-word ceiling with anything around it."
  6)


(defn- sentence-length-bounds
  [target]
  {:min min-sentence-words
   :max (max min-max-sentence-words
             (+ (sentence-word-count target) words-around-target))})


(defn- sentence-length-ok?
  [target sentence]
  (let [{:keys [min max]} (sentence-length-bounds target)]
    (<= min (sentence-word-count sentence) max)))


(defn- split-sentence-words
  [sentence]
  (if (str/blank? sentence)
    []
    (str/split (str/trim sentence) #"\s+")))


(defn- strip-word-edges
  [word]
  (some-> word
          (str/replace #"(?u)^\P{L}+" "")
          (str/replace #"(?u)\P{L}+$" "")))


(defn- strip-word-indexes
  "The example without its structure items' `wordIndex`. A candidate of any
   other shape is returned unchanged for the schema to reject."
  [example]
  (let [structure (when (map? example) (:structure example))]
    (if (and (sequential? structure) (every? map? structure))
      (assoc example :structure (mapv #(dissoc % :wordIndex) structure))
      example)))


(defn- same-word?
  [sentence-word used-form]
  (= (normalize-text used-form)
     (normalize-text (strip-word-edges sentence-word))))


(defn- find-word-index
  [words next-word-index used-form]
  (loop [word-index next-word-index]
    (when (< word-index (count words))
      (if (same-word? (nth words word-index) used-form)
        word-index
        (recur (inc word-index))))))


(defn- add-word-indexes-to-structure
  [sentence structure]
  (let [words (split-sentence-words sentence)]
    (loop [items   (seq structure)
           next-word-index 0
           indexed []]
      (if-let [item (first items)]
        (when-let [word-index (find-word-index words next-word-index (:usedForm item))]
          (recur (next items)
                 (inc word-index)
                 (conj indexed (assoc item :wordIndex word-index))))
        indexed))))


(defn- add-word-indexes
  "No pair guard: a word the sentence genuinely says twice is ordinary German
   — `Zeit` in `Von Zeit zu Zeit …` — and with `structure` annotating words
   and not membership, nothing tells that apart from a separable prefix
   doubled onto the preposition sharing its spelling. Rejecting both cost an
   example for every construction of the first shape. The prompt's own rule
   against annotating that preposition is what carries the second now, and
   when the model disobeys it the cost is one wrong tooltip."
  [example]
  (let [example (strip-word-indexes example)]
    (when-let [structure (add-word-indexes-to-structure (:value example) (:structure example))]
      (assoc example :structure structure))))


(defn- log-generation-failure!
  [data]
  (t/log!
   {:level :warn
    :id    ::generation-failed
    :data  data}
   "Examples generation failed"))


(defn- glosses
  "The Russian glosses of a subject, from a string, a collection of strings or
   nil: trimmed, blanks and duplicates dropped, order kept."
  [translation]
  (->> (cond
         (nil? translation)        []
         (string? translation)     [translation]
         (sequential? translation) translation
         :else                     [])
       (keep #(some-> % str/trim not-empty))
       distinct
       vec))


(defn subject
  "What an example is generated for: the trimmed German word, its sorted
   Russian glosses and the collection context. The cache key and the prompt
   are both built from this one value."
  [{:keys [context translation word]}]
  {:context      (utils/non-blank (some-> context str/trim))
   :translations (vec (sort (glosses translation)))
   :word         (utils/non-blank (some-> word str/trim))})


(def subject-schema
  "What `subject` returns and everything downstream accepts. It is closed, so
   a raw request map with `:translation` does not pass for a subject."
  [:map {:closed true}
   [:context [:maybe [:string {:min 1}]]]
   [:translations [:vector [:string {:min 1}]]]
   [:word [:string {:min 1}]]])


(def ^:private issue-messages
  "Each message states the rule in the words the system prompt states it in,
   so a retry reads as a correction and not as a new instruction."
  {:malformed-example
   "The generated example did not match the required JSON shape or text constraints. See `details` for the specific field errors."
   :structure-mismatch
   "Items in `structure` must appear in strict left-to-right order as they occur in the German sentence, and each must match the word at its position. A `usedForm` is exactly one word of the sentence, spelled as the sentence spells it — the inflected form, never the lemma. An expression of several words is annotated one item per word, each with its own `dictionaryForm`, or left out of `structure` altogether — the sentence is where such an expression lives, not `structure`."
   :sentence-length-out-of-range
   "The German sentence is outside the accepted length. See `details` for the number of words it must contain."
   :target-lemma-missing
   "The sentence must contain the whole target: a single lemma as a `dictionaryForm` entry in `structure`, and a multi-word target with every one of its words present, inflected as the sentence needs."})


(def ^:private single-lemma-prefixes
  "First tokens after which a two-token target is still one lemma: the
   articles a noun is listed with, and the reflexive marker."
  #{"der" "die" "das" "ein" "eine" "sich"})


(defn- several-words?
  "Whether the target is several words rather than one lemma written with the
   article or the reflexive the dictionary lists it with. Shape only: nothing
   downstream asks which words belong to a construction."
  [target]
  (let [tokens (split-sentence-words target)]
    (and (< 1 (count tokens))
         (not (and (= 2 (count tokens))
                   (single-lemma-prefixes (str/lower-case (first tokens))))))))


(defn- word-present?
  "A word of the target counts as present when the sentence says it, or when
   some item names it as a `dictionaryForm` — which is what carries inflection:
   `verlieren` is in `Er verliert den Kopf.` only through `{verliert,
   verlieren}`."
  [sentence-words structure target-word]
  (or (some #(same-word? % target-word) sentence-words)
      (some #(dictionary/same-lemma? target-word (:dictionaryForm %)) structure)))


(defn- target-present?
  "One lemma must be named in `structure`, as before. Several words must be in
   the sentence — every one of them — because with plain word-by-word
   annotation nothing in `structure` says the construction was the target. An
   article pair takes whichever of the two answers yes: `die Leiter` is named
   by its own item, `das heißt` only by the sentence."
  [target sentence structure]
  (let [sentence-words (split-sentence-words sentence)
        target-words   (split-sentence-words target)
        all-words?     (every? #(word-present? sentence-words structure %) target-words)]
    (boolean
     (if (several-words? target)
       all-words?
       (or (dictionary/lemma-in-structure? target structure)
           (and (< 1 (count target-words)) all-words?))))))


(defn- example-issue
  [target example]
  (let [raw     (strip-word-indexes example)
        valid?  (m/validate valid-generated-example-schema raw)
        indexed (when valid? (add-word-indexes raw))]
    (cond
      (not valid?)
      (let [explain (me/humanize (m/explain valid-generated-example-schema raw))]
        (log-generation-failure!
         {:word    target
          :error   "Invalid generated example shape"
          :explain explain})
        {:issue :malformed-example :details explain})

      (nil? indexed)
      {:issue :structure-mismatch}

      (not (sentence-length-ok? target (:value indexed)))
      {:issue   :sentence-length-out-of-range
       :details (sentence-length-bounds target)}

      (not (target-present? target (:value indexed) (:structure indexed)))
      {:issue :target-lemma-missing})))


(defn generation-failure?
  [result]
  (= ::generation-failure (::type result)))


(def system-prompt
  "One target concept: the target is a lemma or a phrase, and every rule below
   is written for both. `structure` annotates the sentence word by word under
   one set of rules whatever the target is — it never records which words
   belonged to a construction. The few-shot block carries one case per
   distinct shape, and the retry messages in `issue-messages` repeat these
   rules word for word."
  (str/join
   "\n"
   [;; What this is
    "You generate learner-facing German example sentences for a vocabulary app."
    "Return only JSON that matches the supplied schema."
    "Input fields: word, translation, part of speech, cefrLevel, context, previousAttempt, previousIssue."
    "The target is `word`: either a single lemma (`die Leiter`, `aufpassen`, `sich vorstellen`) or a whole phrase (`auf jeden Fall`, `von Zeit zu Zeit`, `das heißt`). Every rule below applies to both, and `structure` is built the same way for both."
    "Missing cefrLevel => B2."
    ""
    ;; Sense
    "SENSE"
    "- `translation` is one or more Russian glosses the learner has confirmed; any of them is an acceptable sense for the sentence."
    "- Pick one sense that fits naturally; the structure `translation` of the target must match the gloss you chose."
    "- Ambiguous noun articles and ambiguous prefixes (`um-`, `über-`, `unter-`, `durch-`, `wieder-`) must follow the supplied Russian gloss."
    ""
    ;; Sentence
    "SENTENCE"
    "- Produce one natural standard German sentence containing the whole target."
    "- A lemma may appear as a correct inflected form. A phrase may be inflected and rearranged by German word order, and its words need not be adjacent — `Ich komme auf jeden Fall mit.` is the phrase `auf jeden Fall`."
    "- A phrase that is already a complete sentence is extended or embedded into a turn, never returned unchanged."
    "- Leave room around the target: roughly the target's own length plus a handful of words, and at least three words in all."
    "- German sentence only: no labels, notes, markdown, or meta commentary."
    "- `translation` must be one natural Russian sentence, not a calque."
    ""
    ;; Structure: what goes in
    "STRUCTURE — WHAT GOES IN"
    "- `structure` must include every noun, verb (including auxiliaries and modals), adjective, and adverb in the sentence."
    "- Exclude articles, pronouns, prepositions, conjunctions, and pure particles like `zu` or `nicht`."
    "- Detached prefixes of separable verbs are not particles: include them."
    "- The exclusions hold for a phrase target too: annotate the words of the construction that qualify, and leave its articles, prepositions and conjunctions out like any others."
    ""
    ;; Structure: the shape of an item
    "STRUCTURE — THE SHAPE OF AN ITEM"
    "- Each item in `structure` must be a JSON object with keys `usedForm`, `dictionaryForm`, and `translation`."
    "- A `usedForm` is exactly one word of the sentence, spelled as the sentence spells it — the inflected form, never the lemma. An expression of several words is annotated one item per word, each with its own `dictionaryForm`, or left out of `structure` altogether — the sentence is where such an expression lives, not `structure`."
    "- Never use arrays like `[\"Fenster\", \"das Fenster\", \"окно\"]` inside `structure`."
    "- Example structure item: `{\"usedForm\":\"Fenster\",\"dictionaryForm\":\"das Fenster\",\"translation\":\"окно\"}`."
    "- Keep `dictionaryForm` lemma-only unless the lemma inherently includes an article or `sich`."
    "- For nouns, `dictionaryForm` includes the article."
    "- Reflexive verb `dictionaryForm` keeps `sich`."
    "- For separable verbs with detached prefixes, include one item for the verb part and one for the prefix; both use the full infinitive as `dictionaryForm` and the same Russian gloss."
    "- A word of a phrase target takes its own lemma and its own gloss, like any other word. Never use the whole phrase as a `dictionaryForm`."
    ""
    ;; Structure: order and repetition
    "STRUCTURE — ORDER"
    "- Order `structure` items strictly left to right as they appear in the German sentence, and each `usedForm` must match the word at its position."
    "- The backend assigns `wordIndex`; do not return `wordIndex`."
    "- Separable verbs emit the prefix exactly once. If a preposition shares spelling with the prefix (for example `auf` in `Pass auf deine Sachen auf!`), exclude the preposition — only the detached prefix in the verb frame belongs in `structure`."
    ""
    ;; One case per shape
    "EXAMPLES — one per shape"
    "Noun with article. word=das Verstehen gloss=понимание:"
    "{\"value\":\"Das Verstehen dieser Regel dauert lange.\",\"translation\":\"Понимание этого правила требует времени.\",\"structure\":[{\"usedForm\":\"Verstehen\",\"dictionaryForm\":\"das Verstehen\",\"translation\":\"понимание\"},{\"usedForm\":\"Regel\",\"dictionaryForm\":\"die Regel\",\"translation\":\"правило\"},{\"usedForm\":\"dauert\",\"dictionaryForm\":\"dauern\",\"translation\":\"длиться\"},{\"usedForm\":\"lange\",\"dictionaryForm\":\"lang\",\"translation\":\"долго\"}]}"
    "Separable verb. word=aufpassen gloss=следить:"
    "{\"value\":\"Er passt auf die Kinder auf.\",\"translation\":\"Он следит за детьми.\",\"structure\":[{\"usedForm\":\"passt\",\"dictionaryForm\":\"aufpassen\",\"translation\":\"следить\"},{\"usedForm\":\"Kinder\",\"dictionaryForm\":\"das Kind\",\"translation\":\"дети\"},{\"usedForm\":\"auf\",\"dictionaryForm\":\"aufpassen\",\"translation\":\"следить\"}]}"
    "The first `auf` is a preposition and is excluded; only the sentence-final `auf` is the detached separable prefix."
    "Reflexive. word=sich vorstellen gloss=представляться:"
    "{\"value\":\"Er stellt sich bei den neuen Kollegen vor.\",\"translation\":\"Он представляется новым коллегам.\",\"structure\":[{\"usedForm\":\"stellt\",\"dictionaryForm\":\"sich vorstellen\",\"translation\":\"представляться\"},{\"usedForm\":\"neu\",\"dictionaryForm\":\"neu\",\"translation\":\"новый\"},{\"usedForm\":\"Kollegen\",\"dictionaryForm\":\"der Kollege\",\"translation\":\"коллега\"},{\"usedForm\":\"vor\",\"dictionaryForm\":\"sich vorstellen\",\"translation\":\"представляться\"}]}"
    "Homograph. word=Leiter gloss=лестница:"
    "{\"value\":\"Die Leiter steht neben der Wand.\",\"translation\":\"Лестница стоит у стены.\",\"structure\":[{\"usedForm\":\"Leiter\",\"dictionaryForm\":\"die Leiter\",\"translation\":\"лестница\"},{\"usedForm\":\"steht\",\"dictionaryForm\":\"stehen\",\"translation\":\"стоять\"},{\"usedForm\":\"Wand\",\"dictionaryForm\":\"die Wand\",\"translation\":\"стена\"}]}"
    "Do not use `der Leiter` for this meaning."
    "Phrase. word=von Zeit zu Zeit gloss=время от времени:"
    "{\"value\":\"Von Zeit zu Zeit besuche ich meine Eltern.\",\"translation\":\"Время от времени я навещаю своих родителей.\",\"structure\":[{\"usedForm\":\"Zeit\",\"dictionaryForm\":\"die Zeit\",\"translation\":\"время\"},{\"usedForm\":\"Zeit\",\"dictionaryForm\":\"die Zeit\",\"translation\":\"время\"},{\"usedForm\":\"besuche\",\"dictionaryForm\":\"besuchen\",\"translation\":\"навещать\"},{\"usedForm\":\"Eltern\",\"dictionaryForm\":\"die Eltern\",\"translation\":\"родители\"}]}"
    "The phrase is in the sentence, not in `structure`: `von` and `zu` are excluded as prepositions, and each `Zeit` is annotated as the noun it is."]))


(defn word-meta
  "What the dictionary says about the subject's word: part of speech and
   level, or nil when it has no entry. A dictionary that cannot be read gives
   a generation failure that is not retried."
  [{:keys [translations word]}]
  (try
    (dictionary/lookup-word-meta word translations)
    (catch Exception error
      (let [failure {::type      ::generation-failure
                     :retryable? false
                     :word       word
                     :error      (ex-message error)
                     :context    :failure/dictionary}]
        (log-generation-failure! failure)
        failure))))


(defn- generation-version
  "What a generated sentence depends on besides its subject: the prompt, the
   models and the dictionary's reading of the word."
  [word-meta]
  (let [{:keys [model models]} (provider/config)]
    {:models    (or models [model])
     :prompt    system-prompt
     :word-meta word-meta}))


(def ^:private digested
  "Hashed once per generation. The prompt runs to several kilobytes and every
   request would otherwise serialize and hash all of it — twice on a miss —
   for a value that changes when the build does, or when a word's dictionary
   entry does."
  (memoize (fn [version] (utils/sha256-hex (cheshire/generate-string version)))))


(defn generation-digest
  "The digest of the generation that would produce an example under the
   dictionary's `word-meta`. A new prompt, model or dictionary entry changes
   it, so old rows simply stop being found."
  [word-meta]
  (digested (generation-version word-meta)))


(defn subject-key
  "The cache key of `subject` under the dictionary's `word-meta`."
  [subject word-meta]
  (cache/digest subject (generation-digest word-meta)))


(defn- previous-issue-payload
  [{:keys [issue details]}]
  (cond-> {:issue   (name issue)
           :message (get issue-messages
                         issue
                         "Unknown issue; regenerate the example from scratch.")}
    (seq details) (assoc :details details)))


(defn- user-prompt
  [word translations context word-meta retry-context]
  (str/join
   "\n"
   ["Generate one example."
    "Return one JSON object matching the supplied schema."
    "If `previousAttempt` and `previousIssue` are present, fix the described problem without changing the intended sense."
    (cheshire/generate-string
     (cond-> {:word         word
              :partOfSpeech (:partOfSpeech word-meta)
              :cefrLevel    (:cefrLevel word-meta "C2")
              :translation  translations}
       (utils/non-blank context) (assoc :context context)
       retry-context (assoc :previousAttempt (:example retry-context)
                            :previousIssue   (previous-issue-payload retry-context))))]))


(defn- request-body
  [word translations context word-meta retry-context]
  {:messages        [{:role    "system"
                      :content system-prompt}
                     {:role    "user"
                      :content (user-prompt word translations context word-meta retry-context)}]
   :temperature     0.1
   :max_tokens      (example-max-tokens)
   :response_format {:type        "json_schema"
                     :json_schema {:name   "sentence_example"
                                   :schema (mjs/transform generated-example-schema)
                                   :strict true}}})


(defn example-api-request
  [word translations context word-meta retry-context]
  (provider/request
   (request-body word translations context word-meta retry-context)
   @generation-timeout-ms))


(defn- parsed-body
  "The response body as a JSON object, or nil when it is not one."
  [body]
  (let [parsed (try
                 (cheshire/parse-string (str body) true)
                 (catch Exception _
                   nil))]
    (when (map? parsed)
      parsed)))


(defn- first-choice
  "The first choice of a parsed 200 body, or nil when the body carries an
   error or no choices."
  [body]
  (let [choices (when (nil? (:error body)) (:choices body))
        choice  (when (sequential? choices) (first choices))]
    (when (map? choice)
      choice)))


(defn- choice-text
  "The text the model wrote in `choice`, or nil when it wrote none."
  [choice]
  (let [content (:content (:message choice))
        text    (cond
                  (string? content)
                  content

                  (sequential? content)
                  (->> content
                       (filter #(and (map? %) (= "text" (:type %)) (string? (:text %))))
                       (map :text)
                       (apply str)))]
    (when-not (str/blank? text)
      text)))


(defn- candidate
  "The example the model wrote in `content`, parsed. Text that is not JSON is
   returned as it is, for the checks to reject."
  [content]
  (try
    (cheshire/parse-string content true)
    (catch Exception _
      content)))


(defn- moderated?
  "Whether moderation or the model refused the input, so asking again would
   be refused too."
  [status body choice]
  (if (= 403 status)
    (boolean (seq (get-in body [:error :metadata :reasons])))
    (boolean
     (and choice
          (or (= "content_filter" (:finish_reason choice))
              (not (str/blank? (str (:refusal (:message choice))))))))))


(defn- failed-at-provider
  "The failure of a provider that answered with an error status or did not
   answer."
  [response word]
  ;; A 4xx other than 408 fails the same way when asked again.
  (let [status  (:status response)
        hard?   (and (int? status) (<= 400 status 499) (not= 408 status))
        failure (merge
                 (select-keys response [:status :error :body])
                 {::type          ::generation-failure
                  :retryable?     (not hard?)
                  :word           word
                  :retry-after-ms (provider/retry-after-ms response)})]
    (log-generation-failure! failure)
    failure))


;; The shapes read here follow https://openrouter.ai/openapi.json and
;; https://openrouter.ai/docs/api/reference/errors-and-debugging.
(defn- parse-generated-example
  "What one provider response gives: the model's candidate, a generation
   failure, or a moderated marker when the input was refused."
  [response word]
  (let [status (:status response)
        body   (parsed-body (:body response))
        choice (when (= 200 status) (first-choice body))
        text   (when choice (choice-text choice))]
    (cond
      (moderated? status body choice)
      (let [moderated {::type ::moderated
                       :word  word
                       :body  (:body response)}]
        (log-generation-failure! (assoc moderated :error "Moderation refused the input"))
        moderated)

      text
      (candidate text)

      (= 200 status)
      ;; No completion. Only an empty answer cut off at the token limit is
      ;; not asked again.
      (let [failure {::type      ::generation-failure
                     :retryable? (not= "length" (:finish_reason choice))
                     :word       word
                     :error      "No completion in the provider's answer"
                     :body       (:body response)}]
        (log-generation-failure! failure)
        failure)

      :else
      (failed-at-provider response word))))


(defn- generate-attempt!
  "One request to the provider, waited for at most the per-attempt timeout.
   An interrupted wait gives a failure that is not retried."
  [word translations context word-meta retry-context]
  (let [timeout-ms @generation-timeout-ms]
    (try
      (-> (example-api-request word translations context word-meta retry-context)
          (deref timeout-ms {:error (str "No answer within " timeout-ms " ms")})
          (parse-generated-example word))
      (catch InterruptedException error
        (.interrupt (Thread/currentThread))
        (let [failure {::type      ::generation-failure
                       :retryable? false
                       :word       word
                       :error      (.getMessage error)
                       :context    :failure/interrupted}]
          (log-generation-failure! failure)
          failure))
      (catch Exception error
        (let [failure {::type      ::generation-failure
                       :retryable? true
                       :word       word
                       :error      (.getMessage error)
                       :context    :failure/transport
                       :cause      (some-> error ex-data)}]
          (log-generation-failure! failure)
          failure)))))


(defn generate-one!
  "An example sentence generated for `subject` under the dictionary's
   `word-meta`, in up to `max-generation-attempts` attempts. A provider
   failure gives a generation failure; a rejected last candidate or a refused
   input gives nil."
  ([subject word-meta]
   (generate-one! subject word-meta max-generation-attempts))
  ([{:keys [context translations word]} word-meta max-attempts]
   (loop [attempt       1
          retry-context nil]
     (let [example  (generate-attempt! word translations context word-meta retry-context)
           failure? (generation-failure? example)
           refused? (= ::moderated (::type example))
           result   (when-not (or failure? refused?) (example-issue word example))
           issue    (:issue result)]
       (cond
         ;; The same input would be refused again.
         refused?
         nil

         (and failure? (not (:retryable? example)))
         example

         (and (not failure?) (nil? issue))
         (add-word-indexes example)

         (< attempt max-attempts)
         (let [retry-ctx (when (and (not failure?) issue)
                           (cond-> {:example (strip-word-indexes example)
                                    :issue   issue}
                             (seq (:details result)) (assoc :details (:details result))))]
           (when retry-ctx
             (log-generation-failure!
              {:words   [word]
               :attempt attempt
               :error   "Rejected generated example candidate"
               :issue   issue
               :example example}))
           (recur (inc attempt) retry-ctx))

         ;; The last attempt decides whose failure it is.
         failure?
         example

         :else
         (do
           (log-generation-failure!
            {:words   [word]
             :attempt attempt
             :error   "Exhausted example generation attempts"
             :issue   issue
             :example example})
           nil))))))


(defn outcome
  "The kind of result `get!` returned, for the endpoint to pick its status:
   `:outcome/success`, `:outcome/rejected`, `:outcome/throttled` or
   `:outcome/unavailable`."
  [result]
  (cond
    (valid-example? result)            :outcome/success
    (not (generation-failure? result)) :outcome/rejected
    (= 429 (:status result))           :outcome/throttled
    :else                              :outcome/unavailable))


(defn get!
  "The example for `subject`: the stored one, or one generated now and
   stored. Otherwise the generation's failure, or nil when no candidate
   passed."
  [db subject]
  (let [word-meta (word-meta subject)]
    (if (generation-failure? word-meta)
      word-meta
      (let [cache-key (subject-key subject word-meta)]
        (try
          (single-flight/run
           cache-key
           (fn []
             (or (cache/lookup db cache-key)
                 (let [result (generate-one! subject word-meta)]
                   (if (and (valid-example? result)
                            (false? (cache/store! db cache-key subject result)))
                     (or (cache/lookup db cache-key) result)
                     result)))))
          (catch java.util.concurrent.TimeoutException error
            (let [failure {::type      ::generation-failure
                           :retryable? false
                           :word       (:word subject)
                           :error      (ex-message error)
                           :context    :failure/joined-run}]
              (log-generation-failure! failure)
              failure)))))))


(comment
  (let [asked (subject {:word "das Entsetzen" :translation ["ужас" "испуг"]})]
    (generate-one! asked (word-meta asked))))

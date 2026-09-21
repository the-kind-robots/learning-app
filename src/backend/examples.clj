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
  [example]
  (update example
          :structure
          (fn [structure]
            (mapv #(dissoc % :wordIndex) (or structure [])))))


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


(defn- normalize-translations
  "Accepts a string, a collection of strings, or nil. Returns a vector of
   non-blank strings, preserving order and deduping."
  [translation]
  (let [raw (cond
              (nil? translation)        []
              (string? translation)     [translation]
              (sequential? translation) translation
              :else                     [])]
    (->> raw (remove str/blank?) distinct vec)))


(def ^:private issue-messages
  "Each message states the rule in the words the system prompt states it in,
   so a retry reads as a correction and not as a new instruction."
  {:malformed-example
   "The generated example did not match the required JSON shape or text constraints. See `details` for the specific field errors."
   :structure-mismatch
   "Items in `structure` must appear in strict left-to-right order as they occur in the German sentence, each `usedForm` must be one word of the sentence, and each must match the word at its position."
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


(defn- retry-after-ms
  [response]
  (when-let [f (:retry-after-ms (provider/config))]
    (f response)))


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
    "- One item per word: a `usedForm` is a single word of the sentence, never several."
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
                   {::type      ::generation-failure
                    :retryable? (not hard?)
                    :word       word
                    :retry-after-ms (retry-after-ms response)})]
      (log-generation-failure! failure)
      failure)))


(defn- generate-attempt!
  [word translations context word-meta retry-context]
  (try
    (-> @(example-api-request word translations context word-meta retry-context)
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
  `translation` may be a single string or a collection of strings; all are
  passed through to the prompt and validation so any can be the chosen sense.
  Returns one of:
  * success — map with keys :value, :translation, :structure
    (a vector of maps with :usedForm, :dictionaryForm, :translation);
  * generation-failure map on a hard error (e.g. 429);
  * nil when all attempts are exhausted."
  ([input]
   (generate-one! input 3))
  ([{:keys [word translation context]} max-attempts]
   (let [translations (normalize-translations translation)
         word-meta    (dictionary/lookup-word-meta word translations)]
     (loop [attempt       1
            retry-context nil]
       (let [example  (generate-attempt! word translations context word-meta retry-context)
             failure? (generation-failure? example)
             result   (when-not failure? (example-issue word example))
             issue    (:issue result)]
         (cond
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

           :else
           (do
             (when (and (not failure?) issue)
               (log-generation-failure!
                {:words   [word]
                 :attempt attempt
                 :error   "Exhausted example generation attempts"
                 :issue   issue
                 :example example}))
             nil)))))))


(comment
  (generate-one! {:word "das Entsetzen" :translation ["ужас" "испуг"]}))

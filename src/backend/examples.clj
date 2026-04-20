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
    {:description "A natural German sentence using the requested word."}
    [:string {:min 1}]]
   [:translation
    {:description "Russian translation of the sentence."}
    [:string {:min 1}]]
   [:glossMismatch
    {:description "Whether the supplied Russian gloss appears mismatched to the German word."}
    :boolean]
   [:structure
    {:description "A list of JSON objects containing the used word form, its dictionary form, and its translation, ordered left to right as they appear in the German sentence."}
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
  (-> generated-example-schema
      (mu/update :value       with-constraint german-sentence-text?  "German sentence required")
      (mu/update :translation with-constraint russian-sentence-text? "Russian translation required")
      (mu/update-in [:structure 0 :translation] with-constraint russian-text? "Structure translation must be Cyrillic")))


(defn- normalize-text
  [text]
  (some-> text utils/sanitize-text str/lower-case))


(defn- sentence-word-count
  [sentence]
  (count (re-seq #"\p{L}+" (or sentence ""))))


(defn- sentence-length-ok?
  [sentence]
  (<= 3 (sentence-word-count sentence) 12))


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
  (update example :structure
          (fn [structure]
            (mapv #(dissoc % :wordIndex) (or structure [])))))


(defn- find-word-index
  [words next-word-index used-form]
  (loop [word-index next-word-index]
    (when (< word-index (count words))
      (if (= (normalize-text used-form)
             (normalize-text (strip-word-edges (nth words word-index))))
        word-index
        (recur (inc word-index))))))


(defn- add-word-indexes-to-structure
  [sentence structure]
  (let [words (split-sentence-words sentence)]
    (loop [items           (seq structure)
           next-word-index 0
           indexed         []]
      (if-let [item (first items)]
        (if-let [word-index (find-word-index words next-word-index (:usedForm item))]
          (recur (next items)
                 (inc word-index)
                 (conj indexed (assoc item :wordIndex word-index)))
          nil)
        indexed))))


(defn- structure-has-duplicate-items?
  [structure]
  (not= (count structure)
        (count (distinct (map (juxt :usedForm :dictionaryForm) structure)))))


(defn- add-word-indexes
  [example]
  (let [example (strip-word-indexes example)]
    (when-not (structure-has-duplicate-items? (:structure example))
      (when-let [structure (add-word-indexes-to-structure (:value example) (:structure example))]
        (assoc example :structure structure)))))


(defn- log-generation-failure!
  [data]
  (t/log!
   {:level :warn
    :id    ::generation-failed
    :data  data}
   "Examples generation failed"))


(def ^:private issue-messages
  {:malformed-example            "The generated example did not match the required JSON shape or text constraints. See `details` for the specific field errors."
   :structure-mismatch           "Items in `structure` must appear in strict left-to-right order as they occur in the German sentence, each `usedForm` must match the word at its position, and a `{usedForm, dictionaryForm}` pair must not repeat (each separable-verb prefix appears once, not twice)."
   :sentence-length-out-of-range "The German sentence must contain between 3 and 12 words."
   :target-lemma-missing         "The target lemma must appear as a `dictionaryForm` entry in `structure`."
   :target-gloss-mismatch        "The target lemma's structure `translation` contradicts the supplied Russian gloss."})


(defn- example-issue
  [word translation example]
  (let [raw     (strip-word-indexes example)
        valid?  (m/validate valid-generated-example-schema raw)
        indexed (when valid? (add-word-indexes raw))]
    (cond
      (not valid?)
      (let [explain (me/humanize (m/explain valid-generated-example-schema raw))]
        (log-generation-failure!
         {:word    word
          :error   "Invalid generated example shape"
          :explain explain})
        {:issue :malformed-example :details explain})

      (nil? indexed)
      {:issue :structure-mismatch}

      (not (sentence-length-ok? (:value indexed)))
      {:issue :sentence-length-out-of-range}

      (not (dictionary/lemma-in-structure? word (:structure indexed)))
      {:issue :target-lemma-missing}

      (not (dictionary/word-gloss-valid? word translation (:structure indexed)))
      {:issue :target-gloss-mismatch})))



(defn- retry-after-ms
  [response]
  (when-let [f (:retry-after-ms (provider/config))]
    (f response)))


(defn generation-failure?
  [result]
  (= ::generation-failure (::type result)))


(defn- gloss-mismatch-issue?
  [issue]
  (= :target-gloss-mismatch issue))


(defn- mark-gloss-mismatch
  [example]
  (assoc example :glossMismatch true))


(def system-prompt
  (str/join
   "\n"
   ["You generate learner-facing German example sentences for a vocabulary app."
    "Return only JSON that matches the supplied schema."
    "Input fields: word, translation, part of speech, cefrLevel, previousAttempt, previousIssue."
    "Missing cefrLevel => B2."
    "- Match the intended Russian gloss sence."
    "- Produce one natural standard German sentence, 6-12 words, using the target lemma or a correct inflected form."
    "- German sentence only: no labels, notes, markdown, or meta commentary."
    "- `translation` must be one natural Russian sentence, not a calque."
    "- Set `glossMismatch` to true only if the supplied gloss clearly mismatches the target lemma; otherwise false."
    "- `structure` must include every noun, verb (including auxiliaries and modals), adjective, and adverb in the sentence."
    "- Each item in `structure` must be a JSON object with keys `usedForm`, `dictionaryForm`, and `translation`."
    "- Never use arrays like `[\"Fenster\", \"das Fenster\", \"окно\"]` inside `structure`."
    "- Exclude articles, pronouns, prepositions, conjunctions, and pure particles like `zu` or `nicht`."
    "- Detached prefixes of separable verbs are not particles: include them."
    "- Order `structure` items strictly left to right as they appear in the German sentence."
    "- The backend assigns `wordIndex`; do not return `wordIndex`."
    "- Keep `dictionaryForm` lemma-only unless the lemma inherently includes an article or `sich`."
    "- For nouns, `dictionaryForm` includes the article."
    "- For separable verbs with detached prefixes, include one item for the verb part and one for the prefix; both use the full infinitive as `dictionaryForm` and the same Russian gloss."
    "- Separable verbs emit the prefix exactly once. If a preposition shares spelling with the prefix (for example `auf` in `Pass auf deine Sachen auf!`), exclude the preposition — only the detached prefix in the verb frame belongs in `structure`."
    "- Never emit two `structure` items with the same `usedForm` and `dictionaryForm` pair."
    "- Reflexive verb `dictionaryForm` keeps `sich`."
    "- Ambiguous noun articles and ambiguous prefixes (`um-`, `über-`, `unter-`, `durch-`, `wieder-`) must follow the supplied Russian gloss."
    "- Example structure item: `{\"usedForm\":\"Fenster\",\"dictionaryForm\":\"das Fenster\",\"translation\":\"окно\"}`."
    "Example word=aufstehen gloss=вставать:"
    "{\"value\":\"Ich stehe jeden Morgen um sieben Uhr auf.\",\"translation\":\"Я встаю каждое утро в семь часов.\",\"glossMismatch\":false,\"structure\":[{\"usedForm\":\"stehe\",\"dictionaryForm\":\"aufstehen\",\"translation\":\"вставать\"},{\"usedForm\":\"Morgen\",\"dictionaryForm\":\"der Morgen\",\"translation\":\"утро\"},{\"usedForm\":\"auf\",\"dictionaryForm\":\"aufstehen\",\"translation\":\"вставать\"}]}"
    "Example word=aufpassen gloss=следить:"
    "{\"value\":\"Er passt auf die Kinder auf.\",\"translation\":\"Он следит за детьми.\",\"glossMismatch\":false,\"structure\":[{\"usedForm\":\"passt\",\"dictionaryForm\":\"aufpassen\",\"translation\":\"следить\"},{\"usedForm\":\"Kinder\",\"dictionaryForm\":\"das Kind\",\"translation\":\"дети\"},{\"usedForm\":\"auf\",\"dictionaryForm\":\"aufpassen\",\"translation\":\"следить\"}]}"
    "Note: the first `auf` is a preposition and is excluded; only the sentence-final `auf` is the detached separable prefix."
    "Example word=das Verstehen gloss=понимание:"
    "{\"value\":\"Das Verstehen dieser Regel dauert lange.\",\"translation\":\"Понимание этого правила требует времени.\",\"glossMismatch\":false,\"structure\":[{\"usedForm\":\"Verstehen\",\"dictionaryForm\":\"das Verstehen\",\"translation\":\"понимание\"},{\"usedForm\":\"Regel\",\"dictionaryForm\":\"die Regel\",\"translation\":\"правило\"},{\"usedForm\":\"dauert\",\"dictionaryForm\":\"dauern\",\"translation\":\"длиться\"},{\"usedForm\":\"lange\",\"dictionaryForm\":\"lang\",\"translation\":\"долго\"}]}"
    "Example word=die Bank gloss=скамейка:"
    "{\"value\":\"Wir sitzen auf einer Bank im Park.\",\"translation\":\"Мы сидим на скамейке в парке.\",\"glossMismatch\":false,\"structure\":[{\"usedForm\":\"sitzen\",\"dictionaryForm\":\"sitzen\",\"translation\":\"сидеть\"},{\"usedForm\":\"Bank\",\"dictionaryForm\":\"die Bank\",\"translation\":\"скамейка\"},{\"usedForm\":\"Park\",\"dictionaryForm\":\"der Park\",\"translation\":\"парк\"}]}"
    "Example word=Leiter gloss=лестница:"
    "{\"value\":\"Die Leiter steht neben der Wand.\",\"translation\":\"Лестница стоит у стены.\",\"glossMismatch\":false,\"structure\":[{\"usedForm\":\"Leiter\",\"dictionaryForm\":\"die Leiter\",\"translation\":\"лестница\"},{\"usedForm\":\"steht\",\"dictionaryForm\":\"stehen\",\"translation\":\"стоять\"},{\"usedForm\":\"Wand\",\"dictionaryForm\":\"die Wand\",\"translation\":\"стена\"}]}"
    "Do not use `der Leiter` for this meaning."
    "Example word=sich vorstellen gloss=представляться:"
    "{\"value\":\"Er stellt sich bei den neuen Kollegen vor.\",\"translation\":\"Он представляется новым коллегам.\",\"glossMismatch\":false,\"structure\":[{\"usedForm\":\"stellt\",\"dictionaryForm\":\"sich vorstellen\",\"translation\":\"представляться\"},{\"usedForm\":\"neu\",\"dictionaryForm\":\"neu\",\"translation\":\"новый\"},{\"usedForm\":\"Kollegen\",\"dictionaryForm\":\"der Kollege\",\"translation\":\"коллега\"},{\"usedForm\":\"vor\",\"dictionaryForm\":\"sich vorstellen\",\"translation\":\"представляться\"}]}"]))



(defn- previous-issue-payload
  [{:keys [issue details]}]
  (cond-> {:issue   (name issue)
           :message (get issue-messages issue
                         "Unknown issue; regenerate the example from scratch.")}
    (seq details) (assoc :details details)))


(defn- user-prompt
  [word translation word-meta retry-context]
  (str/join
   "\n"
   ["Generate one example."
    "Return one JSON object matching the supplied schema."
    "If `previousAttempt` and `previousIssue` are present, fix the described problem without changing the intended sense."
    (cheshire/generate-string
     (cond-> {:word         word
              :partOfSpeech (:partOfSpeech word-meta)
              :cefrLevel    (:cefrLevel word-meta "C2")
              :translation  translation}
       retry-context (assoc :previousAttempt (:example retry-context)
                            :previousIssue   (previous-issue-payload retry-context))))]))


(defn- request-body
  [word translation word-meta retry-context]
  {:messages        [{:role    "system"
                      :content system-prompt}
                     {:role    "user"
                      :content (user-prompt word translation word-meta retry-context)}]
   :temperature     0.1
   :max_tokens      (example-max-tokens)
   :response_format {:type        "json_schema"
                     :json_schema {:name   "sentence_example"
                                   :schema (mjs/transform generated-example-schema)
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
             result   (when-not failure? (example-issue word translation example))
             issue    (:issue result)]
         (cond
           (and failure? (not (:retryable? example)))
           example

           (and (not failure?) (gloss-mismatch-issue? issue))
           (-> example
               add-word-indexes
               mark-gloss-mismatch)

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
  (generate-one! {:word "das Entsetzen"}))

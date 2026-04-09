(ns examples
  (:require
   [cheshire.core :as cheshire]
   [clojure.string :as str]
   [org.httpkit.client :as client]
   [taoensso.telemere :as t]))


(defn open-ai-service-account-api-key
  []
  (System/getenv "OPENAI_API_KEY"))


(defn valid-example?
  [example]
  (and (map? example)
       (not (str/blank? (some-> example (get "value"))))
       (not (str/blank? (some-> example (get "translation"))))))


(defn- valid-generated-examples?
  [examples]
  (and (map? examples)
       (every? valid-example? (vals examples))))


(defn- log-generation-failure!
  [data]
  (t/log!
   {:level :warn
    :id    ::generation-failed
    :data  data}
   "Examples generation failed"))


(def system-prompt
  "Process JSON requests containing German words array [\"word1\", \"word2\", ...] and return a JSON object where:
- Each key is a German word
   - Each value is an object with \"value\", \"translation\", \"structure\" keys
   - \"value\" contains a German sentence using the word with an appropriate grammatical form:
     * Nouns: Use cases (Nominativ/Genitiv/Dativ/Akkusativ) or number.
     * Verbs: Use tenses (Präsens/Präteritum/Imperativ/Konjunktiv/etc.), ensuring that if the input verb is provided without a separable prefix, only its inflected forms without any detachable prefix are used. Conversely, if the input verb includes a detachable prefix, all verb forms in the generated sentence must retain that prefix.
     * Adjectives: Use proper declensions.
   Generated sentences should align with language proficiency levels from A1 to C2, incorporating diverse grammatical structures (e.g., conditional clauses, subjunctive mood, etc.) as well as idioms, set phrases, and fixed expressions.
   The sentences should adhere to standard written and spoken German.
   - \"translation\" contains the Russian translation of the sentence.
   - \"structure\" is a list of pairs, where each pair consists of a word from the sentence in its used form, its dictionary form and its russian translation as it is used in the sentence. There should be a pair for each word in the sentence. Exclude pairs for funtction words, pronouns, particles, and prepositions. Exclude pronouns, articles, particles and prepositions from structure. Structure should include only nouns, verbs, adjectives and adverbs. For a noun the 'dictionaryForm' should include an article, e.g [{'usedForm': 'hund', 'dictionaryForm': 'der Hund', 'translation': 'пёс' }]. If a word in the sentence is a verb with separated prefix (and the prefix is actually separeted), the dictionary form of the whole verb should be provided both for separated prefix and root part of the verb, e.g [{'usedForm': 'komme', 'dictionaryForm': 'ankommen', 'translation': 'прибывать'}, {'usedForm': 'an', 'dictionaryForm': 'ankommen', 'translation': 'прибывать'}].Ensure that every noun, verb (including each separated prefix as its own entry with the same dictionary form as the full verb), adjective, and adverb is included in the 'structure' array. Do not skip any eligible word even if it appears only once.
Return only the JSON object without additional text.")


(defn gen-words-api-request
  [words]
  (client/request
   {:url "https://api.openai.com/v1/chat/completions"
    :method :post
    :headers {"Authorization" (str "Bearer " (open-ai-service-account-api-key))
              "Content-Type"  "application/json"}
    :body
    (cheshire/generate-string
     {:model    "gpt-5.2"
      :messages [{:role    "system"
                  :content system-prompt}
                 {:role    "user"
                  :content (str "Generate for input words " (cheshire/generate-string words))}]

      :response_format
      {:type "json_schema"

       :json_schema
       {:name "sentence_examples"
        :schema
        {:type "object"

         :properties
         (into
          {}
          (for [word words]
            [word
             {:type "object"
              :properties
              {"value"
               {:type        "string"
                :description "A German sentence using the word."}

               "translation"
               {:type        "string"
                :description "Russian translation of the sentence."}

               "structure"
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
                   :description "The dictionary form of the word."}}

                 :additionalProperties false
                 :required ["usedForm" "dictionaryForm" "translation"]}}}

              :additionalProperties false
              :required ["value" "translation" "structure"]}]))
         :additionalProperties false
         :required words}
        :strict true}}
      :top_p    1})}))


(defn generate-many!
  "Returns a map from words to maps:
  * `:value` — German text;
  * `:translation` — Russian translation;
  * `:structure` — a list of pairs, where each pair has:
    * `:dictionaryForm` — the dictionary form of the word;
    * `:usedForm` — the form of the word used in the sentence."
  [words]
  (let [response @(gen-words-api-request words)]
    (if (= 200 (:status response))
      (try
        ;; I am not converting keys of :message :content to keywords, as the upper level keys may contain spaces, e.g 'der Hund'.
        (let [examples (-> response :body (cheshire/parse-string true) :choices first :message :content cheshire/parse-string)]
          (if (valid-generated-examples? examples)
            examples
            (do
              (log-generation-failure!
               {:words words
                :status (:status response)
                :error "Invalid generated examples payload"
                :examples examples
                :body (:body response)})
              nil)))
        (catch Exception error
          (log-generation-failure!
           {:words words
            :status (:status response)
            :error (.getMessage error)
            :body (:body response)})
          nil))
      (do
        (log-generation-failure!
         {:words words
          :status (:status response)
          :error (:error response)
          :body (:body response)})
        nil))))


(defn generate-one!
  "Returns a hash map with the following keys:
  * `:value` — German text;
  * `:translation` — Russian translation;
  * `:structure` — a list of pairs, where each pair has:
    * `:dictionaryForm` — the dictionary form of the word;
    * `:usedForm` — the form of the word used in the sentence;
    * `:translation`— russian translation of the word used in the sentence."
  [word]
  (-> [word] generate-many! vals first))


(defn generate!
  [input]
  (cond
    (string? input)     (generate-one! input)
    (sequential? input) (generate-many! input)
    :else               nil))


(comment
  (generate! "das Entsetzen"))


(defn add-example!
  [db word-id]
  (let [word    ""
        example (generate-one! word)]))


(defn refresh!
  "Generate new examples for words in `word-ids` list"
  [db word-ids]
  (let [words        []
        new-examples (generate! (map :value words))]))


(defn examples
  [db word-ids])

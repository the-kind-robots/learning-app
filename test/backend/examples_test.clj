(ns backend.examples-test
  (:require
   [cheshire.core :as cheshire]
   [clojure.test :refer [deftest is testing]]
   [db :as db]
   [examples :as sut]
   [examples.dictionary :as dictionary]
   [examples.provider :as provider]
   [org.httpkit.client :as client]))


(deftest example-api-request-uses-openrouter-defaults
  (testing "OpenRouter is the default transport and model path"
    (let [captured (atom nil)]
      (with-redefs [client/request (fn [request]
                                     (reset! captured request)
                                     ::request)
                    provider/config (constantly {:api-url "https://api.groq.com/openai/v1/chat/completions"
                                                          :api-key "groq-test-key"
                                                          :model "openai/gpt-oss-20b"})]
        (is (= ::request (sut/example-api-request "Hund" [] nil nil)))
        (let [{:keys [url method headers body]} @captured
              payload (cheshire/parse-string body true)]
          (is (= "https://api.groq.com/openai/v1/chat/completions" url))
          (is (= :post method))
          (is (= "Bearer groq-test-key" (get headers "Authorization")))
          (is (= "application/json" (get headers "Content-Type")))
          (is (= "openai/gpt-oss-20b" (:model payload)))
          (is (= 300 (:max_tokens payload)))
          (is (= "json_schema" (get-in payload [:response_format :type])))
          (is (= ["value" "translation" "glossMismatch" "structure"]
                 (get-in payload [:response_format :json_schema :schema :required])))
          (is (= "array"
                 (get-in payload [:response_format :json_schema :schema :properties :structure :type])))
          (is (= "object"
                 (get-in payload [:response_format :json_schema :schema :properties :structure :items :type])))
          (is (= ["usedForm" "dictionaryForm" "translation"]
                 (get-in payload [:response_format :json_schema :schema :properties :structure :items :required])))
          (is (nil?
               (get-in payload [:response_format :json_schema :schema :properties :structure :items :properties :wordIndex])))
          (is (= "sentence_example" (get-in payload [:response_format :json_schema :name])))
          (is (string? (get-in payload [:messages 0 :content])))
          (is (re-find #"learner-facing German example sentences"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"part of speech"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"Ich stehe jeden Morgen um sieben Uhr auf"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"Er passt auf die Kinder auf"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"Separable verbs emit the prefix exactly once"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"Never emit two `structure` items with the same `usedForm` and `dictionaryForm` pair"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"sich vorstellen"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"das Verstehen"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"Leiter"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"die Leiter"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"Each item in `structure` must be a JSON object"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"Order `structure` items strictly left to right"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"The backend assigns `wordIndex`; do not return `wordIndex`"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"Never use arrays like"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"Hund" (get-in payload [:messages 1 :content]))))))))


(deftest example-api-request-allows-env-overrides
  (testing "model, url and provider api key can be overridden from env"
    (let [captured (atom nil)]
      (with-redefs [client/request (fn [request]
                                     (reset! captured request)
                                     ::request)
                    provider/config (constantly {:api-url "https://example.test/openai/v1/chat/completions"
                                                          :api-key "openai-override-key"
                                                          :model "openai/gpt-oss-120b"})]
        (is (= ::request (sut/example-api-request "Hund" ["собака"] nil nil)))
        (let [{:keys [url headers body]} @captured
              payload (cheshire/parse-string body true)]
          (is (= "https://example.test/openai/v1/chat/completions" url))
          (is (= "Bearer openai-override-key" (get headers "Authorization")))
          (is (= "openai/gpt-oss-120b" (:model payload)))
          (is (= 300 (:max_tokens payload)))
          (is (= ["value" "translation" "glossMismatch" "structure"]
                 (get-in payload [:response_format :json_schema :schema :required])))
          (is (= ["usedForm" "dictionaryForm" "translation"]
                 (get-in payload [:response_format :json_schema :schema :properties :structure :items :required])))
          (is (re-find #"собака"
                       (get-in payload [:messages 1 :content]))))))))


(deftest example-api-request-allows-max-tokens-env-override
  (testing "max tokens can be overridden for example generation requests"
    (let [captured (atom nil)]
      (with-redefs [client/request (fn [request]
                                     (reset! captured request)
                                     ::request)
                    provider/config (constantly {:api-url "https://example.test/openai/v1/chat/completions"
                                                 :api-key "openai-override-key"
                                                 :model "openai/gpt-oss-120b"})]
        (with-redefs [sut/example-max-tokens (constantly 180)]
          (is (= ::request (sut/example-api-request "Hund" ["собака"] nil nil)))
          (let [payload (cheshire/parse-string (:body @captured) true)]
            (is (= 180 (:max_tokens payload)))))))))


(deftest example-api-request-serializes-translations-as-array
  (testing "user prompt sends every confirmed translation so the model can pick the fitting sense"
    (let [captured (atom nil)]
      (with-redefs [client/request (fn [request]
                                     (reset! captured request)
                                     ::request)
                    provider/config (constantly {:api-url "https://api.groq.com/openai/v1/chat/completions"
                                                 :api-key "groq-test-key"
                                                 :model "openai/gpt-oss-20b"})]
        (is (= ::request (sut/example-api-request "Bank" ["банк" "скамейка"] nil nil)))
        (let [payload      (cheshire/parse-string (:body @captured) true)
              user-message (get-in payload [:messages 1 :content])
              payload-line (re-find #"(?m)^\{.*\}$" user-message)
              user-payload (cheshire/parse-string payload-line true)]
          (is (= ["банк" "скамейка"] (:translation user-payload)))
          (is (= "Bank" (:word user-payload))))))))


(deftest example-api-request-includes-retry-feedback
  (testing "retry attempts include the rejected example and issue list in the user prompt"
    (let [captured (atom nil)
          rejected-example {:value "Der Leiter steht neben der Wand."
                            :translation "Лестница стоит рядом со стеной."
                            :glossMismatch false
                            :structure
                            [{:usedForm "Leiter"
                              :dictionaryForm "der Leiter"
                              :translation "лестница"}]}]
      (with-redefs [client/request (fn [request]
                                     (reset! captured request)
                                     ::request)
                    provider/config (constantly {:api-url "https://api.groq.com/openai/v1/chat/completions"
                                                          :api-key "groq-test-key"
                                                          :model "openai/gpt-oss-20b"})]
        (is (= ::request
               (sut/example-api-request
                "Leiter"
                ["лестница"]
                nil
                {:example rejected-example
                 :issue   :target-gloss-mismatch})))
        (let [payload      (cheshire/parse-string (:body @captured) true)
              user-message (get-in payload [:messages 1 :content])]
          (is (re-find #"previousAttempt" user-message))
          (is (re-find #"previousIssue" user-message))
          (is (re-find #"target-gloss-mismatch" user-message))
          (is (re-find #"structure `translation` contradicts" user-message))
          (is (re-find #"Der Leiter steht neben der Wand" user-message)))))))


(deftest generate-one-rejects-meta-and-non-russian-garbage
  (testing "obvious meta responses or non-russian structure translations are rejected"
    (let [body (cheshire/generate-string
                {:choices
                 [{:message
                   {:content
                    (cheshire/generate-string
                     {:value "The example for 'aufstehen' is ..."
                      :translation "to stand up"
                      :glossMismatch true
                      :structure
                      [{:usedForm "aufstehen"
                        :dictionaryForm "aufstehen"
                        :translation "to stand up"}]})}}]})]
      (with-redefs [sut/example-api-request (fn [_word _translation _word-meta _retry-context]
                                                (delay {:status 200 :body body}))
                    dictionary/lookup-dictionary-entries (constantly nil)]
        (is (nil? (sut/generate-one! {:word "aufstehen" :translation "вставать"})))))))


(deftest deterministic-example-issues-stop-at-structural-invalidity
  (testing "structurally invalid examples return only the structural issue"
    (let [example {:value "The example for 'aufstehen' is ..."
                   :translation "to stand up"
                   :glossMismatch true
                   :structure
                   [{:usedForm "aufstehen"
                     :dictionaryForm "aufstehen"
                     :translation "to stand up"}]}]
      (is (= :malformed-example
             (:issue (#'sut/example-issue "aufstehen" ["вставать"] example)))))))


(deftest deterministic-example-issues-reject-target-present-only-in-structure
  (testing "target forms mentioned only in structure do not count as present in the sentence"
    (let [example {:value "Der Hund läuft schnell im Park."
                   :translation "Собака быстро бежит по парку."
                   :glossMismatch false
                   :structure
                   [{:usedForm "Leiter"
                     :dictionaryForm "die Leiter"
                     :translation "лестница"}
                    {:usedForm "läuft"
                     :dictionaryForm "laufen"
                     :translation "бежит"}
                    {:usedForm "schnell"
                     :dictionaryForm "schnell"
                     :translation "быстро"}
                    {:usedForm "Park"
                     :dictionaryForm "der Park"
                     :translation "парк"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (= :structure-mismatch
               (:issue (#'sut/example-issue "Leiter" ["лестница"] example))))))))


(deftest deterministic-example-issues-reject-used-form-at-wrong-word-index
  (testing "structure items must stay in left-to-right sentence order"
    (let [example {:value "Pass auf deine Seele auf."
                   :translation "Береги свою душу."
                   :glossMismatch false
                   :structure
                   [{:usedForm "auf"
                     :dictionaryForm "aufpassen"
                     :translation "беречь"}
                    {:usedForm "Pass"
                     :dictionaryForm "aufpassen"
                     :translation "беречь"}
                    {:usedForm "Seele"
                     :dictionaryForm "die Seele"
                     :translation "душа"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (= :structure-mismatch
               (:issue (#'sut/example-issue "aufpassen" ["беречь"] example))))))))


(deftest deterministic-example-issues-reject-duplicate-structure-items
  (testing "duplicate {usedForm, dictionaryForm} pairs reject the structure (e.g. a doubled separable-verb prefix mistaken for a preposition)"
    (let [example {:value "Pass auf deine Sachen auf!"
                   :translation "Береги свои вещи!"
                   :glossMismatch false
                   :structure
                   [{:usedForm "Pass"
                     :dictionaryForm "aufpassen"
                     :translation "беречь"}
                    {:usedForm "auf"
                     :dictionaryForm "aufpassen"
                     :translation "беречь"}
                    {:usedForm "Sachen"
                     :dictionaryForm "die Sache"
                     :translation "вещи"}
                    {:usedForm "auf"
                     :dictionaryForm "aufpassen"
                     :translation "беречь"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (= :structure-mismatch
               (:issue (#'sut/example-issue "aufpassen" ["беречь"] example))))))))


(deftest add-word-indexes-handles-last-separable-prefix
  (testing "backend assigns the final token index for a detached prefix near punctuation"
    (let [example {:value "Pass gut auf das kleine Kind auf!"
                   :translation "Присмотри внимательно за маленьким ребёнком!"
                   :glossMismatch false
                   :structure
                   [{:usedForm "Pass"
                     :dictionaryForm "aufpassen"
                     :translation "присматривать"}
                    {:usedForm "gut"
                     :dictionaryForm "gut"
                     :translation "хорошо"}
                    {:usedForm "Kind"
                     :dictionaryForm "das Kind"
                     :translation "ребёнок"}
                    {:usedForm "auf"
                     :dictionaryForm "aufpassen"
                     :translation "присматривать"}]}]
      (is (= [0 1 5 6]
             (mapv :wordIndex (:structure (#'sut/add-word-indexes example))))))))


(deftest valid-generated-example-rejects-mixed-language-translation
  (testing "translation must be predominantly Cyrillic, not mixed with English"
    (let [example {:value "Der Hund läuft schnell im Park."
                   :translation "The dog runs quickly in the park, собака."
                   :glossMismatch false
                   :structure
                   [{:usedForm "Hund"
                     :dictionaryForm "der Hund"
                     :translation "собака"}
                    {:usedForm "läuft"
                     :dictionaryForm "laufen"
                     :translation "бежит"}
                    {:usedForm "schnell"
                     :dictionaryForm "schnell"
                     :translation "быстро"}
                    {:usedForm "Park"
                     :dictionaryForm "der Park"
                     :translation "парк"}]}]
      (is (= :malformed-example
             (:issue (#'sut/example-issue "Hund" ["собака"] example)))))))


(deftest valid-generated-example-rejects-small-latin-tail-in-translation
  (testing "translation must not keep even a small Latin fragment"
    (let [example {:value "Der Hund läuft schnell im Park."
                   :translation "Собака бежит fast."
                   :glossMismatch false
                   :structure
                   [{:usedForm "Hund"
                     :dictionaryForm "der Hund"
                     :translation "собака"}
                    {:usedForm "läuft"
                     :dictionaryForm "laufen"
                     :translation "бежит"}
                    {:usedForm "schnell"
                     :dictionaryForm "schnell"
                     :translation "быстро"}
                    {:usedForm "Park"
                     :dictionaryForm "der Park"
                     :translation "парк"}]}]
      (is (= :malformed-example
             (:issue (#'sut/example-issue "Hund" ["собака"] example)))))))


(deftest valid-generated-example-rejects-latin-in-structure-translation
  (testing "structure item translation must not keep Latin fragments either"
    (let [example {:value "Der Hund läuft schnell im Park."
                   :translation "Собака быстро бежит по парку."
                   :glossMismatch false
                   :structure
                   [{:usedForm "Hund"
                     :dictionaryForm "der Hund"
                     :translation "собака"}
                    {:usedForm "läuft"
                     :dictionaryForm "laufen"
                     :translation "бежит"}
                    {:usedForm "schnell"
                     :dictionaryForm "schnell"
                     :translation "быстро fast"}
                    {:usedForm "Park"
                     :dictionaryForm "der Park"
                     :translation "парк"}]}]
      (is (= :malformed-example
             (:issue (#'sut/example-issue "Hund" ["собака"] example)))))))


(deftest valid-generated-example-rejects-cyrillic-in-german-sentence
  (testing "german sentence should not contain Cyrillic text"
    (let [example {:value "Der Hund бежит по парку."
                   :translation "Собака бежит по парку."
                   :glossMismatch false
                   :structure
                   [{:usedForm "Hund"
                     :dictionaryForm "der Hund"
                     :translation "собака"}
                    {:usedForm "Park"
                     :dictionaryForm "der Park"
                     :translation "парк"}]}]
      (is (= :malformed-example
             (:issue (#'sut/example-issue "Hund" ["собака"] example)))))))


(deftest valid-generated-example-rejects-multiple-sentences
  (testing "german example must contain exactly one sentence"
    (let [example {:value "Der Hund läuft. Er ist schnell."
                   :translation "Собака бежит. Она быстрая."
                   :glossMismatch false
                   :structure
                   [{:usedForm "Hund"
                     :dictionaryForm "der Hund"
                     :translation "собака"}
                    {:usedForm "läuft"
                     :dictionaryForm "laufen"
                     :translation "бежит"}
                    {:usedForm "schnell"
                     :dictionaryForm "schnell"
                     :translation "быстрый"}]}]
      (is (= :malformed-example
             (:issue (#'sut/example-issue "Hund" ["собака"] example)))))))


(deftest valid-generated-example-rejects-colon-prefixed-german-meta
  (testing "german value should look like a plain sentence, not a prefixed explanation"
    (let [example {:value "Sentence: Der Hund läuft im Park."
                   :translation "Собака бежит в парке."
                   :glossMismatch false
                   :structure
                   [{:usedForm "Hund"
                     :dictionaryForm "der Hund"
                     :translation "собака"}
                    {:usedForm "läuft"
                     :dictionaryForm "laufen"
                     :translation "бежит"}
                    {:usedForm "Park"
                     :dictionaryForm "der Park"
                     :translation "парк"}]}]
      (is (= :malformed-example
             (:issue (#'sut/example-issue "Hund" ["собака"] example)))))))


(deftest valid-generated-example-rejects-colon-prefixed-russian-meta
  (testing "russian translation should look like a plain sentence, not a prefixed explanation"
    (let [example {:value "Der Hund läuft im Park."
                   :translation "Перевод: Собака бежит в парке."
                   :glossMismatch false
                   :structure
                   [{:usedForm "Hund"
                     :dictionaryForm "der Hund"
                     :translation "собака"}
                    {:usedForm "läuft"
                     :dictionaryForm "laufen"
                     :translation "бежит"}
                    {:usedForm "Park"
                     :dictionaryForm "der Park"
                     :translation "парк"}]}]
      (is (= :malformed-example
             (:issue (#'sut/example-issue "Hund" ["собака"] example)))))))


(deftest deterministic-example-issues-allow-three-word-sentence
  (testing "simple three-word sentence is acceptable"
    (let [example {:value "Der Hund schläft."
                   :translation "Собака спит."
                   :glossMismatch false
                   :structure
                   [{:usedForm "Hund"
                     :dictionaryForm "der Hund"
                     :translation "собака"}
                    {:usedForm "schläft"
                     :dictionaryForm "schlafen"
                     :translation "спит"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (= nil
               (#'sut/example-issue
                "Hund"
                ["собака"]
                example)))))))


(deftest deterministic-example-issues-ignore-gloss-mismatch-flag
  (testing "glossMismatch alone does not reject an otherwise valid example"
    (let [example {:value "Die Leiter steht neben der Wand."
                   :translation "Лестница стоит рядом со стеной."
                   :glossMismatch true
                   :structure
                   [{:usedForm "Leiter"
                     :dictionaryForm "die Leiter"
                     :translation "лестница"}
                    {:usedForm "steht"
                     :dictionaryForm "stehen"
                     :translation "стоять"}
                    {:usedForm "Wand"
                     :dictionaryForm "die Wand"
                     :translation "стена"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (= nil
               (#'sut/example-issue
                "Leiter"
                ["лестница"]
                example)))))))


(deftest target-dictionary-form-allows-reflexive-lemma-match
  (testing "non-article target can match reflexive dictionary form"
    (let [example {:value "Er stellt sich heute freundlich vor."
                   :translation "Он сегодня дружелюбно представляется."
                   :glossMismatch false
                   :structure
                   [{:usedForm "stellt"
                     :dictionaryForm "sich vorstellen"
                     :translation "представляться"}
                    {:usedForm "heute"
                     :dictionaryForm "heute"
                     :translation "сегодня"}
                    {:usedForm "freundlich"
                     :dictionaryForm "freundlich"
                     :translation "дружелюбно"}
                    {:usedForm "vor"
                     :dictionaryForm "sich vorstellen"
                     :translation "представляться"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (= nil
               (#'sut/example-issue
                "vorstellen"
                ["представляться"]
                example)))))))


(deftest generate-one-retries-until-deterministic-checks-pass
  (testing "best-of-N style retries can skip a bad candidate and keep a later valid one"
    (let [bad-example {:value "Die Leiter."
                       :translation "Лестница стоит рядом со стеной."
                       :glossMismatch false
                       :structure
                       [{:usedForm "Leiter"
                         :dictionaryForm "die Leiter"
                         :translation "лестница"}]}
          good-example {:value "Die Leiter steht neben der Wand."
                        :translation "Лестница стоит рядом со стеной."
                        :glossMismatch false
                        :structure
                        [{:usedForm "Leiter"
                          :dictionaryForm "die Leiter"
                          :translation "лестница"}
                         {:usedForm "steht"
                          :dictionaryForm "stehen"
                          :translation "стоять"}
                         {:usedForm "Wand"
                          :dictionaryForm "die Wand"
                          :translation "стена"}]}
          responses (atom [bad-example good-example])
          retry-contexts (atom [])]
      (with-redefs [sut/generate-attempt!      (fn [_word _translation _word-meta retry-context]
                                                 (swap! retry-contexts conj retry-context)
                                                 (let [next-example (first @responses)]
                                                   (swap! responses subvec 1)
                                                   next-example))
                    dictionary/lookup-dictionary-entries (constantly nil)]
        (is (= (#'sut/add-word-indexes good-example)
               (sut/generate-one! {:word "Leiter" :translation "лестница"} 2)))
        (is (= [nil
                {:example bad-example
                 :issue   :sentence-length-out-of-range}]
               @retry-contexts))))))


(deftest generate-one-keeps-example-when-dictionary-gloss-disagrees
  (testing "dictionary gloss mismatch keeps the example and marks glossMismatch for the client"
    (let [example {:value "Pass auf deine Sachen auf!"
                   :translation "Береги свои вещи!"
                   :glossMismatch false
                   :structure
                   [{:usedForm "Pass"
                     :dictionaryForm "aufpassen"
                     :translation "беречь"}
                    {:usedForm "Sachen"
                     :dictionaryForm "die Sache"
                     :translation "вещи"}
                    {:usedForm "auf"
                     :dictionaryForm "aufpassen"
                     :translation "беречь"}]}
          calls (atom 0)]
      (with-redefs [sut/generate-attempt! (fn [_word _translation _word-meta _retry-context]
                                            (swap! calls inc)
                                            example)
                    dictionary/lookup-dictionary-entries
                    (fn [dictionary-form]
                      (case dictionary-form
                        "aufpassen" [{:_id "lemma:aufpassen:verb"
                                      :value "aufpassen"
                                      :translation [{:lang "ru" :value "присматривать"}]}]
                        "die Sache" [{:_id "lemma:die sache:noun"
                                      :value "die Sache"
                                      :translation [{:lang "ru" :value "вещь"}]}]
                        []))]
        (is (= (assoc (#'sut/add-word-indexes example) :glossMismatch true)
               (sut/generate-one! {:word "aufpassen" :translation "беречь"} 3)))
        (is (= 1 @calls))))))


(deftest dictionary-gloss-validation-allows-overlapping-translation-variants
  (testing "validation accepts when any supplied gloss matches the dictionary entry"
    (with-redefs [dictionary/lookup-dictionary-entries
                  (fn [_dictionary-form]
                    [{:_id "lemma:die leiter:noun"
                      :value "die Leiter"
                      :translation [{:lang "ru" :value "лестница"}]}])]
      (is (true? (dictionary/word-gloss-valid?
                  "Leiter"
                  ["лестница" "стремянка"]
                  [{:usedForm "Leiter"
                    :dictionaryForm "die Leiter"
                    :translation "лестница"}]))))))


(deftest generate-one-accepts-map-input
  (testing "single-item API accepts {:word :translation} input"
    (let [example {:value "Die Leiter steht neben der Wand."
                   :translation "Лестница стоит рядом со стеной."
                   :glossMismatch false
                   :structure
                   [{:usedForm "Leiter"
                     :dictionaryForm "die Leiter"
                     :translation "лестница"}]}]
      (with-redefs [sut/generate-attempt! (fn [_word _translation _word-meta _retry-context]
                                            example)
                    dictionary/lookup-dictionary-entries (constantly nil)]
        (is (= (#'sut/add-word-indexes example)
               (sut/generate-one! {:word "Leiter" :translation "лестница"} 1)))))))


(deftest lookup-dictionary-entries-uses-live-dictionary-db
  (testing "target validation reads dictionary entries from dictionary-db"
    (let [captured (atom nil)]
      (with-redefs [db/request-sync (fn [request]
                                      (reset! captured request)
                                      {:status 200
                                       :body   {:docs [{:value "der Schrank"
                                                        :translation [{:lang "ru"
                                                                       :value "шкаф"}]}]}})]
        (let [entries (#'dictionary/lookup-dictionary-entries "der Schrank")]
          (is (= "dictionary-db/_find" (:url @captured)))
          (is (= "dictionary-entry"
                 (get-in @captured [:body :selector "type"])))
          (is (= "der schrank"
                 (get-in @captured [:body :selector "meta.normalized_value"])))
          (is (seq entries))
          (is (= "der Schrank" (:value (first entries)))))))))


(deftest lookup-dictionary-entries-falls-back-to-surface-form-lemma-docs
  (testing "bare words can resolve through surface-form docs to article-bearing lemma docs"
    (let [captured (atom [])]
      (with-redefs [db/request-sync (fn [request]
                                      (swap! captured conj request)
                                      (case (:url request)
                                        "dictionary-db/_find"
                                        {:status 200
                                         :body   {:docs []}}

                                        "dictionary-db/sf:fenster"
                                        {:status 200
                                         :body   {:entries [{:lemma-id "lemma:das fenster:noun"
                                                             :lemma    "das Fenster"}]}}

                                        "dictionary-db/_all_docs"
                                        {:status 200
                                         :body   {:rows [{:id  "lemma:das fenster:noun"
                                                          :doc {:_id         "lemma:das fenster:noun"
                                                                :type        "dictionary-entry"
                                                                :value       "das Fenster"
                                                                :translation [{:lang "ru"
                                                                               :value "окно"}]}}]}}

                                        (throw (ex-info "unexpected request" request))))]
        (let [entries (#'dictionary/lookup-dictionary-entries "Fenster")]
          (is (= ["dictionary-db/_find"
                  "dictionary-db/sf:fenster"
                  "dictionary-db/_all_docs"]
                 (mapv :url @captured)))
          (is (= ["lemma:das fenster:noun"]
                 (get-in (last @captured) [:body :keys])))
          (is (= ["das Fenster"]
                 (mapv :value entries))))))))


(deftest generate-one-logs-transport-errors
  (testing "transport exceptions are logged and returned as nil"
    (let [logged (atom nil)]
      (with-redefs [sut/example-api-request (fn [_word _translation _word-meta _retry-context]
                                                (delay (throw (ex-info "network down" {:status 0}))))
                    sut/log-generation-failure! (fn [data]
                                                  (swap! logged conj data))]
        (is (nil? (sut/generate-one! {:word "Hund" :translation "собака"} 1)))
        (is (some #(= "Hund" (:word %)) @logged))
        (is (some #(= :transport (:context %)) @logged))
        (is (some #(= "network down" (:error %)) @logged))))))


(deftest generate-one-does-not-retry-on-rate-limit
  (testing "rate-limited upstream responses stop immediately instead of stretching into route timeouts"
    (let [calls (atom 0)
          logged (atom [])]
      (with-redefs [sut/example-api-request (fn [_word _translation _word-meta _retry-context]
                                                (swap! calls inc)
                                                (delay {:status 429
                                                        :body "{\"error\":{\"message\":\"Rate limit exceeded\",\"type\":\"tokens\",\"code\":\"rate_limit_exceeded\"}}"}))
                    sut/log-generation-failure! (fn [data]
                                                  (swap! logged conj data))]
        (is (sut/generation-failure? (sut/generate-one! {:word "Leiter" :translation "лестница"} 3)))
        (is (= 1 @calls))
        (is (= 1 (count @logged)))
        (let [entry (first @logged)]
          (is (map? entry))
          (is (= "Leiter" (:word entry)))
          (is (= 429 (:status entry)))
          (is (contains? entry :body))
          (is (string? (:body entry))))))))

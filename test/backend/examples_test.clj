(ns backend.examples-test
  (:require
   [backend.support.generation :as support.generation]
   [cheshire.core :as cheshire]
   [clojure.test :refer [deftest is testing]]
   [db :as db]
   [examples :as sut]
   [examples.dictionary :as dictionary]
   [examples.provider :as provider]
   [malli.core :as m]
   [org.httpkit.client :as client]
   [taoensso.telemere :as t]))


(deftest example-api-request-uses-openrouter-defaults
  (testing "OpenRouter is the default transport and model path"
    (let [captured (atom nil)]
      (with-redefs [client/request  (fn [request]
                                      (reset! captured request)
                                      ::request)
                    provider/config (constantly {:api-url "https://api.groq.com/openai/v1/chat/completions"
                                                 :api-key "groq-test-key"
                                                 :model   "openai/gpt-oss-20b"})]
        (is (= ::request (sut/example-api-request "Hund" [] nil nil nil)))
        (let [{:keys [url method headers body]} @captured
              payload (cheshire/parse-string body true)]
          (is (= "https://api.groq.com/openai/v1/chat/completions" url))
          (is (= :post method))
          (is (= "Bearer groq-test-key" (get headers "Authorization")))
          (is (= "application/json" (get headers "Content-Type")))
          (is (= "openai/gpt-oss-20b" (:model payload)))
          (is (= 300 (:max_tokens payload)))
          (is (= "json_schema" (get-in payload [:response_format :type])))
          (is (= ["value" "translation" "structure"]
                 (get-in payload [:response_format :json_schema :schema :required])))
          (is (= "array"
                 (get-in payload [:response_format :json_schema :schema :properties :structure :type])))
          (is (= "object"
                 (get-in payload [:response_format :json_schema :schema :properties :structure :items :type])))
          (is (= ["usedForm" "dictionaryForm" "translation"]
                 (get-in payload [:response_format :json_schema :schema :properties :structure :items :required])))
          (is (nil?
               (get-in payload
                       [:response_format :json_schema :schema :properties :structure :items :properties :wordIndex])))
          (is (= "sentence_example" (get-in payload [:response_format :json_schema :name])))
          (is (string? (get-in payload [:messages 0 :content])))
          (is (re-find #"learner-facing German example sentences"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"part of speech"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"Er passt auf die Kinder auf"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"Separable verbs emit the prefix exactly once"
                       (get-in payload [:messages 0 :content])))
          (is (re-find
               #"A `usedForm` is exactly one word of the sentence, spelled as the sentence spells it"
               (get-in payload [:messages 0 :content])))
          (is (re-find
               #"the sentence is where such an expression lives, not `structure`"
               (get-in payload [:messages 0 :content])))
          (is (re-find #"Von Zeit zu Zeit besuche ich meine Eltern"
                       (get-in payload [:messages 0 :content]))
              "the phrase shape has a few-shot case of its own")
          (is (re-find #"Never use the whole phrase as a `dictionaryForm`"
                       (get-in payload [:messages 0 :content])))
          (is (not (re-find #"every word of the phrase gets its own item"
                            (get-in payload [:messages 0 :content])))
              "membership attribution is gone from the prompt")
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
      (with-redefs [client/request  (fn [request]
                                      (reset! captured request)
                                      ::request)
                    provider/config (constantly {:api-url "https://example.test/openai/v1/chat/completions"
                                                 :api-key "openai-override-key"
                                                 :model   "openai/gpt-oss-120b"})]
        (is (= ::request (sut/example-api-request "Hund" ["собака"] nil nil nil)))
        (let [{:keys [url headers body]} @captured
              payload (cheshire/parse-string body true)]
          (is (= "https://example.test/openai/v1/chat/completions" url))
          (is (= "Bearer openai-override-key" (get headers "Authorization")))
          (is (= "openai/gpt-oss-120b" (:model payload)))
          (is (= 300 (:max_tokens payload)))
          (is (= ["value" "translation" "structure"]
                 (get-in payload [:response_format :json_schema :schema :required])))
          (is (= ["usedForm" "dictionaryForm" "translation"]
                 (get-in payload [:response_format :json_schema :schema :properties :structure :items :required])))
          (is (re-find #"собака"
                       (get-in payload [:messages 1 :content]))))))))


(deftest example-api-request-allows-max-tokens-env-override
  (testing "max tokens can be overridden for example generation requests"
    (let [captured (atom nil)]
      (with-redefs [client/request  (fn [request]
                                      (reset! captured request)
                                      ::request)
                    provider/config (constantly {:api-url "https://example.test/openai/v1/chat/completions"
                                                 :api-key "openai-override-key"
                                                 :model   "openai/gpt-oss-120b"})]
        (with-redefs [sut/example-max-tokens (constantly 180)]
          (is (= ::request (sut/example-api-request "Hund" ["собака"] nil nil nil)))
          (let [payload (cheshire/parse-string (:body @captured) true)]
            (is (= 180 (:max_tokens payload)))))))))


(defn- user-payload
  "The JSON object the user message of a captured request carries."
  [request]
  (let [content (get-in (cheshire/parse-string (:body request) true) [:messages 1 :content])]
    (cheshire/parse-string (re-find #"(?m)^\{.*\}$" content) true)))


(deftest example-api-request-serializes-translations-as-array
  (testing "user prompt sends every confirmed translation so the model can pick the fitting sense"
    (let [captured (atom nil)]
      (with-redefs [client/request  (fn [request]
                                      (reset! captured request)
                                      ::request)
                    provider/config (constantly {:api-url "https://api.groq.com/openai/v1/chat/completions"
                                                 :api-key "groq-test-key"
                                                 :model   "openai/gpt-oss-20b"})]
        (is (= ::request (sut/example-api-request "Bank" ["банк" "скамейка"] nil nil nil)))
        (let [asked (user-payload @captured)]
          (is (= ["банк" "скамейка"] (:translation asked)))
          (is (= "Bank" (:word asked))))))))


(deftest example-api-request-carries-the-collection-context
  (testing "the collection a word is learned in reaches the prompt; the main card sends none"
    (let [captured (atom nil)]
      (with-redefs [client/request  (fn [request]
                                      (reset! captured request)
                                      ::request)
                    provider/config (constantly {:api-url "https://api.groq.com/openai/v1/chat/completions"
                                                 :api-key "groq-test-key"
                                                 :model   "openai/gpt-oss-20b"})]
        (sut/example-api-request "Bank" ["скамейка"] "Park" nil nil)
        (is (= "Park" (:context (user-payload @captured))))
        (sut/example-api-request "Bank" ["скамейка"] "   " nil nil)
        (is (nil? (:context (user-payload @captured))) "a blank context is not a context")
        (sut/example-api-request "Bank" ["скамейка"] nil nil nil)
        (is (nil? (:context (user-payload @captured))))))))


(deftest example-api-request-includes-retry-feedback
  (testing "retry attempts include the rejected example and issue list in the user prompt"
    (let [captured (atom nil)
          rejected-example {:value "Der Leiter steht neben der Wand."
                            :translation "Лестница стоит рядом со стеной."
                            :structure
                            [{:usedForm       "Leiter"
                              :dictionaryForm "der Leiter"
                              :translation    "лестница"}]}]
      (with-redefs [client/request  (fn [request]
                                      (reset! captured request)
                                      ::request)
                    provider/config (constantly {:api-url "https://api.groq.com/openai/v1/chat/completions"
                                                 :api-key "groq-test-key"
                                                 :model   "openai/gpt-oss-20b"})]
        (is (= ::request
               (sut/example-api-request
                "Leiter"
                ["лестница"]
                nil
                nil
                {:example rejected-example
                 :issue   :structure-mismatch})))
        (let [payload      (cheshire/parse-string (:body @captured) true)
              user-message (get-in payload [:messages 1 :content])]
          (is (re-find #"previousAttempt" user-message))
          (is (re-find #"previousIssue" user-message))
          (is (re-find #"structure-mismatch" user-message))
          (is (re-find #"Items in `structure` must appear in strict left-to-right order" user-message))
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
                      :structure
                      [{:usedForm       "aufstehen"
                        :dictionaryForm "aufstehen"
                        :translation    "to stand up"}]})}}]})]
      (with-redefs [sut/example-api-request (fn [_word _translation _context _word-meta _retry-context]
                                              (support.generation/answered {:status 200 :body body}))
                    dictionary/lookup-dictionary-entries (constantly nil)]
        (is (nil? (sut/generate-one! (sut/subject {:word "aufstehen" :translation "вставать"}) nil)))))))


(deftest deterministic-example-issues-stop-at-structural-invalidity
  (testing "structurally invalid examples return only the structural issue"
    (let [example {:value "The example for 'aufstehen' is ..."
                   :translation "to stand up"
                   :structure
                   [{:usedForm       "aufstehen"
                     :dictionaryForm "aufstehen"
                     :translation    "to stand up"}]}]
      (is (= :malformed-example
             (:issue (#'sut/example-issue "aufstehen" example)))))))


(deftest deterministic-example-issues-reject-target-present-only-in-structure
  (testing "target forms mentioned only in structure do not count as present in the sentence"
    (let [example {:value "Der Hund läuft schnell im Park."
                   :translation "Собака быстро бежит по парку."
                   :structure
                   [{:usedForm       "Leiter"
                     :dictionaryForm "die Leiter"
                     :translation    "лестница"}
                    {:usedForm       "läuft"
                     :dictionaryForm "laufen"
                     :translation    "бежит"}
                    {:usedForm       "schnell"
                     :dictionaryForm "schnell"
                     :translation    "быстро"}
                    {:usedForm       "Park"
                     :dictionaryForm "der Park"
                     :translation    "парк"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (= :structure-mismatch
               (:issue (#'sut/example-issue "Leiter" example))))))))


(deftest deterministic-example-issues-reject-used-form-at-wrong-word-index
  (testing "structure items must stay in left-to-right sentence order"
    (let [example {:value "Pass auf deine Seele auf."
                   :translation "Береги свою душу."
                   :structure
                   [{:usedForm       "auf"
                     :dictionaryForm "aufpassen"
                     :translation    "беречь"}
                    {:usedForm       "Pass"
                     :dictionaryForm "aufpassen"
                     :translation    "беречь"}
                    {:usedForm       "Seele"
                     :dictionaryForm "die Seele"
                     :translation    "душа"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (= :structure-mismatch
               (:issue (#'sut/example-issue "aufpassen" example))))))))


(deftest a-doubled-separable-prefix-is-accepted-and-costs-a-tooltip
  (testing
    "the pair guard is gone: with `structure` annotating words, a doubled separable prefix and a word the sentence genuinely says twice are the same shape"
    (let [example {:value "Pass auf deine Sachen auf!"
                   :translation "Береги свои вещи!"
                   :structure
                   [{:usedForm       "Pass"
                     :dictionaryForm "aufpassen"
                     :translation    "беречь"}
                    {:usedForm       "auf"
                     :dictionaryForm "aufpassen"
                     :translation    "беречь"}
                    {:usedForm       "Sachen"
                     :dictionaryForm "die Sache"
                     :translation    "вещи"}
                    {:usedForm       "auf"
                     :dictionaryForm "aufpassen"
                     :translation    "беречь"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is
         (nil? (:issue (#'sut/example-issue "aufpassen" example)))
         "the preposition wrongly annotated with the verb's gloss is the cost; rejecting this also rejected every `von Zeit zu Zeit`")
        (is (= [0 1 3 4]
               (mapv :wordIndex (:structure (#'sut/add-word-indexes example)))))))))


(deftest add-word-indexes-handles-last-separable-prefix
  (testing "backend assigns the final token index for a detached prefix near punctuation"
    (let [example {:value "Pass gut auf das kleine Kind auf!"
                   :translation "Присмотри внимательно за маленьким ребёнком!"
                   :structure
                   [{:usedForm       "Pass"
                     :dictionaryForm "aufpassen"
                     :translation    "присматривать"}
                    {:usedForm       "gut"
                     :dictionaryForm "gut"
                     :translation    "хорошо"}
                    {:usedForm       "Kind"
                     :dictionaryForm "das Kind"
                     :translation    "ребёнок"}
                    {:usedForm       "auf"
                     :dictionaryForm "aufpassen"
                     :translation    "присматривать"}]}]
      (is (= [0 1 5 6]
             (mapv :wordIndex (:structure (#'sut/add-word-indexes example))))))))


(deftest valid-generated-example-rejects-mixed-language-translation
  (testing "translation must be predominantly Cyrillic, not mixed with English"
    (let [example {:value "Der Hund läuft schnell im Park."
                   :translation "The dog runs quickly in the park, собака."
                   :structure
                   [{:usedForm       "Hund"
                     :dictionaryForm "der Hund"
                     :translation    "собака"}
                    {:usedForm       "läuft"
                     :dictionaryForm "laufen"
                     :translation    "бежит"}
                    {:usedForm       "schnell"
                     :dictionaryForm "schnell"
                     :translation    "быстро"}
                    {:usedForm       "Park"
                     :dictionaryForm "der Park"
                     :translation    "парк"}]}]
      (is (= :malformed-example
             (:issue (#'sut/example-issue "Hund" example)))))))


(deftest valid-generated-example-rejects-small-latin-tail-in-translation
  (testing "translation must not keep even a small Latin fragment"
    (let [example {:value "Der Hund läuft schnell im Park."
                   :translation "Собака бежит fast."
                   :structure
                   [{:usedForm       "Hund"
                     :dictionaryForm "der Hund"
                     :translation    "собака"}
                    {:usedForm       "läuft"
                     :dictionaryForm "laufen"
                     :translation    "бежит"}
                    {:usedForm       "schnell"
                     :dictionaryForm "schnell"
                     :translation    "быстро"}
                    {:usedForm       "Park"
                     :dictionaryForm "der Park"
                     :translation    "парк"}]}]
      (is (= :malformed-example
             (:issue (#'sut/example-issue "Hund" example)))))))


(deftest valid-generated-example-rejects-latin-in-structure-translation
  (testing "structure item translation must not keep Latin fragments either"
    (let [example {:value "Der Hund läuft schnell im Park."
                   :translation "Собака быстро бежит по парку."
                   :structure
                   [{:usedForm       "Hund"
                     :dictionaryForm "der Hund"
                     :translation    "собака"}
                    {:usedForm       "läuft"
                     :dictionaryForm "laufen"
                     :translation    "бежит"}
                    {:usedForm       "schnell"
                     :dictionaryForm "schnell"
                     :translation    "быстро fast"}
                    {:usedForm       "Park"
                     :dictionaryForm "der Park"
                     :translation    "парк"}]}]
      (is (= :malformed-example
             (:issue (#'sut/example-issue "Hund" example)))))))


(deftest valid-generated-example-rejects-cyrillic-in-german-sentence
  (testing "german sentence should not contain Cyrillic text"
    (let [example {:value "Der Hund бежит по парку."
                   :translation "Собака бежит по парку."
                   :structure
                   [{:usedForm       "Hund"
                     :dictionaryForm "der Hund"
                     :translation    "собака"}
                    {:usedForm       "Park"
                     :dictionaryForm "der Park"
                     :translation    "парк"}]}]
      (is (= :malformed-example
             (:issue (#'sut/example-issue "Hund" example)))))))


(deftest valid-generated-example-rejects-multiple-sentences
  (testing "german example must contain exactly one sentence"
    (let [example {:value "Der Hund läuft. Er ist schnell."
                   :translation "Собака бежит. Она быстрая."
                   :structure
                   [{:usedForm       "Hund"
                     :dictionaryForm "der Hund"
                     :translation    "собака"}
                    {:usedForm       "läuft"
                     :dictionaryForm "laufen"
                     :translation    "бежит"}
                    {:usedForm       "schnell"
                     :dictionaryForm "schnell"
                     :translation    "быстрый"}]}]
      (is (= :malformed-example
             (:issue (#'sut/example-issue "Hund" example)))))))


(deftest valid-generated-example-rejects-colon-prefixed-german-meta
  (testing "german value should look like a plain sentence, not a prefixed explanation"
    (let [example {:value "Sentence: Der Hund läuft im Park."
                   :translation "Собака бежит в парке."
                   :structure
                   [{:usedForm       "Hund"
                     :dictionaryForm "der Hund"
                     :translation    "собака"}
                    {:usedForm       "läuft"
                     :dictionaryForm "laufen"
                     :translation    "бежит"}
                    {:usedForm       "Park"
                     :dictionaryForm "der Park"
                     :translation    "парк"}]}]
      (is (= :malformed-example
             (:issue (#'sut/example-issue "Hund" example)))))))


(deftest valid-generated-example-rejects-colon-prefixed-russian-meta
  (testing "russian translation should look like a plain sentence, not a prefixed explanation"
    (let [example {:value "Der Hund läuft im Park."
                   :translation "Перевод: Собака бежит в парке."
                   :structure
                   [{:usedForm       "Hund"
                     :dictionaryForm "der Hund"
                     :translation    "собака"}
                    {:usedForm       "läuft"
                     :dictionaryForm "laufen"
                     :translation    "бежит"}
                    {:usedForm       "Park"
                     :dictionaryForm "der Park"
                     :translation    "парк"}]}]
      (is (= :malformed-example
             (:issue (#'sut/example-issue "Hund" example)))))))


(deftest a-multi-word-target-is-found-in-the-sentence
  (testing "`structure` annotates words, so the construction is checked against the sentence"
    (let [example {:value       "Ich komme auf jeden Fall mit."
                   :translation "Я обязательно пойду вместе."
                   :structure   [{:usedForm       "komme"
                                  :dictionaryForm "mitkommen"
                                  :translation    "идти вместе"}
                                 {:usedForm       "Fall"
                                  :dictionaryForm "der Fall"
                                  :translation    "случай"}
                                 {:usedForm       "mit"
                                  :dictionaryForm "mitkommen"
                                  :translation    "идти вместе"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (nil? (#'sut/example-issue "auf jeden Fall" example))
            "auf, jeden and Fall are all words of the sentence")
        (is (= [1 4 5]
               (mapv :wordIndex (:structure (#'sut/add-word-indexes example))))
            "nothing in the structure says those words were the target")))))


(deftest an-inflected-target-word-is-found-through-its-dictionary-form
  (testing "`verlieren` is in `Er verliert den Kopf.` only through the item that names it"
    (let [example {:value       "Er verliert den Kopf."
                   :translation "Он теряет голову."
                   :structure   [{:usedForm       "verliert"
                                  :dictionaryForm "verlieren"
                                  :translation    "терять"}
                                 {:usedForm       "Kopf"
                                  :dictionaryForm "der Kopf"
                                  :translation    "голова"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (nil? (#'sut/example-issue "den Kopf verlieren" example)))))))


(deftest a-word-the-sentence-says-twice-no-longer-costs-the-example
  (testing "two identical items are ordinary German, not a mistake to reject"
    (let [example {:value       "Von Zeit zu Zeit besuche ich meine Eltern."
                   :translation "Время от времени я навещаю родителей."
                   :structure   [{:usedForm       "Zeit"
                                  :dictionaryForm "die Zeit"
                                  :translation    "время"}
                                 {:usedForm       "Zeit"
                                  :dictionaryForm "die Zeit"
                                  :translation    "время"}
                                 {:usedForm       "besuche"
                                  :dictionaryForm "besuchen"
                                  :translation    "навещать"}
                                 {:usedForm       "Eltern"
                                  :dictionaryForm "die Eltern"
                                  :translation    "родители"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (nil? (#'sut/example-issue "von Zeit zu Zeit" example)))
        (is (= [1 3 4 7]
               (mapv :wordIndex (:structure (#'sut/add-word-indexes example))))
            "each occurrence indexes its own position")))))


(deftest a-multi-word-target-missing-from-the-sentence-is-rejected
  (testing "the construction must be in the sentence — every word of it"
    (let [example {:value       "Ich besuche meine Eltern."
                   :translation "Я навещаю своих родителей."
                   :structure   [{:usedForm       "besuche"
                                  :dictionaryForm "besuchen"
                                  :translation    "навещать"}
                                 {:usedForm       "Eltern"
                                  :dictionaryForm "die Eltern"
                                  :translation    "родители"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (= :target-lemma-missing
               (:issue (#'sut/example-issue "von Zeit zu Zeit" example))))))))


(deftest a-partly-present-construction-is-rejected
  (testing "most of the words is not the construction"
    (let [example {:value       "Auf dem Tisch liegt ein Buch."
                   :translation "На столе лежит книга."
                   :structure   [{:usedForm       "Tisch"
                                  :dictionaryForm "der Tisch"
                                  :translation    "стол"}
                                 {:usedForm       "liegt"
                                  :dictionaryForm "liegen"
                                  :translation    "лежать"}
                                 {:usedForm       "Buch"
                                  :dictionaryForm "das Buch"
                                  :translation    "книга"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (= :target-lemma-missing
               (:issue (#'sut/example-issue "auf jeden Fall" example)))
            "`auf` is there, `jeden` and `Fall` are not")))))


(deftest an-article-pair-target-passes-on-the-sentence-when-structure-cannot-name-it
  (testing "`das heißt` is a construction the shape rule reads as an article pair"
    (let [example {:value       "Es regnet, das heißt, wir bleiben zu Hause."
                   :translation "Идёт дождь, то есть мы остаёмся дома."
                   :structure   [{:usedForm       "regnet"
                                  :dictionaryForm "regnen"
                                  :translation    "идти дождю"}
                                 {:usedForm       "heißt"
                                  :dictionaryForm "heißen"
                                  :translation    "значить"}
                                 {:usedForm       "bleiben"
                                  :dictionaryForm "bleiben"
                                  :translation    "оставаться"}
                                 {:usedForm       "Hause"
                                  :dictionaryForm "das Haus"
                                  :translation    "дом"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (nil? (#'sut/example-issue "das heißt" example)))))))


(def ^:private eight-word-phrase
  "Ich habe damit überhaupt nichts zu tun gehabt")


(deftest sentence-length-floats-with-the-target
  (testing "a long phrase gets the room the flat twelve-word ceiling refused"
    (let [long-example  {:value "Ich habe damit überhaupt nichts zu tun gehabt, sagte er dem wartenden Lehrer leise."
                         :translation "Я к этому совершенно не имел отношения, тихо сказал он ждущему учителю."
                         :structure [{:usedForm       "habe"
                                      :dictionaryForm "haben"
                                      :translation    "иметь"}
                                     {:usedForm       "gehabt"
                                      :dictionaryForm "haben"
                                      :translation    "иметь"}
                                     {:usedForm       "Lehrer"
                                      :dictionaryForm "der Lehrer"
                                      :translation    "учитель"}]}
          short-example {:value       "Der Hund."
                         :translation "Собака."
                         :structure   [{:usedForm       "Hund"
                                        :dictionaryForm "der Hund"
                                        :translation    "собака"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (nil? (#'sut/example-issue eight-word-phrase long-example))
            "fourteen words around an eight-word target is inside the floating ceiling")
        (is (= :sentence-length-out-of-range
               (:issue (#'sut/example-issue "Hund" long-example)))
            "the same sentence is still too long for a one-word target")
        (is (= :sentence-length-out-of-range
               (:issue (#'sut/example-issue "Hund" short-example)))
            "the floor stays where it was")))))


(deftest deterministic-example-issues-allow-three-word-sentence
  (testing "simple three-word sentence is acceptable"
    (let [example {:value "Der Hund schläft."
                   :translation "Собака спит."
                   :structure
                   [{:usedForm       "Hund"
                     :dictionaryForm "der Hund"
                     :translation    "собака"}
                    {:usedForm       "schläft"
                     :dictionaryForm "schlafen"
                     :translation    "спит"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (= nil
               (#'sut/example-issue
                "Hund"
                example)))))))


(deftest target-dictionary-form-allows-reflexive-lemma-match
  (testing "non-article target can match reflexive dictionary form"
    (let [example {:value "Er stellt sich heute freundlich vor."
                   :translation "Он сегодня дружелюбно представляется."
                   :structure
                   [{:usedForm       "stellt"
                     :dictionaryForm "sich vorstellen"
                     :translation    "представляться"}
                    {:usedForm       "heute"
                     :dictionaryForm "heute"
                     :translation    "сегодня"}
                    {:usedForm       "freundlich"
                     :dictionaryForm "freundlich"
                     :translation    "дружелюбно"}
                    {:usedForm       "vor"
                     :dictionaryForm "sich vorstellen"
                     :translation    "представляться"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (= nil
               (#'sut/example-issue
                "vorstellen"
                example)))))))


(deftest generate-one-retries-until-deterministic-checks-pass
  (testing "best-of-N style retries can skip a bad candidate and keep a later valid one"
    (let [bad-example    {:value "Die Leiter."
                          :translation "Лестница стоит рядом со стеной."
                          :structure
                          [{:usedForm       "Leiter"
                            :dictionaryForm "die Leiter"
                            :translation    "лестница"}]}
          good-example   {:value "Die Leiter steht neben der Wand."
                          :translation "Лестница стоит рядом со стеной."
                          :structure
                          [{:usedForm       "Leiter"
                            :dictionaryForm "die Leiter"
                            :translation    "лестница"}
                           {:usedForm       "steht"
                            :dictionaryForm "stehen"
                            :translation    "стоять"}
                           {:usedForm       "Wand"
                            :dictionaryForm "die Wand"
                            :translation    "стена"}]}
          responses      (atom [bad-example good-example])
          retry-contexts (atom [])]
      (with-redefs [sut/generate-attempt! (fn [_word _translation _context _word-meta retry-context]
                                            (swap! retry-contexts conj retry-context)
                                            (let [next-example (first @responses)]
                                              (swap! responses subvec 1)
                                              next-example))
                    dictionary/lookup-dictionary-entries (constantly nil)]
        (is (= (#'sut/add-word-indexes good-example)
               (sut/generate-one! (sut/subject {:word "Leiter" :translation "лестница"}) nil 2)))
        (is (= [nil
                {:example bad-example
                 :details {:max 12 :min 3}
                 :issue   :sentence-length-out-of-range}]
               @retry-contexts))))))


(deftest generate-one-accepts-map-input
  (testing "single-item API accepts {:word :translation} input"
    (let [example {:value "Die Leiter steht neben der Wand."
                   :translation "Лестница стоит рядом со стеной."
                   :structure
                   [{:usedForm       "Leiter"
                     :dictionaryForm "die Leiter"
                     :translation    "лестница"}]}]
      (with-redefs [sut/generate-attempt! (fn [_word _translation _context _word-meta _retry-context]
                                            example)
                    dictionary/lookup-dictionary-entries (constantly nil)]
        (is (= (#'sut/add-word-indexes example)
               (sut/generate-one! (sut/subject {:word "Leiter" :translation "лестница"}) nil 1)))))))


(deftest every-shape-of-subject-is-a-subject
  (testing "each branch of what a caller may send normalizes into one shape"
    (doseq [asked [{:word "Hund"}
                   {:word "Hund" :translation "собака"}
                   {:word "Hund" :translation ["собака" "пёс"]}
                   {:word "Hund" :translation ["  собака " "   "]}
                   {:word " Hund " :translation nil :context "   "}
                   {:word "Hund" :translation [] :context "Tiere"}]]
      (is (m/validate sut/subject-schema (sut/subject asked))
          (str "not a subject: " (pr-str asked))))))


(deftest a-subject-reads-what-the-caller-sent
  (testing "one gloss as a bare string"
    (is (= ["собака"] (:translations (sut/subject {:word "Hund" :translation "собака"})))))
  (testing "several as a collection, sorted — their order is this device's, not anyone's"
    (is (= ["пёс" "собака"]
           (:translations (sut/subject {:word "Hund" :translation ["собака" "пёс"]})))))
  (testing "none at all"
    (is (= [] (:translations (sut/subject {:word "Hund"})))))
  (testing "blanks and duplicates are not glosses"
    (is (= ["собака"]
           (:translations (sut/subject {:word "Hund" :translation [" собака " "   " "собака"]})))))
  (testing "the word keeps its case and loses its spacing"
    (is (= "Hund" (:word (sut/subject {:word " Hund "})))))
  (testing "a blank context is no context"
    (is (nil? (:context (sut/subject {:word "Hund" :context "   "}))))
    (is (= "Tiere" (:context (sut/subject {:word "Hund" :context " Tiere "}))))))


(deftest lookup-dictionary-entries-uses-live-dictionary-db
  (testing "target validation reads dictionary entries from dictionary-db"
    (let [captured (atom nil)]
      (with-redefs [db/request-sync (fn [request]
                                      (reset! captured request)
                                      {:status 200
                                       :body   {:docs [{:value       "der Schrank"
                                                        :translation [{:lang  "ru"
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
                                                                :translation [{:lang  "ru"
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
  (testing "transport exceptions are logged and returned as the provider's failure"
    (let [logged (atom nil)]
      (with-redefs [sut/example-api-request     (fn [_word _translation _context _word-meta _retry-context]
                                                  (throw (ex-info "network down" {:status 0})))
                    sut/log-generation-failure! (fn [data]
                                                  (swap! logged conj data))]
        (is (sut/generation-failure? (sut/generate-one! (sut/subject {:word "Hund" :translation "собака"}) nil 1)))
        (is (some #(= "Hund" (:word %)) @logged))
        (is (some #(= :failure/transport (:context %)) @logged))
        (is (some #(= "network down" (:error %)) @logged))))))


(deftest generate-one-does-not-retry-on-rate-limit
  (testing "rate-limited upstream responses stop immediately instead of stretching into route timeouts"
    (let [calls  (atom 0)
          logged (atom [])]
      (with-redefs
        [sut/example-api-request
         (fn [_word _translation _context _word-meta _retry-context]
           (swap! calls inc)
           (support.generation/answered
            {:status 429
             :body
             "{\"error\":{\"message\":\"Rate limit exceeded\",\"type\":\"tokens\",\"code\":\"rate_limit_exceeded\"}}"}))
         sut/log-generation-failure! (fn [data]
                                       (swap! logged conj data))]
        (is (sut/generation-failure? (sut/generate-one! (sut/subject {:word "Leiter" :translation "лестница"}) nil 3)))
        (is (= 1 @calls))
        (is (= 1 (count @logged)))
        (let [entry (first @logged)]
          (is (map? entry))
          (is (= "Leiter" (:word entry)))
          (is (= 429 (:status entry)))
          (is (contains? entry :body))
          (is (string? (:body entry))))))))


(deftest ensure-word-lookup-index-creates-the-index-the-lookup-reads-by
  (testing "boot asks dictionary-db for the index the word lookup selects through"
    (let [captured (atom nil)]
      (with-redefs [db/request-sync (fn [request]
                                      (reset! captured request)
                                      {:status 200
                                       :body   {:result "created"}})]
        (dictionary/ensure-word-lookup-index!)
        (is (= :post (:method @captured)))
        (is (= "dictionary-db/_index" (:url @captured)))
        (is (= ["type" "meta.normalized_value"]
               (get-in @captured [:body :index :fields])))
        (is (= "json" (get-in @captured [:body :type])))))))


(deftest ensure-word-lookup-index-survives-an-unreachable-dictionary
  (testing "a dictionary database that does not answer is logged, not thrown"
    (with-redefs [db/request-sync (fn [_request]
                                    (throw (ex-info "Connection refused" {})))]
      (is (nil? (dictionary/ensure-word-lookup-index!))))))


(deftest lookup-word-meta-carries-part-of-speech-and-cefr-level
  (testing "a word the dictionary knows contributes both prompt fields"
    (with-redefs [dictionary/lookup-dictionary-entries
                  (constantly [{:_id         "lemma:das haus:noun"
                                :meta        {:cefr_level "a1"}
                                :pos         "noun"
                                :translation [{:lang "ru" :value "дом"}]}])]
      (is (= {:partOfSpeech "noun" :cefrLevel "a1"}
             (dictionary/lookup-word-meta "das Haus" ["дом"])))))

  (testing "the gloss decides between entries that share a normalized form"
    (with-redefs [dictionary/lookup-dictionary-entries
                  (constantly [{:_id         "lemma:der leiter:noun"
                                :meta        {:cefr_level "c1"}
                                :pos         "noun"
                                :translation [{:lang "ru" :value "руководитель"}]}
                               {:_id         "lemma:die leiter:noun"
                                :meta        {:cefr_level "b1"}
                                :pos         "noun"
                                :translation [{:lang "ru" :value "лестница"}]}])]
      (is (= {:partOfSpeech "noun" :cefrLevel "b1"}
             (dictionary/lookup-word-meta "Leiter" ["лестница"])))))

  (testing "a word the dictionary does not know leaves both fields unset"
    (with-redefs [dictionary/lookup-dictionary-entries (constantly [])]
      (is (nil? (dictionary/lookup-word-meta "Quasselstrippe" ["болтун"]))))))


(deftest generation-request-carries-the-word-meta-the-dictionary-returned
  (testing "part of speech and CEFR level reach the generation attempt"
    (let [captured (atom nil)]
      (with-redefs [dictionary/lookup-dictionary-entries
                    (constantly [{:_id         "lemma:das haus:noun"
                                  :meta        {:cefr_level "a1"}
                                  :pos         "noun"
                                  :translation [{:lang "ru" :value "дом"}]}])
                    sut/example-api-request
                    (fn [_word _translation _context word-meta _retry-context]
                      (reset! captured word-meta)
                      (support.generation/answered {:status 500 :body "{}"}))]
        (let [asked (sut/subject {:word "das Haus" :translation "дом"})]
          (sut/generate-one! asked (sut/word-meta asked) 1))
        (is (= {:partOfSpeech "noun" :cefrLevel "a1"} @captured))))))


(defn- generated-for
  "What generation answers for Hund/собака when the provider answers its
   attempts with `responses`, and how many attempts it made."
  [responses]
  (let [calls (atom 0)]
    (with-redefs [sut/example-api-request (support.generation/provider-answering-in-turn calls responses)]
      (let [result (sut/generate-one! (sut/subject {:word "Hund" :translation "собака"}) nil)]
        {:attempts @calls
         :result   result}))))


(deftest the-provider-retry-after-reaches-the-failure
  (testing "http-kit names headers with keywords, and the delay is read from there"
    (let [{:keys [attempts result]} (generated-for [{:status 429 :headers {:retry-after "7"}}])]
      (is (sut/generation-failure? result))
      (is (= 429 (:status result)))
      (is (= 7000 (:retry-after-ms result)))
      (is (= 1 attempts)))))


(deftest a-4xx-a-retry-cannot-fix-is-not-asked-again
  (doseq [status [400 401 402 403 404 413 422]]
    (testing status
      (let [{:keys [attempts result]} (generated-for [{:status status :body "{}"}])]
        (is (sut/generation-failure? result))
        (is (= status (:status result)))
        (is (= 1 attempts)))))
  (testing "a request timeout is asked again"
    (is (= 3 (:attempts (generated-for [{:status 408 :body "{}"}]))))))


(deftest no-completion-is-the-provider-s-failure
  (doseq [[response reason] [[{:status 200 :body "{\"error\":{\"code\":502,\"message\":\"upstream\"}}"}
                              "an error object with a 200"]
                             [{:status 200 :body "{\"choices\":[]}"} "no choices"]
                             [(support.generation/completion {:content nil}) "null content"]
                             [(support.generation/completion {:content "   "}) "blank content"]
                             [{:status 200 :body "not json"} "a body that is not JSON"]
                             [(support.generation/completion {:content nil} "error")
                              "a choice that ended in an error"]]]
    (testing reason
      (let [{:keys [attempts result]} (generated-for [response])]
        (is (sut/generation-failure? result))
        (is (= 3 attempts) "asked again: the next answer may carry a completion"))))
  (testing "an empty answer cut off at the token limit is not asked again"
    (let [{:keys [attempts result]} (generated-for [(support.generation/completion {:content ""} "length")])]
      (is (sut/generation-failure? result))
      (is (= 1 attempts)))))


(deftest content-that-is-no-example-is-a-rejected-candidate
  (doseq [[content reason] [["{\"value\":\"Der Hund" "JSON cut off at the token limit"]
                            ["Here is your example: Der Hund bellt." "prose"]
                            ["[\"Der Hund bellt.\"]" "JSON that is not an object"]
                            ["\"Der Hund bellt.\"" "a JSON string"]
                            [(cheshire/generate-string {:value       "Der Hund bellt."
                                                        :translation "Собака лает."
                                                        :structure   ["Hund" "bellt"]})
                             "structure items that are not objects"]
                            [(cheshire/generate-string {:value       "Der Hund bellt."
                                                        :translation "Собака лает."
                                                        :structure   "Hund"})
                             "a structure that is not a list"]]]
    (testing reason
      (let [{:keys [attempts result]} (generated-for [(support.generation/completion {:content content} "length")])]
        (is (nil? result) "nil: the pair's failure, not the provider's")
        (is (= 3 attempts))))))


(deftest text-parts-of-a-content-list-are-the-candidate
  (let [example {:value       "Die Leiter steht neben der Wand."
                 :translation "Лестница стоит у стены."
                 :structure   [{:usedForm "Leiter" :dictionaryForm "die Leiter" :translation "лестница"}
                               {:usedForm "steht" :dictionaryForm "stehen" :translation "стоять"}
                               {:usedForm "Wand" :dictionaryForm "die Wand" :translation "стена"}]}
        text    (cheshire/generate-string example)
        calls   (atom 0)]
    (with-redefs [sut/example-api-request (support.generation/provider-answering-in-turn
                                           calls
                                           [(support.generation/completion {:content
                                                                            [{:type "text" :text (subs text 0 10)}
                                                                             {:type "text" :text (subs text 10)}]})])]
      (is (= "Die Leiter steht neben der Wand."
             (:value (sut/generate-one! (sut/subject {:word "Leiter" :translation "лестница"}) nil)))))))


(deftest a-refused-input-is-the-pair-s-failure
  (doseq [[response reason]
          [[{:status 403
             :body   (cheshire/generate-string
                      {:error {:code     403
                               :message  "Input flagged"
                               :metadata {:reasons       ["violence"]
                                          :flagged_input "Hund"
                                          :provider_name "x"
                                          :model_slug    "y"}}})}
            "the provider's moderation flags the input"]
           [(support.generation/completion {:content nil} "content_filter") "the model's output is filtered"]
           [(support.generation/completion {:content nil :refusal "I can't help with that."}) "the model refuses"]]]
    (testing reason
      (let [{:keys [attempts result]} (generated-for [response])]
        (is (nil? result))
        (is (= 1 attempts) "the same input is refused again, so it is not asked again")))))


(deftest a-403-without-moderation-reasons-is-the-provider-s-failure
  (let [{:keys [result]} (generated-for [{:status 403
                                          :body   "{\"error\":{\"code\":403,\"message\":\"Key disabled\"}}"}])]
    (is (sut/generation-failure? result))))


(deftest the-last-attempt-decides-whose-failure-it-is
  (testing "candidates rejected, then the provider fails: the provider's failure"
    (let [{:keys [attempts result]} (generated-for [support.generation/rejected-candidate
                                                    support.generation/rejected-candidate
                                                    {:status 500 :body "{}"}])]
      (is (sut/generation-failure? result))
      (is (= 500 (:status result)))
      (is (= 3 attempts))))
  (testing "the provider fails, then candidates are rejected: the pair's failure"
    (let [{:keys [attempts result]} (generated-for [{:status 500 :body "{}"}
                                                    {:status 500 :body "{}"}
                                                    support.generation/rejected-candidate])]
      (is (nil? result))
      (is (= 3 attempts)))))


(deftest an-attempt-waits-for-its-timeout-and-no-longer
  (let [calls (atom 0)]
    (with-redefs-fn {#'sut/generation-timeout-ms (delay 100)
                     #'sut/example-api-request   (fn [& _] (swap! calls inc) (promise))}
      (fn []
        (let [started (System/nanoTime)
              result  (sut/generate-one! (sut/subject {:word "Hund" :translation "собака"}) nil)
              took-ms (quot (- (System/nanoTime) started) 1000000)]
          (is (sut/generation-failure? result))
          (is (= 3 @calls) "every attempt is made")
          (is (<= 300 took-ms 2000) (str "took " took-ms " ms")))))))


(deftest an-interrupted-wait-starts-no-new-attempt
  (let [calls   (atom 0)
        asked   (promise)
        outcome (promise)
        worker  (Thread. (fn []
                           (let [result (sut/generate-one! (sut/subject {:word "Hund" :translation "собака"}) nil)]
                             (deliver outcome
                                      {:interrupted? (Thread/interrupted)
                                       :result       result}))))]
    (with-redefs [sut/example-api-request (fn [& _]
                                            (swap! calls inc)
                                            (deliver asked true)
                                            (promise))]
      (.start worker)
      (is (true? (deref asked 2000 false)))
      (.interrupt worker)
      (let [{:keys [interrupted? result]} (deref outcome 2000 nil)]
        (is (sut/generation-failure? result))
        (is (true? interrupted?) "the interrupt flag is set again")
        (is (= 1 @calls) "no paid attempt follows")))))


(deftest an-unreadable-attempt-timeout-is-the-default
  (testing "unset is the default, quietly"
    (is (= 30000 (#'sut/generation-timeout-ms-from nil))))
  (testing "a positive whole number is taken"
    (is (= 12000 (#'sut/generation-timeout-ms-from " 12000 "))))
  (testing "anything else is the default, never an exception"
    (doseq [value ["" "  " "thirty" "0" "-5" "1.5" "99999999999999999999"]]
      (is (= 30000 (#'sut/generation-timeout-ms-from value)) (pr-str value)))))


(deftest a-timeout-the-proxy-could-not-cover-is-clamped
  (let [bound @#'sut/longest-attempt-timeout-ms]
    (testing "three attempts at the clamped timeout fit under the proxy's wait"
      (is (<= (+ (* @#'sut/max-generation-attempts bound) @#'sut/attempt-margin-ms)
              @#'sut/proxy-wait-ms)))
    (testing "a configured 60 s attempt is clamped to the bound, and the clamp is logged once"
      (let [{:keys [value signals]} (t/with-signals true (#'sut/generation-timeout-ms-from "60000"))]
        (is (= bound value))
        (is (= 1 (count signals)))
        (let [{:keys [level id data]} (first signals)]
          (is (= [:warn ::sut/generation-timeout-clamped] [level id]))
          (is (= {:value 60000 :clamped-to bound} data)))))
    (testing "the default is inside the bound and is kept, quietly"
      (let [{:keys [value signals]} (t/with-signals true (#'sut/generation-timeout-ms-from nil))]
        (is (= 30000 value))
        (is (empty? signals))))))


(deftest retry-after-reads-seconds-and-dates
  (let [now (java.time.Instant/parse "1994-11-06T08:49:00Z")
        retry-after (fn [value] (provider/retry-after-ms {:headers {:retry-after value}} now))]
    (testing "delta-seconds"
      (is (= 7000 (retry-after "7")))
      (is (= 1500 (retry-after "1.5")) "a fraction is rounded up to the millisecond")
      (is (= 0 (retry-after "0"))))
    (testing "the three HTTP-date forms of RFC 9110"
      (is (= 37000 (retry-after "Sun, 06 Nov 1994 08:49:37 GMT")))
      (is (= 37000 (retry-after "Sunday, 06-Nov-94 08:49:37 GMT")))
      (is (= 37000 (retry-after "Sun Nov  6 08:49:37 1994"))))
    (testing "a date in the past is no delay"
      (is (= 0 (retry-after "Sun, 06 Nov 1994 08:00:00 GMT"))))
    (testing "a delay is held to an hour"
      (is (= 3600000 (retry-after "86400")))
      (is (= 3600000 (retry-after "99999999999999999999")))
      (is (= 3600000 (retry-after "Fri, 01 Jan 2100 00:00:00 GMT"))))
    (testing "anything else is no header at all, never an exception"
      (doseq [value ["" "soon" "-5" "NaN" "Infinity" "1e400" "Sun, 32 Nov 1994 08:49:37 GMT"]]
        (is (nil? (retry-after value)) (pr-str value)))
      (is (nil? (provider/retry-after-ms {:headers {}} now))))))


(deftest a-dictionary-that-cannot-be-read-is-a-failure-not-an-unknown-word
  (testing "a read that timed out is a generation failure no retry follows"
    (with-redefs [db/request-sync (fn [_request]
                                    {:error (org.httpkit.client.TimeoutException. "read timeout")})]
      (let [result (sut/word-meta (sut/subject {:word "Fenster" :translation "окно"}))]
        (is (sut/generation-failure? result))
        (is (false? (:retryable? result)))
        (is (= :failure/dictionary (:context result))))))
  (testing "a word the dictionary has no entry for is nil, and generation goes on"
    (with-redefs [db/request-sync (fn [request]
                                    (if (= "dictionary-db/_find" (:url request))
                                      {:status 200 :body {:docs []}}
                                      {:status 404 :body {}}))]
      (is (nil? (sut/word-meta (sut/subject {:word "Fenster" :translation "окно"})))))))

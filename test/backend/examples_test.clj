(ns backend.examples-test
  (:require
   [cheshire.core :as cheshire]
   [clojure.test :refer [deftest is testing]]
   [examples :as sut]
   [org.httpkit.client :as client]))


(deftest gen-words-api-request-uses-openrouter-defaults
  (testing "OpenRouter is the default transport and model path"
    (let [captured (atom nil)]
      (with-redefs [client/request (fn [request]
                                     (reset! captured request)
                                     ::request)
                    sut/env        (fn [name]
                                     (case name
                                       "OPENROUTER_API_KEY" "or-test-key"
                                       nil))]
        (is (= ::request (sut/gen-words-api-request "Hund" nil nil)))
        (let [{:keys [url method headers body]} @captured
              payload (cheshire/parse-string body true)]
          (is (= "https://openrouter.ai/api/v1/chat/completions" url))
          (is (= :post method))
          (is (= "Bearer or-test-key" (get headers "Authorization")))
          (is (= "application/json" (get headers "Content-Type")))
          (is (= "nvidia/nemotron-3-super-120b-a12b:free" (:model payload)))
          (is (= "json_schema" (get-in payload [:response_format :type])))
          (is (= ["value" "translation" "glossMismatch" "structure"]
                 (get-in payload [:response_format :json_schema :schema :required])))
          (is (= "sentence_example" (get-in payload [:response_format :json_schema :name])))
          (is (string? (get-in payload [:messages 0 :content])))
          (is (re-find #"learner-facing German example sentences"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"part of speech"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"Ich stehe jeden Morgen um sieben Uhr auf"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"sich vorstellen"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"das Verstehen"
                       (get-in payload [:messages 0 :content])))
          (is (re-find #"Hund" (get-in payload [:messages 1 :content]))))))))


(deftest gen-words-api-request-allows-env-overrides
  (testing "model, url and legacy api key can be overridden from env"
    (let [captured (atom nil)]
      (with-redefs [client/request (fn [request]
                                     (reset! captured request)
                                     ::request)
                    sut/env        (fn [name]
                                     (case name
                                       "OPENROUTER_API_KEY" nil
                                       "OPENAI_API_KEY" "legacy-test-key"
                                       "EXAMPLE_GENERATION_API_URL" "https://example.test/v1/chat/completions"
                                       "EXAMPLE_GENERATION_MODEL" "google/gemma-3-27b-it:free"
                                       nil))]
        (is (= ::request (sut/gen-words-api-request "Hund" "собака" nil)))
        (let [{:keys [url headers body]} @captured
              payload (cheshire/parse-string body true)]
          (is (= "https://example.test/v1/chat/completions" url))
          (is (= "Bearer legacy-test-key" (get headers "Authorization")))
          (is (= "google/gemma-3-27b-it:free" (:model payload)))
          (is (= ["value" "translation" "glossMismatch" "structure"]
                 (get-in payload [:response_format :json_schema :schema :required])))
          (is (re-find #"собака"
                       (get-in payload [:messages 1 :content]))))))))


(deftest gen-words-api-request-includes-retry-feedback
  (testing "retry attempts include the rejected example and issue list in the user prompt"
    (let [captured (atom nil)
          rejected-example {"value" "Der Leiter steht neben der Wand."
                            "translation" "Лестница стоит рядом со стеной."
                            "glossMismatch" false
                            "structure"
                            [{"usedForm" "Leiter"
                              "dictionaryForm" "der Leiter"
                              "translation" "лестница"}]}]
      (with-redefs [client/request (fn [request]
                                     (reset! captured request)
                                     ::request)
                    sut/env        (fn [name]
                                     (case name
                                       "OPENROUTER_API_KEY" "or-test-key"
                                       nil))]
        (is (= ::request
               (sut/gen-words-api-request
                "Leiter"
                "лестница"
                {:example rejected-example
                 :issues  [:target-dictionary-form-gloss-mismatch]})))
        (let [payload      (cheshire/parse-string (:body @captured) true)
              user-message (get-in payload [:messages 1 :content])]
          (is (re-find #"previousAttempt" user-message))
          (is (re-find #"previousIssues" user-message))
          (is (re-find #"target-dictionary-form-gloss-mismatch" user-message))
          (is (re-find #"Der Leiter steht neben der Wand" user-message)))))))


(deftest generate-one-rejects-meta-and-non-russian-garbage
  (testing "obvious meta responses or non-russian structure translations are rejected"
    (let [body (cheshire/generate-string
                {:choices
                 [{:message
                   {:content
                    (cheshire/generate-string
                     {"value" "The example for 'aufstehen' is ..."
                      "translation" "to stand up"
                      "glossMismatch" true
                      "structure"
                      [{"usedForm" "aufstehen"
                        "dictionaryForm" "aufstehen"
                        "translation" "to stand up"}]})}}]})]
      (with-redefs [sut/gen-words-api-request (fn [_word _translation _retry-context]
                                                (delay {:status 200 :body body}))
                    sut/lookup-dictionary-entries (constantly nil)]
        (is (nil? (sut/generate-one! {:word "aufstehen" :translation "вставать"})))))))


(deftest generate-one-retries-until-deterministic-checks-pass
  (testing "best-of-N style retries can skip a bad candidate and keep a later valid one"
    (let [bad-example {"value" "Der Leiter steht neben der Wand."
                       "translation" "Лестница стоит рядом со стеной."
                       "glossMismatch" true
                       "structure"
                       [{"usedForm" "Leiter"
                         "dictionaryForm" "die Leiter"
                         "translation" "лестница"}
                        {"usedForm" "steht"
                         "dictionaryForm" "stehen"
                         "translation" "стоять"}
                        {"usedForm" "Wand"
                         "dictionaryForm" "die Wand"
                         "translation" "стена"}]}
          good-example {"value" "Die Leiter steht neben der Wand."
                        "translation" "Лестница стоит рядом со стеной."
                        "glossMismatch" false
                        "structure"
                        [{"usedForm" "Leiter"
                          "dictionaryForm" "die Leiter"
                          "translation" "лестница"}
                         {"usedForm" "steht"
                          "dictionaryForm" "stehen"
                          "translation" "стоять"}
                         {"usedForm" "Wand"
                          "dictionaryForm" "die Wand"
                          "translation" "стена"}]}
          responses (atom [bad-example good-example])
          retry-contexts (atom [])]
      (with-redefs [sut/raw-generate-one!      (fn
                                                 ([_word _translation]
                                                  (let [next-example (first @responses)]
                                                    (swap! responses subvec 1)
                                                    next-example))
                                                 ([_word _translation retry-context]
                                                  (swap! retry-contexts conj retry-context)
                                                  (let [next-example (first @responses)]
                                                    (swap! responses subvec 1)
                                                    next-example)))
                    sut/lookup-dictionary-entries (constantly nil)]
        (is (= good-example
               (sut/generate-one! {:word "Leiter" :translation "лестница"} 2)))
        (is (= [nil
                {:example bad-example
                 :issues  [:gloss-mismatch]}]
               @retry-contexts))))))


(deftest generate-one-retries-when-dictionary-gloss-disagrees
  (testing "backend dictionary validation rejects a wrong noun article for the intended gloss"
    (let [bad-example {"value" "Der Leiter steht neben der Wand."
                       "translation" "Лестница стоит рядом со стеной."
                       "glossMismatch" false
                       "structure"
                       [{"usedForm" "Leiter"
                         "dictionaryForm" "der Leiter"
                         "translation" "лестница"}
                        {"usedForm" "steht"
                         "dictionaryForm" "stehen"
                         "translation" "стоять"}
                        {"usedForm" "Wand"
                          "dictionaryForm" "die Wand"
                          "translation" "стена"}]}
          good-example {"value" "Die Leiter steht neben der Wand."
                        "translation" "Лестница стоит рядом со стеной."
                        "glossMismatch" false
                        "structure"
                        [{"usedForm" "Leiter"
                          "dictionaryForm" "die Leiter"
                          "translation" "лестница"}
                         {"usedForm" "steht"
                          "dictionaryForm" "stehen"
                          "translation" "стоять"}
                         {"usedForm" "Wand"
                          "dictionaryForm" "die Wand"
                          "translation" "стена"}]}
          responses (atom [bad-example good-example])]
      (with-redefs [sut/raw-generate-one!      (fn
                                                 ([_word _translation]
                                                  (let [next-example (first @responses)]
                                                    (swap! responses subvec 1)
                                                    next-example))
                                                 ([_word _translation _retry-context]
                                                  (let [next-example (first @responses)]
                                                    (swap! responses subvec 1)
                                                    next-example)))
                    sut/lookup-dictionary-entries
                    (fn [dictionary-form]
                      (case dictionary-form
                        "der Leiter" [{:_id "lemma:der leiter:noun"
                                       :value "der Leiter"
                                       :translation [{:lang "ru" :value "руководитель"}]}]
                        "die Leiter" [{:_id "lemma:die leiter:noun"
                                       :value "die Leiter"
                                       :translation [{:lang "ru" :value "лестница"}]}]
                        []))]
        (is (= good-example
               (sut/generate-one! {:word "Leiter" :translation "лестница"} 2)))))))


(deftest generate-one-accepts-map-input
  (testing "single-item API accepts {:word :translation} input"
    (let [example {"value" "Die Leiter steht neben der Wand."
                   "translation" "Лестница стоит рядом со стеной."
                   "glossMismatch" false
                   "structure"
                   [{"usedForm" "Leiter"
                     "dictionaryForm" "die Leiter"
                     "translation" "лестница"}]}]
      (with-redefs [sut/raw-generate-one! (fn
                                            ([_word _translation]
                                             example)
                                            ([_word _translation _retry-context]
                                             example))
                    sut/lookup-dictionary-entries (constantly nil)]
        (is (= example
               (sut/generate-one! {:word "Leiter" :translation "лестница"} 1)))))))


(deftest generate-one-logs-transport-errors
  (testing "transport exceptions are logged and returned as nil"
    (let [logged (atom nil)]
      (with-redefs [sut/gen-words-api-request (fn [_word _translation _retry-context]
                                                (delay (throw (ex-info "network down" {:status 0}))))
                    sut/log-generation-failure! (fn [data]
                                                  (swap! logged conj data))]
        (is (nil? (sut/generate-one! {:word "Hund" :translation "собака"} 1)))
        (is (some #(= "Hund" (:word %)) @logged))
        (is (some #(= :transport (:context %)) @logged))
        (is (some #(= "network down" (:error %)) @logged))))))

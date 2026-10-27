(ns backend.core-test
  (:require
   [backend.support.db :as support.db]
   [backend.support.generation :as support.generation]
   [cheshire.core :as cheshire]
   [clojure.string :as str]
   [clojure.test :refer [are deftest is testing]]
   [core :as sut]
   [db :as db]
   [examples :as examples]
   [examples.dictionary :as dictionary]
   [examples.provider :as provider]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as result-set]
   [org.httpkit.client :as client]
   [org.httpkit.server :as server]
   [utils :as utils])
  (:import
   [java.io File]
   [java.net URLEncoder]))


(set! *warn-on-reflection* true)


(defn- migrated-db
  []
  (support.db/migrated-db "core-test"))


(defn- add-account!
  [db id token]
  (jdbc/execute! db
    ["INSERT INTO users (id, token_sha256) VALUES (?, ?)" id (utils/sha256-hex token)]))


(deftest a-token-authenticates-the-account-it-was-minted-for
  (let [db (migrated-db)]
    (add-account! db 1 "token-of-one")
    (add-account! db 2 "token-of-two")
    (testing "each token resolves to its own account"
      (is (= 1 (#'sut/authenticated-user-id db "token-of-one")))
      (is (= 2 (#'sut/authenticated-user-id db "token-of-two"))))
    (testing "anything else authenticates nobody"
      (is (nil? (#'sut/authenticated-user-id db "not-a-token")))
      (is (nil? (#'sut/authenticated-user-id db "")))
      (is (nil? (#'sut/authenticated-user-id db nil))))))


(deftest the-server-keeps-no-token-it-could-leak
  (let [db    (migrated-db)
        token "token-of-one"]
    (add-account! db 1 token)
    (testing "only the hash is written"
      (is (= [(utils/sha256-hex token)]
             (map :token_sha256
                  (jdbc/execute! db
                    ["SELECT token_sha256 FROM users"]
                    {:builder-fn result-set/as-unqualified-maps})))))))


(deftest a-grant-opens-exactly-one-account
  (let [db    (migrated-db)
        token (sut/mint-grant! db)]
    (testing "an unused grant burns"
      (is (true? (#'sut/burn-grant! db token))))
    (testing "and never burns twice"
      (is (false? (#'sut/burn-grant! db token))))
    (testing "a grant nobody minted does not burn"
      (is (false? (#'sut/burn-grant! db "invented"))))))


(deftest an-expired-grant-is-worthless
  (let [db    (migrated-db)
        token (sut/mint-grant! db -1)]
    (is (false? (#'sut/burn-grant! db token)))))


(deftest the-invite-url-never-doubles-a-trailing-slash
  (is (= "https://example.test/#invite=abc"
         (#'sut/invite-url
          (#'sut/configured-public-url {"LEARNING_APP__PUBLIC_URL" "https://example.test/"})
          "abc"))))


(deftest stopping-the-server-drains-in-flight-requests
  (let [in-flight (promise)
        handler   (fn [_]
                    (deliver in-flight true)
                    (Thread/sleep 300)
                    {:body "drained" :status 200})]
    (sut/start-server! handler 0)
    (try
      (let [port     (server/server-port @sut/server)
            response (future @(client/request {:method :get
                                               :url    (str "http://localhost:" port "/")}))]
        (is (true? (deref in-flight 2000 false)) "the request never reached the handler")
        (sut/stop-server!)
        (testing "the in-flight request finished with a response"
          (is (= 200 (:status @response))))
        (testing "the listening socket is closed"
          (is (thrown? java.net.ConnectException
                       (.close (java.net.Socket. "localhost" (int port))))))
        (testing "the server handle is released"
          (is (nil? @sut/server))))
      (finally
       (sut/stop-server!)))))


(deftest example-generation-answers-only-an-authenticated-session
  (let [db (migrated-db)
        generations (atom 0)]
    (add-account! db 1 "token-of-one")
    (with-redefs [sut/db-spec db
                  dictionary/lookup-dictionary-entries (constantly nil)
                  dictionary/lemma-ids (constantly #{})
                  examples/generate-one! (fn [_subject _word-meta _max-attempts _avoid]
                                           (swap! generations inc)
                                           {:translation "У дома есть сад."
                                            :value       "Das Haus hat einen Garten."})]
      (sut/start-server! sut/app-handler 0)
      (try
        (let [port (server/server-port @sut/server)
              ask  (fn [cookie query]
                     @(client/request
                       (cond-> {:method :get
                                :url    (str "http://localhost:" port "/api/examples" query)}
                         cookie (assoc :headers {"Cookie" (str "auth-token=" cookie)}))))]
          (testing "a request with no session is refused"
            (is (= 401 (:status (ask nil "?word=Haus")))))
          (testing "a token nobody minted is refused"
            (is (= 401 (:status (ask "invented" "?word=Haus")))))
          (testing "an anonymous caller is not even told which parameter is missing"
            (is (= 401 (:status (ask nil "")))))
          (testing "nothing was generated for any of them"
            (is (zero? @generations)))
          (testing "the session that owns an account is served"
            (let [response (ask "token-of-one" "?word=Haus")]
              (is (= 200 (:status response)))
              (is (str/includes? (:body response) "Das Haus hat einen Garten."))
              (is (= 1 @generations))))
          (testing "and an authenticated caller still gets its validation error"
            (is (= 400 (:status (ask "token-of-one" ""))))))
        (finally
         (sut/stop-server!))))))


(deftest a-repeated-parameter-never-answers-with-an-exception-message
  (let [db (migrated-db)
        generations (atom 0)]
    (add-account! db 1 "token-of-one")
    (with-redefs [sut/db-spec db
                  examples/generate-one! (fn [_subject _word-meta _max-attempts _avoid]
                                           (swap! generations inc)
                                           nil)]
      (sut/start-server! sut/app-handler 0)
      (try
        (let [port (server/server-port @sut/server)
              ask  (fn [cookie query]
                     @(client/request
                       (cond-> {:method :get
                                :url    (str "http://localhost:" port "/api/examples" query)}
                         cookie (assoc :headers {"Cookie" (str "auth-token=" cookie)}))))]
          (testing "an anonymous caller is refused before the parameters are read"
            (is (= 401 (:status (ask nil "?word=Haus&context=A&context=B")))))
          (testing "a repeated context is a 400 with no exception text"
            (let [response (ask "token-of-one" "?word=Haus&context=A&context=B")]
              (is (= 400 (:status response)))
              (is (not (str/includes? (:body response) "Exception")))))
          (testing "a repeated word is the same"
            (is (= 400 (:status (ask "token-of-one" "?word=Haus&word=Hund")))))
          (testing "nothing was generated"
            (is (zero? @generations))))
        (finally
         (sut/stop-server!))))))


(defn- with-example-server
  "Serves `/api/examples` against a fresh database holding one account, with
   `generate!`, a function of the subject alone, in the provider's place. The
   dictionary answers, and knows no word; a test that wants it otherwise
   redefines the lookup inside `body`. Calls (body ask), where ask takes a query
   string and returns the response of an authenticated request. `prepare!` runs
   against that database before the server starts, for a test that wants it in
   some other state."
  ([generate! body]
   (with-example-server generate! body identity))
  ([generate! body prepare!]
   (let [db (migrated-db)]
     (add-account! db 1 "token-of-one")
     (prepare! db)
     (with-redefs [sut/db-spec db
                   dictionary/lookup-dictionary-entries (constantly nil)
                   examples/generate-one! (fn [subject _word-meta _max-attempts _avoid] (generate! subject))]
       (sut/start-server! sut/app-handler 0)
       (try
         (let [port (server/server-port @sut/server)]
           (body (fn ask
                   ([query] (ask query "token-of-one"))
                   ([query cookie]
                    @(client/request
                      (cond-> {:method :get
                               :url    (str "http://localhost:" port "/api/examples" query)}
                        cookie (assoc :headers {"Cookie" (str "auth-token=" cookie)})))))))
         (finally
          (sut/stop-server!)))))))


(def ^:private generated
  "What `generate-one!` answers with: an example whose `structure` items carry
   the `wordIndex` the backend assigned. The cache keeps this shape and no
   other, so a fixture without it would be dropped and every count of
   generations here would read as a cache that never hits."
  {:structure   [{:dictionaryForm "der Hund"
                  :translation    "собака"
                  :usedForm       "Hund"
                  :wordIndex      1}
                 {:dictionaryForm "bellen"
                  :translation    "лает"
                  :usedForm       "bellt"
                  :wordIndex      2}]
   :translation "Собака лает."
   :value       "Der Hund bellt."})


(deftest a-subject-already-generated-never-reaches-the-provider
  (let [generations (atom 0)]
    (with-example-server
     (fn [_] (swap! generations inc) generated)
     (fn [ask]
       (let [first-response (ask "?word=Hund&translation=собака")]
         (testing "the first request generates"
           (is (= 200 (:status first-response)))
           (is (= 1 @generations))))
       (testing "the second is served from the cache, and the provider is not called"
         (let [again (ask "?word=Hund&translation=собака")]
           (is (= 200 (:status again)))
           (is (str/includes? (:body again) "Der Hund bellt."))
           (is (= 1 @generations))))
       (testing "the same glosses in another order are the same subject"
         (ask "?word=Hund&translation=пёс&translation=собака")
         (is (= 2 @generations) "two glosses are a different subject than one")
         (ask "?word=Hund&translation=собака&translation=пёс")
         (is (= 2 @generations)))
       (testing "a different collection context is a different subject"
         (ask "?word=Hund&translation=собака&context=Tiere")
         (is (= 3 @generations)))))))


(defn- query
  "A query string with its values percent-encoded, the way a browser sends
   them — a Cyrillic gloss written raw into the URL never survives the trip."
  [& pairs]
  (str "?"
       (str/join "&"
                 (for [[k v] (partition 2 pairs)]
                   (str k "=" (URLEncoder/encode (str v) "UTF-8"))))))


(deftest a-hit-serves-what-a-generation-would
  (testing "the cache holds data, so the second caller is answered like the first"
    (let [generations (atom 0)]
      (with-example-server
       (fn [_] (swap! generations inc) generated)
       (fn [ask]
         (let [generated-body (:body (ask (query "word" "Hund" "translation" "собака")))
               cached-body    (:body (ask (query "word" "Hund" "translation" "собака")))]
           (is (= 1 @generations) "the second request never reached the provider")
           (is (= (cheshire/parse-string generated-body true)
                  (cheshire/parse-string cached-body true)))
           (is (= generated (cheshire/parse-string cached-body true))
               "including the structure items and their indexes")))))))


(deftest a-word-the-dictionary-knows-is-a-different-subject
  (testing "the metadata goes into the prompt, so it goes into the key"
    (let [generations (atom 0)
          entry       {:pos         "noun"
                       :meta        {:cefr_level "a1"}
                       :translation [{:lang "ru" :value "собака"}]}]
      (with-example-server
       (fn [_] (swap! generations inc) generated)
       (fn [ask]
         (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)
                       dictionary/lemma-ids (constantly #{})]
           (is (= 200 (:status (ask (query "word" "Hund" "translation" "собака")))))
           (testing "and the example of a word the dictionary does not know is kept"
             (is (= 200 (:status (ask (query "word" "Hund" "translation" "собака")))))
             (is (= 1 @generations))))
         (testing "the word arriving in the dictionary is a miss, like an edited prompt"
           (with-redefs [dictionary/lookup-dictionary-entries (constantly [entry])]
             (is (= 200 (:status (ask (query "word" "Hund" "translation" "собака")))))
             (is (= 2 @generations)))))))))


(deftest a-broken-cache-still-serves-the-subject
  (testing "the table is gone: the read misses, the write is dropped, the caller is served"
    (let [generations (atom 0)]
      (with-example-server
       (fn [_] (swap! generations inc) generated)
       (fn [ask]
         (let [response (ask "?word=Hund&translation=собака")]
           (is (= 200 (:status response)))
           (is (str/includes? (:body response) "Der Hund bellt.")))
         (testing "and the next caller is served too, by generating again"
           (is (= 200 (:status (ask "?word=Hund&translation=собака"))))
           (is (= 2 @generations))))
       (fn [db] (jdbc/execute! db ["DROP TABLE example_cache"]))))))


(deftest a-refused-generation-is-never-cached
  (let [generations (atom 0)]
    (with-example-server
     (fn [_]
       (swap! generations inc)
       ;; What the provider path returns when it gives up: not an example.
       {::examples/type ::examples/generation-failure :status 429 :retry-after-ms 2000})
     (fn [ask]
       (testing "the caller is told it failed, and how long to wait"
         (let [response (ask "?word=Hund&translation=собака")]
           (is (= 429 (:status response)))
           (is (= "2" (get-in response [:headers :retry-after])))))
       (testing "and the next caller generates again rather than being served the failure"
         (is (= 429 (:status (ask "?word=Hund&translation=собака"))))
         (is (= 2 @generations)))))))


(deftest a-malformed-example-is-never-cached
  (let [generations (atom 0)]
    (with-example-server
     (fn [_]
       (swap! generations inc)
       ;; Shape of an example, but with nothing to show: `valid-example?`
       ;; refuses it, and so must the cache.
       {:value "Der Hund bellt." :translation "   "})
     (fn [ask]
       (is (= 422 (:status (ask "?word=Hund&translation=собака"))))
       (is (= 422 (:status (ask "?word=Hund&translation=собака"))))
       (is (= 2 @generations))))))


(deftest a-cached-answer-still-needs-a-session
  (with-example-server
   (constantly generated)
   (fn [ask]
     (is (= 200 (:status (ask (query "word" "Hund")))))
     (testing "the word is in the cache, and an anonymous caller still gets nothing"
       (let [response (ask (query "word" "Hund") nil)]
         (is (= 401 (:status response)))
         (is (not (str/includes? (:body response) "Der Hund bellt."))))))))


(defn- with-regenerating-server
  "Serves `/api/examples` against a fresh database holding one account, with
   `generate!`, a function of the subject and the sentences to avoid, in the
   provider's place. Calls (body db get-example regenerate), where the last
   two send a request with the session cookie, or none when it is nil."
  [generate! body]
  (let [db (migrated-db)]
    (add-account! db 1 "token-of-one")
    (with-redefs [sut/db-spec db
                  dictionary/lookup-dictionary-entries (constantly nil)
                  examples/generate-one! (fn [subject _word-meta _max-attempts avoid]
                                           (generate! subject avoid))]
      (sut/start-server! sut/app-handler 0)
      (try
        (let [port    (server/server-port @sut/server)
              request (fn [method url payload cookie]
                        @(client/request
                          (cond-> {:method method
                                   :url    (str "http://localhost:" port url)}
                            payload (assoc :body (if (string? payload) payload (cheshire/generate-string payload)))
                            cookie  (assoc :headers {"Cookie" (str "auth-token=" cookie)}))))]
          (body db
                (fn get-example [query]
                  (request :get (str "/api/examples" query) nil "token-of-one"))
                (fn regenerate
                  ([payload] (regenerate payload "token-of-one"))
                  ([payload cookie]
                   (request :post "/api/examples" payload cookie)))))
        (finally
         (sut/stop-server!))))))


(def ^:private another-example
  (assoc generated :value "Der Hund schläft." :translation "Собака спит."))


(deftest a-regeneration-answers-another-example-and-leaves-the-cache-alone
  (let [asked (atom [])]
    (with-regenerating-server
     (fn [subject avoid]
       (swap! asked conj {:avoid avoid :word (:word subject)})
       (if (seq avoid) another-example generated))
     (fn [db get-example regenerate]
       (is (= generated (cheshire/parse-string (:body (get-example (query "word" "Hund"))) true))
           "the shared cache is filled by the ordinary request")
       (let [before   (jdbc/execute! db ["SELECT * FROM example_cache"])
             response (regenerate {:word         "Hund"
                                   :translations ["собака"]
                                   :context      nil
                                   :avoid        ["Der Hund bellt."]})]
         (testing "the answer is a different example, for the same word"
           (is (= 200 (:status response)))
           (is (= another-example (cheshire/parse-string (:body response) true)))
           (is (= {:avoid ["Der Hund bellt."] :word "Hund"} (last @asked))))
         (testing "the cache holds what it held, and still serves the first example"
           (is (= before (jdbc/execute! db ["SELECT * FROM example_cache"])))
           (is (= generated
                  (cheshire/parse-string (:body (get-example (query "word" "Hund"))) true)))))))))


(deftest a-regeneration-avoids-the-example-the-cache-holds
  (let [asked (atom nil)]
    (with-regenerating-server
     (fn [_subject avoid]
       (reset! asked avoid)
       (if (seq avoid) another-example generated))
     (fn [_db get-example regenerate]
       (get-example (query "word" "Hund" "translation" "собака"))
       (regenerate {:word "Hund" :translations ["собака"] :avoid []})
       (is (= ["Der Hund bellt."] @asked))))))


(deftest a-regeneration-needs-a-session-and-a-word
  (let [generations (atom 0)]
    (with-regenerating-server
     (fn [_subject _avoid] (swap! generations inc) generated)
     (fn [_db _get-example regenerate]
       (testing "no session: 401, the provider is not called"
         (is (= 401 (:status (regenerate {:word "Hund"} nil))))
         (is (= 401 (:status (regenerate {:word ""} nil)))
             "an anonymous caller learns nothing about the parameters"))
       (testing "a blank word: 400"
         (is (= 400 (:status (regenerate {:word "  " :avoid []}))))
         (is (= 400 (:status (regenerate {:translations ["собака"]})))))
       (is (zero? @generations))))))


(deftest a-regeneration-with-a-malformed-body-is-refused-after-the-session
  (with-regenerating-server
   (fn [_subject _avoid] another-example)
   (fn [_db _get-example regenerate]
     (testing "an anonymous caller is told nothing about the body"
       (is (= 401 (:status (regenerate "{not json" nil)))))
     (testing "an authenticated one is told the body is not JSON"
       (let [response (regenerate "{not json")]
         (is (= 400 (:status response)))
         (is (= "Invalid JSON body" (:error (cheshire/parse-string (:body response) true)))))
       (is (= 400 (:status (regenerate "[1,2]"))) "JSON that is not an object is refused too")))))


(deftest a-failed-regeneration-fails-with-the-status-a-get-fails-with
  (with-regenerating-server
   (fn [_subject _avoid] nil)
   (fn [_db get-example regenerate]
     (is (= (:status (get-example (query "word" "Hund")))
            (:status (regenerate {:word "Hund" :avoid []}))
            422)))))


(deftest an-example-is-asked-in-the-learners-language
  (let [asked (atom [])]
    (with-example-server
     (fn [subject]
       (swap! asked conj (:language subject))
       generated)
     (fn [ask]
       (testing "a language the interface speaks reaches the generation"
         (is (= 200 (:status (ask "?word=Hund&language=en")))))
       (testing "a request that names none is Russian"
         (is (= 200 (:status (ask "?word=Hund")))))
       (testing "a language the interface does not speak is refused"
         (is (= 400 (:status (ask "?word=Hund&language=ja")))))
       (is (= ["en" "ru"] @asked) "the refused one reached no generation"))))
  (with-regenerating-server
   (fn [_subject _avoid] another-example)
   (fn [_db _get-example regenerate]
     (is (= 400 (:status (regenerate {:word "Hund" :language "ja" :avoid []})))
         "a regeneration is refused the same"))))


(deftest a-regeneration-avoids-the-cached-example-of-its-part-of-speech
  (let [asked (atom nil)]
    (with-regenerating-server
     (fn [subject avoid]
       (reset! asked avoid)
       (if (= "adj" (:pos subject)) generated another-example))
     (fn [_db get-example regenerate]
       ;; The dictionary honours the pick, and the part of speech is in the key.
       (with-redefs [dictionary/lookup-word-meta (fn [_word _language _translations pos]
                                                   {:cefrLevel "A1" :lemma "Hund" :partOfSpeech pos})]
         (get-example (query "word" "Hund" "pos" "adj"))
         (get-example (query "word" "Hund" "pos" "noun"))
         (regenerate {:word "Hund" :pos "adj" :avoid []})
         (is (= ["Der Hund bellt."] @asked)
             "the adjective's example, not the noun's")
         (regenerate {:word "Hund" :pos "noun" :avoid []})
         (is (= ["Der Hund schläft."] @asked)))))))


(deftest a-regeneration-request-is-read-from-its-body
  (testing "a good body gives the subject's fields and the sentences to avoid"
    (is (= {:params {:context     "Tiere"
                     :language    "en"
                     :pos         "adj"
                     :translation ["dog"]
                     :word        "Hund"}
            :avoid  ["Der Hund bellt."]}
           (sut/regeneration-request {:word         "Hund"
                                      :translations ["dog"]
                                      :context      "Tiere"
                                      :language     "en"
                                      :pos          "adj"
                                      :avoid        ["Der Hund bellt."]}))))
  (testing "what is not a string is dropped, and a missing field is nil or empty"
    (is (= {:params {:context nil :language nil :pos nil :translation ["собака"] :word "Hund"}
            :avoid  ["Der Hund bellt."]}
           (sut/regeneration-request {:word         "Hund"
                                      :translations [1 {} "собака"]
                                      :context      3
                                      :language     7
                                      :pos          [:adj]
                                      :avoid        [7 nil "Der Hund bellt."]})))
    (is (= {:params {:context nil :language nil :pos nil :translation [] :word "Hund"}
            :avoid  []}
           (sut/regeneration-request {:word "Hund" :translations 5 :avoid "x"}))))
  (testing "a body that is not an object, or has no word, is refused"
    (are [body error] (= {:error error} (sut/regeneration-request body))
     nil          "Invalid JSON body"
     [1 2]        "Invalid JSON body"
     {}           "Missing 'word' parameter"
     {:word "  "} "Missing 'word' parameter"
     {:word 5}    "Missing 'word' parameter"))
  (testing "five sentences of 300 characters are the most"
    (is (nil? (:error (sut/regeneration-request
                       {:word "Hund" :avoid (repeat 5 (apply str (repeat 300 "a")))}))))
    (are [avoid] (= {:error "Too many or too long 'avoid' sentences"}
                    (sut/regeneration-request {:word "Hund" :avoid avoid}))
     (repeat 6 "Der Hund bellt.")
     [(apply str (repeat 301 "a"))])))


(def ^:private real-generation
  "The generation itself, kept before any test puts a stub in its place. The
   dictionary knows no word in these tests, so it is handed no reading. The attempts
   are named, since the shorter arity calls back through the stubbed var."
  (let [generate-one! examples/generate-one!]
    (fn [subject]
      (generate-one! subject nil 3 []))))


(deftest a-failed-generation-says-whose-problem-it-is
  (doseq [[provider-answer status retry-after attempts reason]
          [[{:status 401 :body "{}"} 503 "30" 1 "the provider refuses the service's key"]
           [{:status 403 :body "{\"error\":{\"code\":403,\"message\":\"Key disabled\"}}"} 503 "30" 1
            "the provider forbids the service"]
           [{:status 402 :body "{}"} 503 "30" 1 "the provider's credits are spent"]
           [{:status 500 :body "{}"} 503 "30" 3 "the provider fails on every attempt"]
           [{:error (java.net.ConnectException. "Connection refused")} 503 "30" 3 "the provider cannot be reached"]
           [(support.generation/completion {:content nil}) 503 "30" 3 "the provider answers with no completion"]
           [{:status 429 :headers {:retry-after "7"}} 429 "7" 1 "the provider throttles"]
           [{:status 429} 429 nil 1 "the provider throttles and names no delay"]
           ;; A date already past is no delay: the application sends no
           ;; Retry-After at all, and nginx adds its own 5 to a bare 429.
           [{:status 429 :headers {:retry-after "Sun, 06 Nov 1994 08:49:37 GMT"}} 429 nil 1
            "the provider throttles with a date already past"]
           [support.generation/rejected-candidate 422 nil 3 "no candidate passes the checks"]
           [(support.generation/completion {:content "{\"value\":\"Der Hund"} "length") 422 nil 3
            "every candidate is cut off"]
           [{:status 403
             :body   "{\"error\":{\"code\":403,\"message\":\"Flagged\",\"metadata\":{\"reasons\":[\"violence\"]}}}"}
            422 nil 1 "the provider's moderation refuses the input"]]]
    (testing reason
      (let [calls (atom 0)]
        (with-redefs [examples/example-api-request (support.generation/provider-answering-in-turn calls
                                                                                                  [provider-answer])
                      dictionary/lookup-dictionary-entries (constantly nil)
                      dictionary/lemma-ids (constantly #{})]
          (with-example-server
           real-generation
           (fn [ask]
             (let [response (ask (query "word" "Hund" "translation" "собака"))]
               (is (= status (:status response)))
               (is (= retry-after (get-in response [:headers :retry-after])))
               (is (= attempts @calls))))))))))


(deftest the-provider-retry-after-reaches-the-client
  (testing "through a real http-kit request, whose headers arrive as keywords"
    (let [provider-server (server/run-server
                           (fn [_]
                             {:status  429
                              :headers {"Retry-After" "7"}
                              :body    "{}"})
                           {:port 0 :legacy-return-value? false})
          config (provider/config)]
      (try
        (with-redefs [provider/config      (constantly
                                            (assoc config
                                                   :api-url
                                                   (str "http://localhost:"
                                                        (server/server-port provider-server)
                                                        "/")))
                      dictionary/lookup-dictionary-entries (constantly nil)
                      dictionary/lemma-ids (constantly #{})]
          (with-example-server
           real-generation
           (fn [ask]
             (let [response (ask (query "word" "Hund" "translation" "собака"))]
               (is (= 429 (:status response)))
               (is (= "7" (get-in response [:headers :retry-after])))))))
        (finally
         (server/server-stop! provider-server))))))


(deftest nothing-is-generated-without-the-dictionary
  (doseq [[failure reason] [[(ex-info "Dictionary DB error" {:status 500}) "the dictionary fails"]
                            [(ex-info "Dictionary DB error" {:status nil}) "the dictionary does not answer in time"]]]
    (testing reason
      (let [generations (atom 0)
            database    (atom nil)]
        (with-example-server
         (fn [_] (swap! generations inc) generated)
         (fn [ask]
           (with-redefs [dictionary/lookup-dictionary-entries (fn [_] (throw failure))]
             (let [response (ask (query "word" "Hund" "translation" "собака"))]
               (is (= 503 (:status response)))
               (is (= "30" (get-in response [:headers :retry-after])))))
           (is (zero? @generations) "the provider is not called")
           (is (empty? (jdbc/execute! @database ["SELECT 1 FROM example_cache"])) "nothing is stored"))
         (fn [db] (reset! database db)))))))


(deftest identical-subjects-in-flight-share-one-generation
  (testing "a failure is not cached, so a second generation could not hide behind the cache"
    (let [generations (atom 0)
          release     (promise)]
      (with-example-server
       (fn [_]
         (swap! generations inc)
         @release
         {::examples/type ::examples/generation-failure :status 500})
       (fn [ask]
         (support.generation/with-joins-counted
          1
          (fn [joined]
            (let [first-response  (future (ask (query "word" "Hund" "translation" "собака")))
                  second-response (future (ask (query "word" "Hund" "translation" "собака")))]
              (is (true? (joined)) "the second request joined the running generation")
              (deliver release true)
              (is (= 503 (:status @first-response)))
              (is (= 503 (:status @second-response)))
              (is (= 1 @generations)))))))))
  (testing "both callers get the same example"
    (let [generations (atom 0)
          release     (promise)]
      (with-example-server
       (fn [_]
         (let [n (swap! generations inc)]
           @release
           (assoc generated :value (str "Der Hund bellt " n "."))))
       (fn [ask]
         (support.generation/with-joins-counted
          1
          (fn [joined]
            (let [first-response  (future (ask (query "word" "Hund" "translation" "собака")))
                  second-response (future (ask (query "word" "Hund" "translation" "собака")))]
              (is (true? (joined)))
              (deliver release true)
              (is (= 200 (:status @first-response) (:status @second-response)))
              (is (= (:body @first-response) (:body @second-response)))
              (is (= 1 @generations)))))))))
  (testing "a different subject does not wait on a running one"
    (let [release (promise)
          started (promise)]
      (with-example-server
       (fn [subject]
         (when (= "Hund" (:word subject))
           (deliver started true)
           @release)
         generated)
       (fn [ask]
         (let [held (future (ask (query "word" "Hund" "translation" "собака")))]
           (try
             (is (true? (deref started 2000 false)))
             (is (= 200
                    (:status (deref (future (ask (query "word" "Katze" "translation" "кошка")))
                                    2000
                                    nil)))
                 "answered while the other subject is still generating")
             (finally
              (deliver release true)))
           (is (= 200 (:status @held)))))))))


(deftest every-precached-path-is-a-file-that-exists
  (testing "a named asset that was deleted would reject the atomic install"
    (doseq [path (remove #{"/" "/js/app/main.js"} (#'sut/precache-paths))]
      (is (.exists (File. (str "resources/public" path))) path))))


(deftest a-packaged-app-without-a-secret-refuses-to-start
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"LEARNING_APP__DB_AUTH_SECRET"
                        (#'sut/configured-db-auth-secret {} false))))


(deftest a-packaged-app-without-a-couchdb-password-refuses-to-start
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"LEARNING_APP__COUCHDB_PASSWORD"
                        (#'sut/require-couchdb-password! {} false))))


(deftest a-recycled-id-is-refused-not-inherited
  (testing "a userdb left by a previous owner of the id blocks provisioning"
    (let [db (migrated-db)]
      (with-redefs [db/exists? (constantly true)]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"userdb already exists"
                              (#'sut/create-account! db))))
      (is (empty? (jdbc/execute! db ["SELECT id FROM users"]))
          "the transaction rolled the row back, the id stays unspent"))))


(deftest a-fresh-id-provisions-and-secures-its-userdb
  (testing "the guard does not get in the way of a normal provision"
    (let [db      (migrated-db)
          secured (atom nil)]
      (with-redefs [db/exists? (constantly false)
                    db/use     (fn [name] name)
                    db/secure  (fn [name security] (reset! secured [name security]))]
        (let [{:keys [id token]} (#'sut/create-account! db)]
          (is (int? id))
          (is (= 40 (count token)))
          (is (= (str "userdb-" id) (first @secured)))
          (is (= [(str "u:" id)] (get-in (second @secured) [:members :roles]))))))))


(deftest a-failed-couch-step-leaves-no-half-created-account
  (testing "the row and the secured userdb exist together or not at all"
    (let [db        (migrated-db)
          destroyed (atom nil)]
      (with-redefs [db/exists? (constantly false)
                    db/use     (fn [name] {:name name})
                    db/secure  (fn [_ _] (throw (ex-info "couch is down" {})))
                    db/destroy (fn [db] (reset! destroyed (:name db)) (delay nil))]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"couch is down"
                              (sut/create-account! db))))
      (is (empty? (jdbc/execute! db ["SELECT id FROM users"]))
          "the insert rolled back, no orphan row")
      (is (= "userdb-1" @destroyed)
          "the userdb created for the rolled-back row is removed with it"))))


(deftest a-failed-provision-burns-nothing-for-the-next-attempt
  (testing "after a couch failure the same id provisions cleanly"
    (let [db (migrated-db)]
      (with-redefs [db/exists? (constantly false)
                    db/use     (fn [name] {:name name})
                    db/secure  (fn [_ _] (throw (ex-info "couch is down" {})))
                    db/destroy (fn [_] (delay nil))]
        (is (thrown? clojure.lang.ExceptionInfo (sut/create-account! db))))
      (with-redefs [db/exists? (constantly false)
                    db/use     (fn [name] {:name name})
                    db/secure  (fn [_ _] nil)]
        (is (= 1 (:id (sut/create-account! db)))
            "the id the failed attempt minted is minted again, not leaked")))))


(defn- ^File temp-dir
  []
  (.toFile (java.nio.file.Files/createTempDirectory
            "adopt-test"
            ^"[Ljava.nio.file.attribute.FileAttribute;"
            (into-array java.nio.file.attribute.FileAttribute []))))


(deftest a-legacy-database-moves-into-the-configured-home
  (testing "content and wal travel, the legacy trio disappears"
    (let [dir    (temp-dir)
          legacy (doto (File. dir "app.db") (spit "main-bytes"))
          _ (spit (File. dir "app.db-wal") "wal-bytes")
          _ (spit (File. dir "app.db-shm") "shm-bytes")
          target (File. dir "state/db.sqlite")]
      (#'sut/adopt-database! legacy {:dbname (.getPath target)})
      (is (= "main-bytes" (slurp target)))
      (is (= "wal-bytes" (slurp (File. dir "state/db.sqlite-wal"))))
      (is (not (.exists legacy)) "legacy main gone")
      (is (not (.exists (File. dir "app.db-wal"))) "legacy wal gone")
      (is (not (.exists (File. dir "app.db-shm"))) "legacy shm gone"))))


(deftest an-existing-target-is-never-clobbered
  (testing "rollback safety: newer data at the target survives, legacy stays for the operator"
    (let [dir    (temp-dir)
          legacy (doto (File. dir "app.db") (spit "old-bytes"))
          target (doto (File. dir "db.sqlite") (spit "newer-bytes"))]
      (#'sut/adopt-database! legacy {:dbname (.getPath target)})
      (is (= "newer-bytes" (slurp target)) "target untouched")
      (is (= "old-bytes" (slurp legacy)) "legacy left in place"))))


(deftest an-empty-pre-created-target-does-not-block-adoption
  (testing "systemd-tmpfiles pre-creates the target as a zero-length file (#225)"
    (let [dir    (temp-dir)
          legacy (doto (File. dir "app.db") (spit "real-bytes"))
          target (doto (File. dir "db.sqlite") (spit ""))]
      (#'sut/adopt-database! legacy {:dbname (.getPath target)})
      (is (= "real-bytes" (slurp target)) "adopted over the empty placeholder")
      (is (not (.exists legacy))))))


(deftest a-word-saved-in-an-inflected-form-gets-an-example-in-another-form
  (let [calls  (atom 0)
        lemmas {"explodiert" #{1 2} "explodierte" #{1 2} "explodieren" #{1}}]
    (with-redefs [examples/example-api-request
                  (support.generation/provider-answering-in-turn
                   calls
                   [(support.generation/example-completion
                     {:value       "Die Bombe explodierte laut im Hof."
                      :translation "Бомба громко взорвалась во дворе."
                      :structure   [{:usedForm "Bombe" :dictionaryForm "die Bombe" :translation "бомба"}
                                    {:usedForm "explodierte" :dictionaryForm "explodieren" :translation "взрываться"}
                                    {:usedForm "Hof" :dictionaryForm "der Hof" :translation "двор"}]})])
                  dictionary/lookup-dictionary-entries (constantly nil)
                  dictionary/lemma-ids (fn [form] (get lemmas (str/lower-case form) #{}))]
      (with-example-server
       real-generation
       (fn [ask]
         (let [response (ask (query "word" "explodiert" "translation" "взорванный"))]
           (is (= 200 (:status response)))
           (is (str/includes? (:body response) "Die Bombe explodierte laut im Hof."))
           (is (= 1 @calls))))))))

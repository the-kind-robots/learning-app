(ns backend.core-test
  (:require
   [backend.support.db :as support.db]
   [backend.support.generation :as support.generation]
   [cheshire.core :as cheshire]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [core :as sut]
   [db :as db]
   [examples :as examples]
   [examples.cache :as cache]
   [examples.dictionary :as dictionary]
   [examples.provider :as provider]
   [single-flight :as single-flight]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as result-set]
   [org.httpkit.client :as client]
   [org.httpkit.server :as server]
   [taoensso.telemere :as t]
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


(deftest the-invite-url-points-at-the-configured-public-origin
  (testing "without configuration the canonical origin is assumed"
    (is (= "https://sprecha.de" (#'sut/configured-public-url {}))))
  (testing "the environment wins"
    (is (= "https://example.test"
           (#'sut/configured-public-url {"LEARNING_APP__PUBLIC_URL" "https://example.test"}))))
  (testing "a trailing slash never doubles up in the URL"
    (is (= "https://example.test/#invite=abc"
           (#'sut/invite-url
            (#'sut/configured-public-url {"LEARNING_APP__PUBLIC_URL" "https://example.test/"})
            "abc")))))


(deftest the-mint-invite-command-prints-a-usable-invite-and-starts-no-server
  (let [db (migrated-db)]
    (with-redefs [sut/db-spec db
                  sut/adopt-legacy-database! (fn [])]
      (let [out   (with-out-str (sut/-main "mint-invite"))
            token (second (re-find #"/#invite=([0-9a-f]{40})" out))]
        (testing "the printed URL carries a grant the server will honour"
          (is (some? token) (str "no invite URL in output: " (pr-str out)))
          (is (true? (#'sut/burn-grant! db token))))
        (testing "the command serves nothing"
          (is (nil? @sut/server)))))))


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
                  examples/generate-one! (fn [_subject _word-meta]
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
                   examples/generate-one! (fn [subject _word-meta] (generate! subject))]
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


(deftest the-prompt-is-built-from-the-glosses-the-key-is-built-from
  (testing "spacing and order fold into the key, so they must fold into the generation too"
    (let [asked (atom [])]
      (with-example-server
       ;; What reaches the provider is the subject itself, glosses included.
       (fn [subject] (swap! asked conj (:translations subject)) generated)
       (fn [ask]
         (ask (query "word" "Hund" "translation" "  собака "))
         (is (= [["собака"]] @asked) "the gloss reaches the provider trimmed")
         (ask (query "word" "Hund" "translation" "собака"))
         (is (= [["собака"]] @asked) "and the untrimmed subject was cached under that same key")
         (ask (query "word" "Bank" "translation" "скамейка" "translation" "банк"))
         (ask (query "word" "Bank" "translation" "банк" "translation" "скамейка"))
         (is (= [["собака"] ["банк" "скамейка"]] @asked)
             "one generation for both orders, and the prompt gets the order the key has"))))))


(deftest a-word-the-dictionary-knows-is-a-different-subject
  (testing "the metadata goes into the prompt, so it goes into the key"
    (let [generations (atom 0)
          entry       {:pos         "noun"
                       :meta        {:cefr_level "a1"}
                       :translation [{:lang "ru" :value "собака"}]}]
      (with-example-server
       (fn [_] (swap! generations inc) generated)
       (fn [ask]
         (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
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


(def ^:private real-generation
  "The generation itself, kept before any test puts a stub in its place. The
   dictionary knows no word in these tests, so it is handed no reading. The attempts
   are named, since the shorter arity calls back through the stubbed var."
  (let [generate-one! examples/generate-one!]
    (fn [subject]
      (generate-one! subject nil 3))))


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
                      dictionary/lookup-dictionary-entries (constantly nil)]
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
        (with-redefs [provider/config (constantly
                                       (assoc config
                                              :api-url
                                              (str "http://localhost:"
                                                   (server/server-port provider-server)
                                                   "/")))
                      dictionary/lookup-dictionary-entries (constantly nil)]
          (with-example-server
           real-generation
           (fn [ask]
             (let [response (ask (query "word" "Hund" "translation" "собака"))]
               (is (= 429 (:status response)))
               (is (= "7" (get-in response [:headers :retry-after])))))))
        (finally
         (server/server-stop! provider-server))))))


(deftest the-dictionary-is-read-once-per-request
  (testing "one reading builds the key and goes into the prompt"
    (let [reads    (atom 0)
          prompted (atom [])
          entry    {:pos         "noun"
                    :meta        {:cefr_level "a1"}
                    :translation [{:lang "ru" :value "собака"}]}]
      (with-example-server
       (constantly generated)
       (fn [ask]
         (with-redefs [dictionary/lookup-dictionary-entries (fn [_] (swap! reads inc) [entry])
                       examples/generate-one! (fn [_subject word-meta]
                                                (swap! prompted conj word-meta)
                                                generated)]
           (is (= 200 (:status (ask (query "word" "Hund" "translation" "собака")))))
           (is (= 1 @reads) "a miss reads the dictionary once")
           (is (= [{:partOfSpeech "noun" :cefrLevel "a1"}] @prompted)
               "and the prompt is given that same reading")))))))


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


(deftest waiting-requests-do-not-starve-the-server
  (testing "twenty requests held on one generation leave another request served"
    ;; http-kit 2.8.1 runs handlers on virtual threads on JVM 21+, so a request
    ;; blocked on a generation, or on one it waits for, holds no pool worker.
    (let [release     (promise)
          virtual?    (atom #{})
          generations (atom 0)
          held-count  20]
      (with-example-server
       (fn [subject]
         (swap! virtual? conj (.isVirtual (Thread/currentThread)))
         (when (= "Hund" (:word subject))
           (swap! generations inc)
           @release)
         generated)
       (fn [ask]
         (support.generation/with-joins-counted
          (dec held-count)
          (fn [joined]
            (let [held (doall (repeatedly held-count
                                          #(future (ask (query "word" "Hund" "translation" "собака")))))]
              (try
                (is (true? (joined)) "nineteen requests joined the one generating")
                (is (= 200
                       (:status (deref (future (ask (query "word" "Katze" "translation" "кошка")))
                                       2000
                                       nil)))
                    "answered while twenty others wait")
                (finally
                 (deliver release true)))
              (is (every? #(= 200 (:status @%)) held))
              (is (= 1 @generations))
              (is (= #{true} @virtual?) "handlers run on virtual threads")))))))))


(deftest a-wedged-generation-does-not-hold-a-joined-request-to-the-proxy-timeout
  (testing "the joined request answers 503 itself inside single-flight's bound"
    (let [wedged  (promise)
          started (promise)]
      (with-redefs-fn {#'single-flight/longest-join-wait-ms 150}
        (fn []
          (with-example-server
           (fn [_]
             (deliver started true)
             @wedged)
           (fn [ask]
             (let [held (future (ask (query "word" "Hund" "translation" "собака")))]
               (try
                 (is (true? (deref started 2000 false)) "the first request is generating")
                 (let [response (deref (future (ask (query "word" "Hund" "translation" "собака")))
                                       2000
                                       nil)]
                   (is (= 503 (:status response)) "the joined request gave up at the bound")
                   (is (= "30" (get-in response [:headers :retry-after]))))
                 (finally
                  (deliver wedged generated)))
               (is (= 200 (:status @held))
                   "the run itself still answers its caller once it ends")))))))))


(deftest a-generation-answers-with-the-row-the-cache-kept
  (testing "another process stored its example first, and that one is served"
    (let [database (atom nil)]
      (with-example-server
       (fn [subject]
         (cache/store! @database
                       (examples/subject-key subject (examples/word-meta subject))
                       subject
                       (assoc generated :value "Der Hund schläft."))
         generated)
       (fn [ask]
         (let [response (ask (query "word" "Hund" "translation" "собака"))]
           (is (= 200 (:status response)))
           (is (= "Der Hund schläft." (:value (cheshire/parse-string (:body response) true))))))
       (fn [db] (reset! database db)))))
  (testing "when this request wrote the row, the row is not read back"
    (let [lookups (atom 0)]
      (with-redefs [cache/lookup (fn [_db _key] (swap! lookups inc) nil)]
        (with-example-server
         (constantly generated)
         (fn [ask]
           (is (= 200 (:status (ask (query "word" "Hund" "translation" "собака")))))
           (is (= 1 @lookups) "only the lookup before generating")))))))


(deftest the-build-and-the-server-agree-on-where-the-version-lives
  (testing "the name is the only thing the two sides share, and nothing else checks it"
    (is (str/includes? (slurp "build.clj")
                       (str "\"" @#'sut/sw-version-resource "\""))
        "build.clj writes the service worker version under a different name than core.clj reads")))


(deftest the-precache-list-is-the-shell-and-not-whatever-is-on-disk
  (testing "an asset added to a shell directory joins with no edit here"
    (is (contains? (set (#'sut/shell-assets ["/css/blocks/brand-new.css"]))
                   "/css/blocks/brand-new.css")))
  (testing "anything else a checkout collects is ignored"
    ;; An old build's output, a downloaded file, the worker's own source and
    ;; the metrics library a development build loads: each was served, none
    ;; belongs in a cache.addAll that must not reject.
    (is (= (#'sut/shell-assets [])
           (#'sut/shell-assets
            ["/js/cljs-runtime/cljs.core.js"
             "/js/app/cljs-runtime/goog.base.js"
             "/js/sw.js"
             "/js/web-vitals.js"
             "/dictionary.sqlite3"
             "/styles.css.orig"]))))
  (testing "the shell is there with nothing found at all"
    (is (= (sort (#'sut/shell-assets []))
           (sort @#'sut/shell-asset-files)))))


(deftest every-precached-path-is-a-file-that-exists
  (testing "a named asset that was deleted would reject the atomic install"
    (doseq [path (remove #{"/" "/js/app/main.js"} (#'sut/precache-paths))]
      (is (.exists (File. (str "resources/public" path))) path))))


(deftest the-new-secret-name-is-read
  (is (= "new" (#'sut/configured-db-auth-secret {"LEARNING_APP__DB_AUTH_SECRET" "new"} false))))


(deftest the-old-secret-name-still-works-until-the-unit-renames
  (is (= "old" (#'sut/configured-db-auth-secret {"LEARNING_APP_DB_AUTH_SECRET" "old"} false))))


(deftest the-new-secret-name-wins-when-both-are-set
  (is (= "new"
         (#'sut/configured-db-auth-secret
          {"LEARNING_APP_DB_AUTH_SECRET"  "old"
           "LEARNING_APP__DB_AUTH_SECRET" "new"}
          false))))


(deftest a-packaged-app-without-a-secret-refuses-to-start
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"LEARNING_APP__DB_AUTH_SECRET"
                        (#'sut/configured-db-auth-secret {} false))))


(deftest a-checkout-falls-back-to-the-well-known-secret
  (is (= "secret" (#'sut/configured-db-auth-secret {} true))))


(deftest a-packaged-app-without-a-couchdb-password-refuses-to-start
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"LEARNING_APP__COUCHDB_PASSWORD"
                        (#'sut/require-couchdb-password! {} false))))


(deftest a-couchdb-password-or-a-checkout-satisfies-the-guard
  (is (nil? (#'sut/require-couchdb-password! {"LEARNING_APP__COUCHDB_PASSWORD" "x"} false)))
  (is (nil? (#'sut/require-couchdb-password! {} true))))


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


(deftest no-legacy-database-means-no-adoption
  (let [dir    (temp-dir)
        target (File. dir "db.sqlite")]
    (#'sut/adopt-database! (File. dir "app.db") {:dbname (.getPath target)})
    (is (not (.exists target)))))


(deftest the-default-path-is-its-own-home
  (testing "dev: legacy and target are the same file, nothing moves"
    (let [dir (temp-dir)
          db  (doto (File. dir "app.db") (spit "dev-bytes"))]
      (#'sut/adopt-database! db {:dbname (.getPath db)})
      (is (= "dev-bytes" (slurp db))))))


(deftest an-empty-pre-created-target-does-not-block-adoption
  (testing "systemd-tmpfiles pre-creates the target as a zero-length file (#225)"
    (let [dir    (temp-dir)
          legacy (doto (File. dir "app.db") (spit "real-bytes"))
          target (doto (File. dir "db.sqlite") (spit ""))]
      (#'sut/adopt-database! legacy {:dbname (.getPath target)})
      (is (= "real-bytes" (slurp target)) "adopted over the empty placeholder")
      (is (not (.exists legacy))))))


(defn- signal-of
  "The last signal a query on db leaves, with debug signals let through."
  [db sql-params]
  (t/with-min-level :debug
                    (t/with-signal
                     (sut/on-connection [conn db]
                       (try (jdbc/execute! conn sql-params)
                            (catch Exception _))))))


(deftest a-query-leaves-a-signal-with-its-sql-and-no-parameters
  (let [db (migrated-db)]
    (testing "a successful query is logged at debug"
      (let [{:keys [level id data]} (signal-of db ["SELECT ? AS secret" "tok-123"])]
        (is (= [:debug :core/query] [level id]))
        (is (= "SELECT ? AS secret" (:sql data)))
        (is (nat-int? (:ms data)))
        (is (not (str/includes? (pr-str data) "tok-123")))))
    (testing "a failing query is logged at error with its cause"
      (let [{:keys [level id data error]} (signal-of db ["SELECT * FROM no_such_table"])]
        (is (= [:error :core/query-failed] [level id]))
        (is (= "SELECT * FROM no_such_table" (:sql data)))
        (is (instance? Throwable error))))))

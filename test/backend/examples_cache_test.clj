(ns backend.examples-cache-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [backend.support.db :as support.db]
   [examples :as examples]
   [examples.cache :as sut]
   [examples.provider :as provider]
   [malli.core :as m]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as result-set])
  (:import
   [java.io File]))


(set! *warn-on-reflection* true)


(defn- migrated-db
  []
  (support.db/migrated-db "examples-cache-test"))


(defn- db-without-the-table
  "A database the migrations never ran on, so every statement against
   `example_cache` throws."
  []
  (let [file (doto (File/createTempFile "examples-cache-test-broken" ".db")
               (.deleteOnExit))]
    {:dbtype "sqlite" :dbname (.getAbsolutePath file)}))


(def ^:private example
  "What the endpoint serves and the cache keeps: the provider's answer with the
   `wordIndex` the backend assigned."
  {:structure   [{:dictionaryForm "der Hund"
                  :translation    "собака"
                  :usedForm       "Hund"
                  :wordIndex      1}]
   :translation "Собака лает."
   :value       "Der Hund bellt."})


(defn- key-of
  [asked]
  (sut/digest (examples/question asked)))


(deftest the-same-question-asked-differently-is-one-key
  (testing "gloss order carries no meaning across devices"
    (is (= (key-of {:word "Hund" :translation ["собака" "пёс"]})
           (key-of {:word "Hund" :translation ["пёс" "собака"]}))))
  (testing "a repeated gloss is the same set"
    (is (= (key-of {:word "Hund" :translation ["собака"]})
           (key-of {:word "Hund" :translation ["собака" "собака"]}))))
  (testing "a blank gloss is no gloss"
    (is (= (key-of {:word "Hund" :translation ["собака"]})
           (key-of {:word "Hund" :translation ["собака" "   "]}))))
  (testing "surrounding whitespace is not part of the question"
    (is (= (key-of {:word "Hund" :translation ["собака"] :context "Tiere"})
           (key-of {:word " Hund " :translation [" собака "] :context " Tiere "}))))
  (testing "one gloss as a bare string is one gloss"
    (is (= (key-of {:word "Hund" :translation ["собака"]})
           (key-of {:word "Hund" :translation "собака"})))))


(deftest a-different-question-is-a-different-key
  (testing "German case is meaning, not formatting"
    (is (not= (key-of {:word "Essen"}) (key-of {:word "essen"}))))
  (testing "the collection context is part of the key"
    (is (not= (key-of {:word "Bank" :translation ["скамейка"]})
              (key-of {:word "Bank" :translation ["скамейка"] :context "Park"}))))
  (testing "a blank context is no context"
    (is (= (key-of {:word "Bank" :translation ["скамейка"]})
           (key-of {:word "Bank" :translation ["скамейка"] :context "  "}))))
  (testing "a different gloss set is a different question"
    (is (not= (key-of {:word "Bank" :translation ["скамейка"]})
              (key-of {:word "Bank" :translation ["банк"]}))))
  (testing "no gloss can be typed that composes another question's key"
    (is (not= (key-of {:word "Bank" :translation ["a" "b"]})
              (key-of {:word "Bank" :translation ["a\" \"b"]})))
    (is (not= (key-of {:word "Bank" :translation ["a"] :context "b"})
              (key-of {:word "Bank" :translation ["a\"] \"b"]})))))


(deftest a-stored-example-comes-back-under-its-question
  (let [db       (migrated-db)
        question (examples/question {:word "Hund" :translation ["пёс" "собака"] :context "Tiere"})]
    (is (nil? (sut/lookup db question)))
    (sut/store! db question example)
    (testing "the same question, glosses in the other order"
      (is (= example
             (sut/lookup db
                         (examples/question {:word        "Hund"
                                        :translation ["собака" "пёс"]
                                        :context     "Tiere"})))))
    (testing "another context is another question"
      (is (nil? (sut/lookup db (examples/question {:word "Hund" :translation ["собака" "пёс"]})))))))


(deftest what-comes-back-is-what-went-in
  (testing "the keys the client reads by survive the round trip as they were written"
    (let [db       (migrated-db)
          question (examples/question {:word "Hund" :translation ["собака"]})]
      (sut/store! db question example)
      (let [stored (sut/lookup db question)]
        (is (= example stored))
        (is (= "Hund" (:usedForm (first (:structure stored))))
            "camelCase is the client's contract, not a JSON accident")
        (is (= 1 (:wordIndex (first (:structure stored))))
            "the index the backend assigned is part of the example")))))


(deftest a-row-that-does-not-parse-is-a-miss
  (let [db       (migrated-db)
        question (examples/question {:word "Hund" :translation ["собака"]})]
    (jdbc/execute! db
      ["INSERT INTO example_cache (question_sha256, word, translations, context, example)
        VALUES (?, ?, ?, ?, ?)"
       (sut/digest question) "Hund" "собака" nil "not json at all"])
    (is (nil? (sut/lookup db question))
        "a row nobody can read is a question nobody has answered")))


(deftest the-parts-the-key-was-built-from-are-readable
  (let [db (migrated-db)]
    (sut/store! db
                (examples/question {:word "Hund" :translation ["пёс" "собака"] :context "Tiere"})
                example)
    (is (= [{:word "Hund" :translations "пёс, собака" :context "Tiere"}]
           (jdbc/execute! db
             ["SELECT word, translations, context FROM example_cache"]
             {:builder-fn result-set/as-unqualified-kebab-maps})))))


(deftest an-edited-prompt-is-a-miss-not-a-stale-row
  (testing "the sentence depends on the prompt and the model, so the key does too"
    (let [asked    {:word "Hund" :translation ["собака"]}
          original (key-of asked)]
      (testing "the same question under the same generation is the same key"
        (is (= original (key-of asked))))
      (testing "an edited prompt"
        (with-redefs [examples/system-prompt (str examples/system-prompt "\nOne more rule.")]
          (is (not= original (key-of asked)))))
      (testing "another model"
        (with-redefs [provider/config (constantly {:model "another/model"})]
          (is (not= original (key-of asked))))))))


(deftest a-stored-answer-is-not-served-after-the-prompt-changes
  (let [db       (migrated-db)
        question (examples/question {:word "Hund" :translation ["собака"]})]
    (sut/store! db question example)
    (is (some? (sut/lookup db question)))
    (with-redefs [examples/system-prompt (str examples/system-prompt "\nOne more rule.")]
      (is (nil? (sut/lookup db (examples/question {:word "Hund" :translation ["собака"]})))
          "the row is still there, and nothing asks for it any more"))))


(deftest the-digest-material-does-not-depend-on-how-clojure-prints
  (testing "under a bound print length `pr-str` truncates, and two questions share a key"
    (binding [*print-length* 1]
      (is (not= (key-of {:word "Hund" :translation ["собака" "пёс"]})
                (key-of {:word "Hund" :translation ["собака" "кобель"]}))))))


(deftest the-question-a-caller-builds-is-the-one-this-namespace-takes
  (testing "the contract `digest` reads by, held here rather than checked there"
    (is (m/validate examples/question-schema
                    (examples/question {:word "Hund" :translation "собака"})))
    (is (not (m/validate examples/question-schema {:word "Hund" :translation ["собака"]}))
        "a raw map carries `:translation`, one letter from the key that matters")))


(deftest a-cache-that-cannot-be-read-answers-as-a-miss
  (testing "the table is an optimization, so a broken read is not an error"
    (is (nil? (sut/lookup (db-without-the-table) (examples/question {:word "Hund"}))))))


(deftest a-cache-that-cannot-be-written-drops-the-row
  (testing "the answer it would have saved has already been generated"
    (is (nil? (sut/store! (db-without-the-table) (examples/question {:word "Hund"}) example)))))


(deftest storing-the-same-question-twice-keeps-the-first-answer
  (let [db       (migrated-db)
        question (examples/question {:word "Hund"})
        later    (assoc example :value "Der Hund schläft.")]
    (sut/store! db question example)
    (sut/store! db question later)
    (is (= example (sut/lookup db question)))))

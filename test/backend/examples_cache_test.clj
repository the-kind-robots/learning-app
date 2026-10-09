(ns backend.examples-cache-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [backend.support.db :as support.db]
   [examples :as examples]
   [examples.cache :as sut]
   [examples.provider :as provider]
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


(defn- subject-key
  "The key `examples/get!` computes for a subject whose word the dictionary
   has no entry for."
  [subject]
  (examples/subject-key subject nil))


(defn- key-of
  [asked]
  (subject-key (examples/subject asked)))


(deftest the-same-subject-sent-differently-is-one-key
  (testing "gloss order carries no meaning across devices"
    (is (= (key-of {:word "Hund" :translation ["собака" "пёс"]})
           (key-of {:word "Hund" :translation ["пёс" "собака"]}))))
  (testing "a repeated gloss is the same set"
    (is (= (key-of {:word "Hund" :translation ["собака"]})
           (key-of {:word "Hund" :translation ["собака" "собака"]}))))
  (testing "a blank gloss is no gloss"
    (is (= (key-of {:word "Hund" :translation ["собака"]})
           (key-of {:word "Hund" :translation ["собака" "   "]}))))
  (testing "surrounding whitespace is not part of the subject"
    (is (= (key-of {:word "Hund" :translation ["собака"] :context "Tiere"})
           (key-of {:word " Hund " :translation [" собака "] :context " Tiere "}))))
  (testing "one gloss as a bare string is one gloss"
    (is (= (key-of {:word "Hund" :translation ["собака"]})
           (key-of {:word "Hund" :translation "собака"})))))


(deftest a-different-subject-is-a-different-key
  (testing "German case is meaning, not formatting"
    (is (not= (key-of {:word "Essen"}) (key-of {:word "essen"}))))
  (testing "the collection context is part of the key"
    (is (not= (key-of {:word "Bank" :translation ["скамейка"]})
              (key-of {:word "Bank" :translation ["скамейка"] :context "Park"}))))
  (testing "a blank context is no context"
    (is (= (key-of {:word "Bank" :translation ["скамейка"]})
           (key-of {:word "Bank" :translation ["скамейка"] :context "  "}))))
  (testing "a different gloss set is a different subject"
    (is (not= (key-of {:word "Bank" :translation ["скамейка"]})
              (key-of {:word "Bank" :translation ["банк"]}))))
  (testing "no gloss can be typed that composes another subject's key"
    (is (not= (key-of {:word "Bank" :translation ["a" "b"]})
              (key-of {:word "Bank" :translation ["a\" \"b"]})))
    (is (not= (key-of {:word "Bank" :translation ["a"] :context "b"})
              (key-of {:word "Bank" :translation ["a\"] \"b"]})))))


(deftest a-stored-example-comes-back-under-its-subject
  (let [db      (migrated-db)
        subject (examples/subject {:word "Hund" :translation ["пёс" "собака"] :context "Tiere"})]
    (is (nil? (sut/lookup db (subject-key subject))))
    (sut/store! db (subject-key subject) subject example)
    (testing "the same subject, glosses in the other order"
      (is (= example
             (sut/lookup db (key-of {:word        "Hund"
                                     :translation ["собака" "пёс"]
                                     :context     "Tiere"})))))
    (testing "another context is another subject"
      (is (nil? (sut/lookup db (key-of {:word "Hund" :translation ["собака" "пёс"]})))))))


(deftest what-comes-back-is-what-went-in
  (testing "the keys the client reads by survive the round trip as they were written"
    (let [db      (migrated-db)
          subject (examples/subject {:word "Hund" :translation ["собака"]})]
      (sut/store! db (subject-key subject) subject example)
      (let [stored (sut/lookup db (subject-key subject))]
        (is (= example stored))
        (is (= "Hund" (:usedForm (first (:structure stored))))
            "camelCase is the client's contract, not a JSON accident")
        (is (= 1 (:wordIndex (first (:structure stored))))
            "the index the backend assigned is part of the example")))))


(deftest a-row-that-does-not-parse-is-a-miss
  (let [db      (migrated-db)
        subject (examples/subject {:word "Hund" :translation ["собака"]})]
    (jdbc/execute! db
      ["INSERT INTO example_cache (question_sha256, word, translations, context, example)
        VALUES (?, ?, ?, ?, ?)"
       (subject-key subject) "Hund" "собака" nil "not json at all"])
    (is (nil? (sut/lookup db (subject-key subject)))
        "a row nobody can read is a subject with no example")))


(deftest an-edited-prompt-is-a-miss-not-a-stale-row
  (testing "the sentence depends on the prompt and the model, so the key does too"
    (let [asked    {:word "Hund" :translation ["собака"]}
          original (key-of asked)]
      (testing "the same subject under the same generation is the same key"
        (is (= original (key-of asked))))
      (testing "an edited prompt"
        (with-redefs [examples/system-prompt (str examples/system-prompt "\nOne more rule.")]
          (is (not= original (key-of asked)))))
      (testing "another model"
        (with-redefs [provider/config (constantly {:model "another/model"})]
          (is (not= original (key-of asked))))))))


(deftest a-stored-example-is-not-served-after-the-prompt-changes
  (let [db      (migrated-db)
        subject (examples/subject {:word "Hund" :translation ["собака"]})]
    (sut/store! db (subject-key subject) subject example)
    (is (some? (sut/lookup db (subject-key subject))))
    (with-redefs [examples/system-prompt (str examples/system-prompt "\nOne more rule.")]
      (is (nil? (sut/lookup db (key-of {:word "Hund" :translation ["собака"]})))
          "the row is still there, and nothing asks for it any more"))))


(deftest the-digest-material-does-not-depend-on-how-clojure-prints
  (testing "under a bound print length `pr-str` truncates, and two subjects share a key"
    (binding [*print-length* 1]
      (is (not= (key-of {:word "Hund" :translation ["собака" "пёс"]})
                (key-of {:word "Hund" :translation ["собака" "кобель"]}))))))


(deftest a-cache-that-cannot-be-read-is-a-miss
  (testing "the table is an optimization, so a broken read is not an error"
    (is (nil? (sut/lookup (db-without-the-table) (key-of {:word "Hund"}))))))


(deftest a-cache-that-cannot-be-written-drops-the-row
  (testing "the example it would have saved has already been generated"
    (let [subject (examples/subject {:word "Hund"})]
      (is (nil? (sut/store! (db-without-the-table) (subject-key subject) subject example))))))


(deftest storing-the-same-subject-twice-keeps-the-first-example
  (let [db      (migrated-db)
        subject (examples/subject {:word "Hund"})
        later   (assoc example :value "Der Hund schläft.")]
    (is (true? (sut/store! db (subject-key subject) subject example))
        "the first write says it wrote the row")
    (is (false? (sut/store! db (subject-key subject) subject later))
        "the second says a row was already there")
    (is (= example (sut/lookup db (subject-key subject))))))

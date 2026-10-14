(ns backend.examples-cache-test
  (:require
   [backend.support.db :as support.db]
   [cheshire.core :as cheshire]
   [clojure.test :refer [deftest is testing]]
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


(defn- row-key
  "The key the re-key gives a row, which it hands over shaped as a request."
  [row]
  (subject-key (examples/subject row)))


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


(deftest an-edited-prompt-or-another-model-keeps-the-key
  (let [asked    {:word "Hund" :translation ["собака"]}
        original (key-of asked)]
    (testing "an edited prompt"
      (with-redefs [examples/system-prompt (str examples/system-prompt "\nOne more rule.")]
        (is (= original (key-of asked)))))
    (testing "another model"
      (with-redefs [provider/config (constantly {:model "another/model"})]
        (is (= original (key-of asked)))))))


(deftest a-new-cache-version-is-another-key
  (let [asked    {:word "Hund" :translation ["собака"]}
        original (key-of asked)]
    (with-redefs [examples/cache-version (constantly "2")]
      (is (not= original (key-of asked))))))


(deftest a-stored-example-is-served-after-the-prompt-changes
  (let [db      (migrated-db)
        subject (examples/subject {:word "Hund" :translation ["собака"]})]
    (sut/store! db (subject-key subject) subject example)
    (with-redefs [examples/system-prompt (str examples/system-prompt "\nOne more rule.")]
      (is (= example (sut/lookup db (key-of {:word "Hund" :translation ["собака"]})))))))


(defn- insert-waiting-row!
  "A row as an earlier build stored it: under a key of its own, not yet re-keyed."
  [db old-key subject created-at]
  (jdbc/execute! db
    ["INSERT INTO example_cache (question_sha256, word, translations, context, example, created_at)
      VALUES (?, ?, ?, ?, ?, ?)"
     old-key
     (:word subject)
     (cheshire/generate-string (:translations subject))
     (:context subject)
     (cheshire/generate-string {:value old-key})
     created-at]))


(defn- keys-in
  [db]
  (into #{}
        (map :question_sha256)
        (jdbc/execute! db ["SELECT question_sha256 FROM example_cache"]
          {:builder-fn result-set/as-unqualified-maps})))


(deftest rows-stored-under-old-keys-are-found-after-the-rekey
  (let [db      (migrated-db)
        subject (examples/subject {:word "Hund" :translation ["собака"]})]
    (insert-waiting-row! db "old-key" subject 100)
    (is (nil? (sut/lookup db (subject-key subject))))
    (sut/rekey! db row-key)
    (is (= {:value "old-key"} (sut/lookup db (subject-key subject))))))


(deftest rows-that-land-on-one-key-collapse-to-the-oldest
  (let [db      (migrated-db)
        subject (examples/subject {:word "Hund" :translation ["собака"]})]
    (insert-waiting-row! db "newer" subject 300)
    (insert-waiting-row! db "oldest" subject 100)
    (insert-waiting-row! db "middle" subject 200)
    (is (= {:collapsed 2 :moved 1 :skipped 0} (dissoc (sut/rekey! db row-key) :elapsed-ms)))
    (is (= {:value "oldest"} (sut/lookup db (subject-key subject))))
    (is (= 1 (count (keys-in db))))))


(deftest a-row-stored-after-the-change-yields-to-an-older-waiting-one
  (let [db      (migrated-db)
        subject (examples/subject {:word "Hund" :translation ["собака"]})]
    (sut/store! db (subject-key subject) subject example)
    (insert-waiting-row! db "waiting" subject 1)
    (sut/rekey! db row-key)
    (is (= {:value "waiting"} (sut/lookup db (subject-key subject)))
        "the waiting row was stored first")))


(deftest a-second-rekey-reads-the-dictionary-for-nothing
  (let [db      (migrated-db)
        subject (examples/subject {:word "Hund" :translation ["собака"]})
        reads   (atom 0)
        counted (fn [row] (swap! reads inc) (row-key row))]
    (insert-waiting-row! db "old-key" subject 100)
    (sut/rekey! db counted)
    (sut/rekey! db counted)
    (is (= 1 @reads))))


(deftest a-dictionary-that-is-away-leaves-the-rows-for-the-next-start
  (let [db      (migrated-db)
        subject (examples/subject {:word "Hund" :translation ["собака"]})]
    (insert-waiting-row! db "old-key" subject 100)
    (is (nil? (sut/rekey! db (fn [_] (throw (ex-info "away" {}))))))
    (is (= #{"old-key"} (keys-in db)))
    (sut/rekey! db row-key)
    (is (= {:value "old-key"} (sut/lookup db (subject-key subject))))))


(deftest a-rekey-runs-through-more-rows-than-a-batch
  (let [db (migrated-db)]
    (doseq [n (range 1203)]
      (insert-waiting-row! db (str "old-" n)
                           (examples/subject {:word (str "Wort" n) :translation ["x"]})
                           n))
    (is (= {:collapsed 0 :moved 1203 :skipped 0} (dissoc (sut/rekey! db row-key) :elapsed-ms)))))


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


(defn- insert-raw-row!
  [db key translations]
  (jdbc/execute! db
    ["INSERT INTO example_cache (question_sha256, word, translations, context, example)
      VALUES (?, 'Hund', ?, NULL, '{}')" key translations]))


(deftest a-row-with-a-plain-string-gloss-is-found-by-a-request-for-it
  (let [db (migrated-db)]
    (insert-raw-row! db "legacy" "собака")
    (is (= {:collapsed 0 :moved 1 :skipped 0}
           (dissoc (sut/rekey! db row-key) :elapsed-ms)))
    (is (= {} (sut/lookup db (key-of {:word "Hund" :translation "собака"}))))))


(deftest a-plain-string-with-commas-is-one-gloss
  (let [db (migrated-db)]
    (insert-raw-row! db "legacy" "собака, пёс")
    (sut/rekey! db row-key)
    (is (= {} (sut/lookup db (key-of {:word "Hund" :translation "собака, пёс"}))))
    (is (nil? (sut/lookup db (key-of {:word "Hund" :translation ["собака" "пёс"]}))))))


(deftest a-row-with-no-readable-glosses-is-skipped-and-counted
  (let [db (migrated-db)]
    (insert-raw-row! db "blank" "  ")
    (insert-raw-row! db "numbers" "[1, 2]")
    (is (= {:collapsed 0 :moved 0 :skipped 2}
           (dissoc (sut/rekey! db row-key) :elapsed-ms)))
    (is (= #{"blank" "numbers"} (keys-in db)))))

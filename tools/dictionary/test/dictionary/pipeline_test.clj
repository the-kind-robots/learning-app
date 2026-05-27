(ns dictionary.pipeline-test
  (:require
   [cheshire.core :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [dictionary.core :as core]
   [dictionary.goethe :as goethe]))


(defn- write-temp-csv!
  "Write CSV rows to a temp file. Returns path string."
  [rows]
  (let [f (java.io.File/createTempFile "goethe-pipeline" ".csv")]
    (.deleteOnExit f)
    (with-open [w (clojure.java.io/writer f)]
      (doseq [row rows]
        (.write w (str/join "," row))
        (.write w "\n")))
    (.getAbsolutePath f)))


(deftest full-pure-pipeline-integration
  (testing "entries-from-lines → build-lemmas with synthetic data"
    (let [;; Synthetic Kaikki lines
          hund-1       (json/generate-string
                        {:word         "Hund"
                         :pos          "Noun"
                         :lang_code    "de"
                         :tags         ["masculine"]
                         :senses       [{:tags ["animal"]}]
                         :translations [{:lang_code "ru" :word "собака"}
                                        {:lang_code "en" :word "dog"}]
                         :forms        [{:form "Hunde"} {:form "Hundes"}]})
          hund-2       (json/generate-string
                        {:word         "Hund"
                         :pos          "Noun"
                         :lang_code    "de"
                         :tags         ["masculine"]
                         :senses       [{:tags ["insult"]}]
                         :translations [{:lang_code "ru" :word "пёс"}]
                         :forms        [{:form "Hunden"}]})
          verb         (json/generate-string
                        {:word         "gehen"
                         :pos          "Verb"
                         :lang_code    "de"
                         :tags         []
                         :senses       [{:tags ["motion"]}]
                         :translations [{:lang_code "ru" :word "идти"}]
                         :forms        [{:form "ging"} {:form "gegangen"}]})
          no-ru        (json/generate-string
                        {:word         "Katze"
                         :pos          "Noun"
                         :lang_code    "de"
                         :tags         ["feminine"]
                         :senses       [{:tags ["animal"]}]
                         :translations [{:lang_code "en" :word "cat"}]
                         :forms        []})
          english      (json/generate-string
                        {:word         "dog"
                         :pos          "Noun"
                         :lang_code    "en"
                         :tags         []
                         :senses       [{:tags []}]
                         :translations []
                         :forms        []})
          malformed    "{\"lang_code\": \"de\", broken"
          lines        [hund-1 hund-2 verb no-ru english malformed]

          ;; Goethe CSV
          goethe-path  (write-temp-csv! [["stem" "level"]
                                         ["hund" "a1"]
                                         ["geh" "a2"]
                                         ["katz" "b1"]])
          goethe-index (goethe/stem-level-index goethe-path)

          ;; Step 1: merge
          merge-result (core/entries-from-lines lines)]

      ;; Verify merge
      (is (= 6 (:total-lines merge-result)))
      (is (= 1 (:parse-errors merge-result)))
      ;; 3 entries: Hund (merged), gehen, Katze — English filtered at regex level
      (is (= 3 (count (:dump-entries merge-result))))

      ;; Step 2: build lemmas
      (let [[lemmas skip-count] (core/build-lemmas (:dump-entries merge-result) goethe-index {})]
        ;; Hund + gehen + Katze all have allowed POS → 0 skipped
        (is (= 3 (count lemmas)))
        (is (= 0 skip-count))

        ;; Hund has merged translations from both entries
        (let [hund-lemma (first (filter #(= "der Hund" (:value %)) lemmas))]
          (is (some? hund-lemma))
          (is (= 2 (count (:translations hund-lemma)))))

        ;; text-ids have expected prefix
        (is (every? #(str/starts-with? (:text-id %) "lemma:") lemmas))

        ;; CEFR-ranked entries have higher rank than unranked
        (let [hund-rank  (:rank (first (filter #(= "der Hund" (:value %)) lemmas)))
              katze-rank (:rank (first (filter #(= "die Katze" (:value %)) lemmas)))]
          (is (> hund-rank katze-rank)))))))

(ns dictionary.frequency-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [dictionary.frequency :as frequency]))


(defn- temp-file
  [content]
  (let [file (java.io.File/createTempFile "frequency" ".tsv")]
    (.deleteOnExit file)
    (spit file content)
    (.getAbsolutePath file)))


(deftest read-frequency-file-header-tsv
  (testing "loads header-based frequency file and normalizes words"
    (let [path   (temp-file "word\tcount\tsource\nStraße\t10.5\tsubtlex-de\nmachen\t100.25\tsubtlex-de\n")
          result (frequency/read-frequency-file path)]
      (is (= {:source "subtlex-de" :rank 2 :count 10.5}
             (get result "strasse")))
      (is (= {:source "subtlex-de" :rank 1 :count 100.25}
             (get result "machen"))))))


(deftest read-frequency-file-minimal-tsv
  (testing "loads word/count without header"
    (let [path   (temp-file "Hund\t5.0\nKatze\t10.5\n")
          result (frequency/read-frequency-file path)]
      (is (= 1 (get-in result ["katze" :rank])))
      (is (= 2 (get-in result ["hund" :rank]))))))

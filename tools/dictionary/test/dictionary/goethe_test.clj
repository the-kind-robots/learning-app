(ns dictionary.goethe-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [dictionary.goethe :as goethe]))


(defn- write-temp-csv!
  "Write CSV rows (vector of vectors) to a temp file. Returns path string."
  [rows]
  (let [f (java.io.File/createTempFile "goethe-test" ".csv")]
    (.deleteOnExit f)
    (with-open [w (io/writer f)]
      (doseq [row rows]
        (.write w (str/join "," row))
        (.write w "\n")))
    (.getAbsolutePath f)))


;; ---------------------------------------------------------------------------
;; stem-level-index
;; ---------------------------------------------------------------------------


(deftest stem-level-index-sort-by-length-desc
  (testing "stems are sorted by length descending"
    (let [path  (write-temp-csv! [["stem" "level"]
                                  ["ab" "a1"]
                                  ["abcde" "b1"]
                                  ["abc" "a2"]])
          index (goethe/stem-level-index path)]
      (is (= [["abcde" "b1"] ["abc" "a2"] ["ab" "a1"]] index)))))


(deftest stem-level-index-lowercase
  (testing "stems and levels are lowercased"
    (let [path  (write-temp-csv! [["stem" "level"]
                                  ["HUND" "A1"]])
          index (goethe/stem-level-index path)]
      (is (= [["hund" "a1"]] index)))))


(deftest stem-level-index-trim-whitespace
  (testing "trims whitespace from stems and levels"
    (let [path  (write-temp-csv! [["stem" "level"]
                                  ["  hund  " " a1 "]])
          index (goethe/stem-level-index path)]
      (is (= [["hund" "a1"]] index)))))


(deftest stem-level-index-skip-empty-rows
  (testing "skips rows with empty stem or level"
    (let [path  (write-temp-csv! [["stem" "level"]
                                  ["" "a1"]
                                  ["hund" ""]
                                  ["katze" "a2"]])
          index (goethe/stem-level-index path)]
      (is (= [["katze" "a2"]] index)))))


(deftest stem-level-index-header-only-csv
  (testing "returns empty vec for header-only CSV"
    (let [path  (write-temp-csv! [["stem" "level"]])
          index (goethe/stem-level-index path)]
      (is (= [] index)))))


;; ---------------------------------------------------------------------------
;; cefr-level
;; ---------------------------------------------------------------------------


(deftest cefr-level-exact-match
  (testing "exact match returns the level"
    (let [index [["hund" "a1"]]]
      (is (= "a1" (goethe/cefr-level index "Hund"))))))


(deftest cefr-level-longest-prefix
  (testing "returns level of longest matching stem"
    (let [index [["hundefutter" "a1"] ["hund" "a2"]]]
      (is (= "a1" (goethe/cefr-level index "Hundefutter"))))))


(deftest cefr-level-partial-prefix
  (testing "matches prefix when word is longer than stem"
    (let [index [["hund" "a1"]]]
      (is (= "a1" (goethe/cefr-level index "Hundefutter"))))))


(deftest cefr-level-no-match
  (testing "returns nil when no stem matches"
    (let [index [["katze" "a1"]]]
      (is (nil? (goethe/cefr-level index "Hund"))))))


(deftest cefr-level-case-insensitive
  (testing "matching is case-insensitive"
    (let [index [["hund" "a1"]]]
      (is (= "a1" (goethe/cefr-level index "HUND"))))))


(deftest cefr-level-empty-index
  (testing "returns nil for empty index"
    (is (nil? (goethe/cefr-level [] "Hund")))))

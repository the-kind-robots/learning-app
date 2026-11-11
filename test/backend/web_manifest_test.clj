(ns backend.web-manifest-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [web-manifest :as sut]))


(deftest a-language-without-screenshots-shows-the-english-ones
  (let [resource io/resource]
    (with-redefs [io/resource (fn [name & more]
                                (when-not (str/starts-with? name "public/screenshots/tr/")
                                  (apply resource name more)))]
      (is (= ["/screenshots/en/lesson.png"
              "/screenshots/en/word.png"
              "/screenshots/en/statistics.png"
              "/screenshots/en/collections.png"
              "/screenshots/en/lesson-night.png"]
             (map :src (:screenshots (sut/manifest "tr" "ue"))))))))

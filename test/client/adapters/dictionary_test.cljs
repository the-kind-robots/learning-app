(ns client.adapters.dictionary-test
  "Completion rows carry translations as elements (GH-362). The dictionary
   used to hand back one GROUP_CONCAT string and the adapter split it on `,`,
   which tore every translation that contains a comma — 1100 of them — into
   pieces. The query now emits a JSON array and the adapter reads elements."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.dictionary :as sut]
   [cljs.test :refer-macros [deftest is]]))


(defn- stub-db
  "A dictionary db whose worker answers every exec with `rows`, recording the
   exec options it was handed in `calls`."
  [rows calls]
  {:proxy #js {:exec (fn [opts]
                       (swap! calls conj opts)
                       (js/Promise.resolve (clj->js rows)))}})


(defn- row
  [lemma translations]
  {"lemma"        lemma
   "pos"          "noun"
   "has_exact"    1
   "translations" (js/JSON.stringify (clj->js translations))})


(deftest a-translation-containing-a-comma-stays-one-translation
  (async-testing "a comma inside a translation does not split it"
    (let [rows   [(row "indem" ["тем, что" "благодаря тому, что" "в то время как"])]
          result (await (sut/completions (stub-db rows (atom [])) "indem"))]
      (is (= [["тем, что" "благодаря тому, что" "в то время как"]]
             (mapv :translations result))
          "three stored translations stay three, character for character"))))


(deftest a-lemma-without-translations-gets-none
  (async-testing "an empty array and a null cell both yield no translations, not one blank one"
    (let [rows   [(row "Hund" [])
                  {"lemma" "Katze" "pos" "noun" "has_exact" 0 "translations" nil}]
          result (await (sut/completions (stub-db rows (atom [])) "h"))]
      (is (= [[] []] (mapv :translations result))))))


(deftest a-completion-carries-lemma-pos-exactness-and-matched-forms
  (async-testing "the rest of the row survives the rewrite; matched forms read as elements, a null cell as none"
    (let [rows   [{"lemma"        "Haus"
                   "pos"          "noun"
                   "has_exact"    1
                   "translations" "[\"дом\"]"
                   "matched_forms" "[\"haus\",\"hause\",\"hauses\"]"}
                  {"lemma"        "Hausaufgabe"
                   "pos"          "noun"
                   "has_exact"    0
                   "translations" "[\"домашнее задание\"]"}]
          result (await (sut/completions (stub-db rows (atom [])) "haus"))]
      (is (= [{:exact?       true
               :lemma        "Haus"
               :matched-forms ["haus" "hause" "hauses"]
               :pos          "noun"
               :translations ["дом"]}
              {:exact?       false
               :lemma        "Hausaufgabe"
               :matched-forms []
               :pos          "noun"
               :translations ["домашнее задание"]}]
             (vec result))))))


(deftest a-blank-prefix-asks-nothing
  (async-testing "a blank prefix never reaches the worker"
    (let [calls (atom [])]
      (is (= [] (await (sut/completions (stub-db [] calls) "   "))))
      (is (= [] @calls)))))

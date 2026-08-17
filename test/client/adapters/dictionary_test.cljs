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
                       (js/Promise.resolve (clj->js rows)))}
   :state (atom {:ready? true})})


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


(deftest translation-order-follows-the-query
  (async-testing "translations keep the order the query returned them in"
    (let [rows   [(row "Fenster" ["окно" "витрина" "форточка"])]
          result (await (sut/completions (stub-db rows (atom [])) "fenster"))]
      (is (= ["окно" "витрина" "форточка"]
             (:translations (first result)))))))


(deftest a-lemma-without-translations-gets-none
  (async-testing "an empty array yields no translations, not one blank one"
    (let [rows   [(row "Hund" [])]
          result (await (sut/completions (stub-db rows (atom [])) "hund"))]
      (is (= [] (:translations (first result)))))))


(deftest a-missing-translations-column-gets-none
  (async-testing "a null translations cell yields no translations"
    (let [rows   [{"lemma" "Hund" "pos" "noun" "has_exact" 0 "translations" nil}]
          result (await (sut/completions (stub-db rows (atom [])) "hund"))]
      (is (= [] (:translations (first result)))))))


(deftest a-completion-carries-lemma-pos-and-exactness
  (async-testing "the rest of the row survives the rewrite"
    (let [rows   [{"lemma"        "Haus"
                   "pos"          "noun"
                   "has_exact"    1
                   "translations" "[\"дом\"]"}
                  {"lemma"        "Hausaufgabe"
                   "pos"          "noun"
                   "has_exact"    0
                   "translations" "[\"домашнее задание\"]"}]
          result (await (sut/completions (stub-db rows (atom [])) "haus"))]
      (is (= [{:exact? true :lemma "Haus" :pos "noun" :translations ["дом"]}
              {:exact?       false
               :lemma        "Hausaufgabe"
               :pos          "noun"
               :translations ["домашнее задание"]}]
             (vec result))))))


(deftest a-prefix-matching-nothing-yields-nothing
  (async-testing "no matching lemmas means no completions"
    (is (= [] (vec (await (sut/completions (stub-db [] (atom [])) "xyzq")))))))


(deftest the-prefix-range-is-bound-normalized
  (async-testing "the umlaut prefix is normalized and bounded before binding"
    (let [calls (atom [])]
      (await (sut/completions (stub-db [] calls) "Über"))
      (is (= ["ueber" "ueberz" "ueber"]
             (vec (.-bind ^js (first @calls))))))))


(deftest a-blank-prefix-asks-nothing
  (async-testing "a blank prefix never reaches the worker"
    (let [calls (atom [])]
      (is (= [] (await (sut/completions (stub-db [] calls) "   "))))
      (is (= [] @calls)))))


(deftest an-unready-dictionary-asks-nothing
  (async-testing "an unready dictionary answers empty without an exec"
    (let [calls (atom [])
          db    (assoc (stub-db [] calls) :state (atom {:ready? false}))]
      (is (= [] (await (sut/completions db "hund"))))
      (is (= [] @calls)))))

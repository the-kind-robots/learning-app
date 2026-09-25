(ns client.presenter.vocabulary-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [pages.words.actions :as actions]
   [pages.words.presenter :as sut]))


(def ^:private page-size
  "One page of the word list, as `pages.words.actions` asks for it."
  50)


(deftest word-item-props-builds-view-model
  (testing "maps retrieval row into view props"
    (let [props (sut/word-item-props
                 {:id          "word-1"
                  :value       "der Hund"
                  :translation [{:lang "ru" :value "пёс"}]
                  :retention-level 42.5})]
      (is (= "word-1" (:id props)))
      (is (= "der Hund" (:value props)))
      (is (= "пёс" (:translation props)))
      (is (= 42.5 (:retention-level props))))))


(deftest the-presenter-decides-what-is-a-phrase
  (testing "the kind is read here, so the view never compares"
    (let [phrase (sut/word-item-props
                  {:id          "vocab:auf jeden fall"
                   :kind        "phrase"
                   :value       "auf jeden Fall"
                   :translation [{:lang "ru" :value "во всяком случае, обязательно"}]})
          word   (sut/word-item-props
                  {:id          "vocab:der hund"
                   :value       "der Hund"
                   :translation [{:lang "ru" :value "пёс"}]})]
      (is (true? (:phrase? phrase)))
      (is (false? (:phrase? word)))
      (is (= "во всяком случае, обязательно" (:translation phrase))
          "a phrase translation reaches the row whole"))))


(deftest word-list-props-maps-all-items
  (testing "maps all retrieval rows"
    (let [rows  [{:id "word-1" :value "der Hund" :translation [{:lang "ru" :value "пёс"}] :retention-level 10}
                 {:id "word-2" :value "die Katze" :translation [{:lang "ru" :value "кот"}] :retention-level 20}]
          items (sut/word-list-props rows)]
      (is (= 2 (count items)))
      (is (= ["word-1" "word-2"] (mapv :id items))))))


(defn- shown
  "A read as the action stores it, next to what the presenter makes of it."
  [read]
  (let [state (actions/words-shown read)]
    (merge state (sut/page-props state))))


(def ^:private rows
  [{:id "word-1" :value "der Hund" :translation [{:lang "ru" :value "пёс"}] :retention-level 10}])


(deftest an-empty-vocabulary-invites-a-first-word
  (testing "no words in scope at all"
    (let [props (shown {:search "" :total 0 :words []})]
      (is (= "Слов пока нет" (:text (:empty-state props))))
      (is (= "Добавить слово" (:cta (:empty-state props)))
          "the first-run state keeps its call to action")
      (is (false? (:vocabulary? props))
          "the page chrome is dropped — nothing to search or study"))))


(deftest a-filter-with-no-matches-does-not-claim-the-vocabulary-is-empty
  (testing "words in scope, none of them matching"
    (let [props (shown {:search "zzz" :total 1 :words []})]
      (is (= "Ничего не найдено" (:text (:empty-state props))))
      (is (= "Попробуйте другой запрос" (:hint (:empty-state props))))
      (is (nil? (:cta (:empty-state props)))
          "no invitation to add a first word")
      (is (true? (:vocabulary? props))
          "the search box stays, so the query can be edited or cleared")
      (is (= "zzz" (:search props))))))


(deftest a-filter-with-matches-leaves-no-empty-state
  (testing "matching rows reach the view"
    (let [props (shown {:search "Hund" :total 1 :words rows})]
      (is (nil? (:empty-state props)))
      (is (= ["word-1"] (mapv :id (:items props))))
      (is (true? (:vocabulary? props))))))


(defn- word-rows
  [n]
  (for [i (range n)]
    {:id (str "word-" i) :value (str "Wort" i) :translation [] :retention-level i}))


(deftest a-page-that-did-not-exhaust-the-matches-keeps-the-sentinel
  (testing "fewer rows than the filter matched means another page follows"
    (let [props (shown {:limit   page-size
                        :matches 137
                        :total   137
                        :words   (word-rows page-size)})]
      (is (= 50 (count (:items props)))
          "the first page is one page of rows, not the vocabulary")
      (is (true? (:more? props)))
      (is (= 50 (:words/limit props))
          "the loaded row count rides in state, so a reload can ask for it again"))))


(deftest the-last-page-drops-the-sentinel
  (testing "every matching row on screen"
    (let [props (shown {:limit   100
                        :matches 60
                        :total   60
                        :words   (word-rows 60)})]
      (is (false? (:more? props)))
      (is (= 100 (:words/limit props)))))
  (testing "an empty list has nothing to append"
    (let [props (shown {:limit page-size :matches 0 :total 3 :words []})]
      (is (false? (:more? props))
          "a search with no match must not render a sentinel beside the placeholder"))))

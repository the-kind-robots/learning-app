(ns client.examples-backfill-test
  "Which pairs of an entry have no example: the rule
   (`domain.examples/missing-pairs`), without a database. The fetcher that
   walks memory and asks for them is tested in
   `client.examples-fetcher-test`."
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [domain.examples :as sut]
   [domain.vocabulary :as vocabulary]))


(def ^:private hund
  {:id "vocab:hund" :translation [{:lang "ru" :value "собака"}] :value "Hund"})


(def ^:private tiere
  {:id "coll-1" :name "Tiere" :word-ids ["vocab:hund"]})


(deftest an-entry-in-no-collection-misses-one-only-with-no-example-at-all
  (testing "no example"
    (is (= [{:word hund}] (sut/missing-pairs hund [] []))))
  (testing "any example covers it — the main card is a union"
    (is (empty? (sut/missing-pairs hund [] [{:word-id "vocab:hund" :collection-id "coll-1"}])))))


(deftest an-entry-in-a-collection-misses-an-example-of-that-collection
  (testing "its example came from elsewhere"
    (is (= [{:collection-id "coll-1" :collection-name "Tiere" :word hund}]
           (sut/missing-pairs hund [tiere] [{:word-id "vocab:hund" :collection-id nil}]))))
  (testing "nothing once that collection has its own"
    (is (empty? (sut/missing-pairs hund [tiere] [{:word-id "vocab:hund" :collection-id "coll-1"}]))))
  (testing "two collections each want their own"
    (is (= [{:collection-id "coll-1" :collection-name "Tiere" :word hund}
            {:collection-id "coll-2" :collection-name "Haus" :word hund}]
           (sut/missing-pairs hund [tiere {:id "coll-2" :name "Haus" :word-ids ["vocab:hund"]}] [])))))


(deftest what-a-read-sees-depends-on-the-collection-it-is-scoped-to
  (let [untethered {:word-id "vocab:hund" :collection-id nil}
        themed     {:word-id "vocab:hund" :collection-id "coll-1"}
        examples   [untethered themed]]
    (is (= [themed] (sut/visible-in "coll-1" examples))
        "a theme sees what was generated in it, and nothing else")
    (is (= examples (sut/visible-in nil examples))
        "a read outside every theme sees all of them")
    (is (empty? (sut/visible-in "coll-2" [untethered]))
        "and a theme that generated none of them sees none")))


(deftest a-phrase-is-missing-its-example-like-a-word
  (testing "the rule reads the entry, and a phrase is an entry with a kind"
    (let [phrase {:id          (vocabulary/vocab-id "auf jeden Fall")
                  :kind        "phrase"
                  :translation [{:lang "ru" :value "во всяком случае"}]
                  :value       "auf jeden Fall"}]
      (is (= [{:word phrase}] (sut/missing-pairs phrase [] []))))))

(ns client.collections-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [domain.collections :as domain]
   [use-cases.collections :as sut]))


(def ^:private items
  [{:id "c:kurs" :name "Kurs" :word-ids ["a" "b"]}
   {:id "c:k1" :name "Kurs / Kapitel 1" :word-ids ["b" "c"]}
   {:id "c:k2" :name " kurs /Kapitel 2 " :word-ids ["d"]}
   {:id "c:deep" :name "Kurs / A / B" :word-ids ["e"]}
   {:id "c:kursx" :name "Kursus / 1" :word-ids ["x"]}
   {:id "c:gram" :name "Grammatik" :word-ids ["g"]}])


(deftest the-scope-is-the-distinct-union-of-the-collection-and-its-children
  (is (= ["a" "b" "c" "d" "e"] (sut/scope-word-ids items "c:kurs"))
      "own words first, then the children's, b once; Kursus is not a child")
  (is (= ["b" "c"] (sut/scope-word-ids items "c:k1"))
      "a child has no children of its own")
  (is (= ["g"] (sut/scope-word-ids items "c:gram")))
  (is (nil? (sut/scope-word-ids items "c:gone"))))


(deftest names-are-compared-trimmed-and-case-insensitively
  (is (domain/same-name? " Kurs " "kurs"))
  (is (not (domain/same-name? "Kurs" "Kursus")))
  (is (= "Kurs" (domain/folder-key " Kurs / Kapitel 1")))
  (is (nil? (domain/folder-key "Kurs")))
  (is (= "A / B" (domain/child-name "Kurs / A / B")) "deeper slashes stay in the child")
  (is (domain/child-of? "kurs" "Kurs / Kapitel 1"))
  (is (not (domain/child-of? "Kurs" "Kurs"))))


(defn- port
  [active-id]
  {:collections {:collections/active-id (fn [] active-id)
                 :collections/list      (fn [] (js/Promise.resolve items))
                 :collections/create!   (fn [name] (js/Promise.resolve {:id (str "c:" name)}))}})


(deftest the-active-scope-reads-the-list-once
  (async-testing "the active collection's scope, or nil for main"
    (is (= ["a" "b" "c" "d" "e"] (await (sut/active-word-ids (port "c:kurs")))))
    (is (nil? (await (sut/active-word-ids (port nil)))))
    (is (nil? (await (sut/active-word-ids (port "c:gone")))))))


(deftest create-refuses-a-name-equal-after-trim-and-case
  (async-testing "the duplicate check is the parent lookup's equality"
    (testing "a duplicate by case and whitespace"
      (is (= {:noop :duplicate} (await (sut/create! (port nil) "  kurs ")))))
    (testing "a new name returns its id"
      (is (= {:ok :created :id "c:Neu"} (await (sut/create! (port nil) " Neu ")))))
    (testing "a blank name"
      (is (= {:error :invalid-name} (await (sut/create! (port nil) "   ")))))))

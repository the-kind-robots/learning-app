(ns client.collections-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [domain.collections :as collections]
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
  (is (collections/same-name? " Kurs " "kurs"))
  (is (not (collections/same-name? "Kurs" "Kursus")))
  (is (= "Kurs" (collections/folder-key " Kurs / Kapitel 1")))
  (is (nil? (collections/folder-key "Kurs")))
  (is (= "A / B" (collections/child-name "Kurs / A / B")) "deeper slashes stay in the child")
  (is (collections/child-of? "kurs" "Kurs / Kapitel 1"))
  (is (not (collections/child-of? "Kurs" "Kurs"))))


(defn- port
  ([active-id]
   (port active-id (atom [])))
  ([active-id renames]
   {:collections {:collections/active-id (fn [] active-id)
                  :collections/list      (fn [] (js/Promise.resolve items))
                  :collections/get       (fn [id] (js/Promise.resolve (some #(when (= id (:id %)) %) items)))
                  :collections/create!   (fn [name] (js/Promise.resolve {:id (str "c:" name)}))
                  :collections/rename!   (fn [id name]
                                           (swap! renames conj [id name])
                                           (js/Promise.resolve nil))}}))


(deftest the-active-scope-reads-the-list-once
  (async-testing "the active collection's scope, or nil for main"
    (is (= ["a" "b" "c" "d" "e"] (await (sut/active-word-ids (port "c:kurs")))))
    (is (nil? (await (sut/active-word-ids (port nil)))))
    (is (nil? (await (sut/active-word-ids (port "c:gone")))))))


(deftest rename-refuses-a-name-another-collection-carries
  (async-testing "the same equality as create: the current name stays, nothing is written"
    (let [renames (atom [])]
      (testing "a taken name by case and whitespace"
        (is (= {:name "Kurs" :noop :duplicate}
               (await (sut/rename-active! (port "c:kurs" renames) " grammatik "))))
        (is (= [] @renames)))
      (testing "a blank name keeps the current one"
        (is (= {:name "Kurs"} (await (sut/rename-active! (port "c:kurs" renames) "  "))))
        (is (= [] @renames)))
      (testing "the current name again writes nothing"
        (is (= {:name "Kurs"} (await (sut/rename-active! (port "c:kurs" renames) " Kurs "))))
        (is (= [] @renames)))
      (testing "the collection's own name in another case is a rename, not a duplicate"
        (is (= {:name "KURS" :renamed? true} (await (sut/rename-active! (port "c:kurs" renames) "KURS"))))
        (is (= [["c:kurs" "KURS"]] @renames)))
      (testing "a free name is written"
        (is (= {:name "Neu" :renamed? true} (await (sut/rename-active! (port "c:kurs" renames) " Neu "))))
        (is (= [["c:kurs" "KURS"] ["c:kurs" "Neu"]] @renames)))
      (testing "no active collection"
        (is (nil? (await (sut/rename-active! (port nil renames) "Neu"))))))))


(deftest create-refuses-a-name-equal-after-trim-and-case
  (async-testing "the duplicate check is the parent lookup's equality"
    (testing "a duplicate by case and whitespace"
      (is (= {:noop :duplicate} (await (sut/create! (port nil) "  kurs ")))))
    (testing "a new name returns its id"
      (is (= {:ok :created :id "c:Neu"} (await (sut/create! (port nil) " Neu ")))))
    (testing "a blank name"
      (is (= {:error :invalid-name} (await (sut/create! (port nil) "   ")))))))

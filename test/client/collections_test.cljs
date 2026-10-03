(ns client.collections-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.active-collection :as active-collection]
   [cljs.test :refer-macros [deftest is testing]]
   [domain.collections :as collections]
   [ports.learner :as learner]
   [use-cases.collections :as sut]))


(def ^:private items
  [{:id "c:kurs" :name "Kurs" :word-ids ["a" "b"]}
   {:id "c:k1" :name "Kurs / Kapitel 1" :word-ids ["b" "c"]}
   {:id "c:k2" :name " kurs /Kapitel 2 " :word-ids ["d"]}
   {:id "c:deep" :name "Kurs / A / B" :word-ids ["e"]}
   {:id "c:kursx" :name "Kursus / 1" :word-ids ["x"]}
   {:id "c:gram" :name "Grammatik" :word-ids ["g"]}])


(deftest the-scope-is-the-distinct-union-of-the-collection-and-its-children
  (is (= ["a" "b" "c" "d" "e"] (collections/scope-word-ids items "c:kurs"))
      "own words first, then the children's, b once; Kursus is not a child")
  (is (= ["b" "c"] (collections/scope-word-ids items "c:k1"))
      "a child has no children of its own")
  (is (= ["g"] (collections/scope-word-ids items "c:gram")))
  (is (nil? (collections/scope-word-ids items "c:gone"))))


(deftest names-are-compared-trimmed-and-case-insensitively
  (is (collections/same-name? " Kurs " "kurs"))
  (is (not (collections/same-name? "Kurs" "Kursus")))
  (is (= "Kurs" (collections/folder-key " Kurs / Kapitel 1")))
  (is (nil? (collections/folder-key "Kurs")))
  (is (= "A / B" (collections/child-name "Kurs / A / B")) "deeper slashes stay in the child")
  (is (collections/child-of? "kurs" "Kurs / Kapitel 1"))
  (is (not (collections/child-of? "Kurs" "Kurs"))))


(defn- port
  "What the use cases are handed: memory holding `items`, already caught
   up with the databases."
  ([active-id]
   (port active-id (atom [])))
  ([active-id renames]
   {:learner (assoc learner/reads
                    :learner/active-collection (fn [] (some #(when (= active-id (:id %)) %) items))
                    :learner/memory (fn [] {:collections (into {} (map (juxt :id identity)) items)})
                    :learner/catch-up! (fn [] (js/Promise.resolve nil))
                    :learner/create-collection! (fn [name] (js/Promise.resolve {:id (str "c:" name)}))
                    :learner/rename-collection! (fn [id name]
                                                  (swap! renames conj [id name])
                                                  (js/Promise.resolve {:id id :name name})))}))


(deftest the-summary-lists-collections-oldest-first
  (let [memory {:collections {"c:b" {:id "c:b" :created-at "2026-02" :name "B" :word-ids []}
                              "c:a" {:id "c:a" :created-at "2026-01" :name "A" :word-ids []}}
                :words       {"w" {:id "w"}}}]
    (is (= {:items       [{:id "c:a" :created-at "2026-01" :name "A" :word-ids []}
                          {:id "c:b" :created-at "2026-02" :name "B" :word-ids []}]
            :total-words 1}
           (sut/summary learner/reads memory)))))


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


(deftest the-active-collection-is-the-one-memory-has
  (let [store  (atom {:learner/memory {:collections {"c:kurs" {:id "c:kurs" :name "Kurs"}}}})
        active (:learner/active-collection (learner/start! {:store store}))]
    (testing "the remembered id names a collection memory has"
      (with-redefs [active-collection/active-collection-id (constantly "c:kurs")]
        (is (= {:id "c:kurs" :name "Kurs"} (active)))))
    (testing "a remembered id memory has nothing under — deleted here or on another device — is none"
      (with-redefs [active-collection/active-collection-id (constantly "c:gone")]
        (is (nil? (active)))))
    (testing "nothing remembered is none"
      (with-redefs [active-collection/active-collection-id (constantly nil)]
        (is (nil? (active)))))))

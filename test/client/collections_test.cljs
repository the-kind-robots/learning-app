(ns client.collections-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [cljs.test :refer-macros [are deftest is testing]]
   [clojure.test.check :as tc]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop :include-macros true]
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


(deftest two-versions-of-a-collection-reconcile-into-the-one-renamed-last-with-every-word
  (let [phone  {:_id "c" :_rev "2-a" :name "Kurs" :word-ids ["a" "b"] :created-at "2026-01-01" :renamed-at "2026-03-01"}
        laptop {:_id "c" :_rev "2-b" :name "Kurs 2" :word-ids ["b" "c"] :created-at "2026-01-01" :renamed-at "2026-05-01"}]
    (is (= {:_id "c" :_rev "2-b" :name "Kurs 2" :word-ids ["a" "b" "c"] :created-at "2026-01-01" :renamed-at "2026-05-01"}
           (collections/reconcile phone laptop))
        "the name and the revision are the last renamed version's; the words of both are listed once, sorted")))


(deftest a-version-never-renamed-counts-from-its-creation
  (are [expected-name a b] (= expected-name (:name (collections/reconcile a b)))
    "Neu"  {:_rev "1-a" :name "Alt" :created-at "2026-01-01"}
    {:_rev "1-b" :name "Neu" :created-at "2026-02-01"}
    "Neu"  {:_rev "2-a" :name "Alt" :created-at "2026-01-01" :renamed-at "2026-01-02"}
    {:_rev "1-b" :name "Neu" :created-at "2026-02-01"}
    "Zwei" {:_rev "1-a" :name "Eins" :created-at "2026-01-01"}
    {:_rev "1-b" :name "Zwei" :created-at "2026-01-01"}
    "Zehn" {:_rev "9-z" :name "Neun" :created-at "2026-01-01"}
    {:_rev "10-a" :name "Zehn" :created-at "2026-01-01"}))


(def ^:private gen-version
  (gen/let [generation (gen/choose 1 12)
            hash       (gen/fmap str gen/uuid)
            renamed-at (gen/elements [nil "2026-01-01" "2026-02-01" "2026-03-01"])
            name       (gen/elements ["Kurs" "Haus" "Tiere"])
            word-ids   (gen/fmap (comp vec sort) (gen/vector-distinct (gen/elements (mapv #(str "vocab:w" %) (range 12))) {:max-elements 8}))]
    (cond-> {:_id "c" :_rev (str generation "-" hash) :name name :word-ids word-ids :created-at "2026-01-01"}
      renamed-at (assoc :renamed-at renamed-at))))


(def ^:private reconcile-order-changes-nothing
  (prop/for-all [a gen-version
                 b gen-version]
    (= (collections/reconcile a b) (collections/reconcile b a))))


(def ^:private reconcile-grouping-changes-nothing
  (prop/for-all [a gen-version
                 b gen-version
                 c gen-version]
    (= (collections/reconcile (collections/reconcile a b) c)
       (collections/reconcile a (collections/reconcile b c)))))


(def ^:private reconciling-a-version-with-itself-changes-nothing
  (prop/for-all [a gen-version]
    (= a (collections/reconcile a a))))


(deftest reconciling-versions-does-not-depend-on-their-order-or-grouping
  (doseq [property [reconcile-order-changes-nothing
                    reconcile-grouping-changes-nothing
                    reconciling-a-version-with-itself-changes-nothing]]
    (let [result (tc/quick-check 200 property)]
      (is (:pass? result) (pr-str (select-keys result [:seed :num-tests :shrunk]))))))

(ns client.pages.collections-actions-test
  (:require
   [cljs.reader :as reader]
   [cljs.test :refer-macros [deftest is testing]]
   [nexus.registry :as nxr]
   [pages.collections.actions :as sut]))


(def ^:private memory
  {:collections {"collection:travel" {:id "collection:travel" :name "Travel" :word-ids ["vocab:hund"]}}
   :words       {"vocab:hund" {:id "vocab:hund"}}})


(def ^:private context
  {:active-id "collection:travel"})


(defn- reread
  "Equal data that shares no object with the original, as memory rebuilt from
   the same documents holds it."
  [value]
  (reader/read-string (pr-str value)))


(defn- shown
  [state]
  (merge state (sut/content state context)))


(deftest the-same-collections-save-nothing-new
  (testing "the merged state is the current map itself, so the render watch skips it"
    (let [state (shown {:learner/memory memory :learner/ready? true :page/current :page/collections})]
      (let [rebuilt (assoc state :learner/memory (reread memory))]
        (is (identical? rebuilt (shown rebuilt)))))))


(deftest new-collections-replace-the-old
  (let [state (shown {:learner/memory memory :learner/ready? true})
        saved (shown (assoc-in state [:learner/memory :collections] {}))]
    (is (not (identical? state saved)))
    (is (= [] (:collections/items saved)))))


(deftest before-memory-is-ready-the-screen-is-loading
  (let [state (shown {:collections/items [:kept] :learner/memory memory :learner/ready? false})]
    (is (true? (:collections/loading? state)))
    (is (= [:kept] (:collections/items state)) "the tiles already on screen stay")))


(deftest a-delete-announces-itself-and-keeps-no-state-for-it
  (let [show-deleted       (get-in (nxr/get-registry) [:nexus/actions :action/show-deleted])
        [_ focus announce] (show-deleted {} {} {:name "Solo" :focus-id "collection:travel"})]
    (is (= [:effect/focus-collection "collection:travel"] focus))
    (is (= [:effect/announce "Набор «Solo» удалён"] announce))))

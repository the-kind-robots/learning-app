(ns client.pages.collections-actions-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [nexus.registry :as nxr]
   [pages.collections.actions :as sut]))


(def ^:private memory
  {:collections {"collection:travel" {:id "collection:travel" :name "Travel" :word-ids ["vocab:hund"]}}
   :words       {"vocab:hund" {:id "vocab:hund"}}})


(def ^:private context
  {:active-id "collection:travel"})


(defn- shown
  [state]
  (merge state (sut/content state context)))


(deftest new-collections-replace-the-old
  (let [state (shown {:learner/memory memory :learner/readiness :basic})
        saved (shown (assoc-in state [:learner/memory :collections] {}))]
    (is (not (identical? state saved)))
    (is (= [] (:collections/items saved)))))


(deftest a-delete-announces-itself-and-keeps-no-state-for-it
  (let [show-deleted       (get-in (nxr/get-registry) [:nexus/actions :action/show-deleted])
        [_ focus announce] (show-deleted {} {} {:name "Solo" :focus-id "collection:travel"})]
    (is (= [:effect/focus-collection "collection:travel"] focus))
    (is (= [:effect/announce "app-status" "Набор «Solo» удалён"] announce))))

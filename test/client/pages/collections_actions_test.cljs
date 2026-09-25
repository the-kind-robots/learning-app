(ns client.pages.collections-actions-test
  (:require
   [cljs.reader :as reader]
   [cljs.test :refer-macros [deftest is testing]]
   [nexus.registry :as nxr]
   [pages.collections.actions :as sut]))


(def ^:private summary
  {:active-id   "collection:travel"
   :items       [{:id "collection:travel" :name "Travel" :word-ids ["vocab:hund"]}]
   :total-words 1})


(defn- reread
  "Equal data that shares no object with the original, as a reload reads it."
  [value]
  (reader/read-string (pr-str value)))


(deftest a-reload-with-the-same-data-saves-nothing-new
  (testing "the merged state is the current map itself, so the render watch skips it"
    (let [state (merge {:page/current :page/loading} (sut/collections-shown {} summary))]
      (is (identical? state (merge state (sut/collections-shown state (reread summary))))))))


(deftest new-data-replaces-the-old
  (let [state (merge {} (sut/collections-shown {} summary))
        next  (assoc summary :items [])
        saved (merge state (sut/collections-shown state next))]
    (is (not (identical? state saved)))
    (is (= [] (:collections/items saved)))))


(deftest a-delete-announces-itself-and-keeps-no-state-for-it
  (let [show-deleted (get-in (nxr/get-registry) [:nexus/actions :action/show-deleted])
        [[_ saved] focus announce] (show-deleted {} summary {:name "Solo" :focus-id "collection:travel"})]
    (is (= [:effect/focus-collection "collection:travel"] focus))
    (is (= [:effect/announce "Набор «Solo» удалён"] announce))
    (is (= (sut/collections-shown {} summary) saved)
        "the save carries the shown screen and nothing about the delete")))

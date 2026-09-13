(ns client.pages.collections-actions-test
  (:require
   [cljs.reader :as reader]
   [cljs.test :refer-macros [deftest is testing]]
   [pages.collections.actions :as sut]))


(def ^:private summary
  {:active-id "collection:travel"
   :items     [{:id "collection:travel" :name "Travel" :word-ids ["vocab:hund"] :words []}]
   :main      {:words [{:id "vocab:hund" :value "Hund"}]}})


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
        next  (assoc-in summary [:main :words] [])
        saved (merge state (sut/collections-shown state next))]
    (is (not (identical? state saved)))
    (is (= [] (get-in saved [:collections/main :words])))))

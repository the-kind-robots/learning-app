(ns pages.collections.presenter
  "What the themes screen shows, from the collections alone: one sequence of tiles in the learner's order, «Всё
   подряд» first, names with a `/` folded into a folder tile (ADR-0013). Every predicate the view needs is
   decided here."
  (:require
   [collection-accents :as colours]
   [domain.collections :as collections]))


(def main-target
  "The `data-collection-id` of «Всё подряд», which has no document."
  "main")


(def ^:private name-lang
  "The language a collection name is written in, so a long word hyphenates
   at its syllables. «Всё подряд» is not a collection name and has none."
  "de")


(defn- target
  "One tappable thing: a tile, a folder header or a row, its button and its name and count as the view's
   `target-attrs` and `name-and-count` read them."
  [state {:keys [id name lang count tap active? deletable?]}]
  {:target/id         id
   :target/name       name
   :target/lang       lang
   :target/count      count
   :target/active?    active?
   :target/editing?   (= id (:collections/editing-id state))
   :target/deletable? deletable?
   :target/tap        tap})


(defn- collection-target
  [state {:keys [id name word-ids]} shown-name]
  (target state
          {:id         id
           :name       shown-name
           :lang       name-lang
           :count      (count word-ids)
           :active?    (= id (:collections/active-id state))
           :deletable? true
           :tap        [[:action/handle-tab-click id]]}))


(defn- main-tile
  [state]
  (assoc (target state
                 {:id         main-target
                  :count      (:collections/total-words state)
                  :active?    (nil? (:collections/active-id state))
                  :deletable? false
                  :tap        [[:action/handle-main-tab-click]]})
         :target/main? true
         :tile/folder? false
         :tile/key     main-target))


(defn- plain-tile
  [state {:keys [collections name]}]
  (assoc (collection-target state (first collections) name)
         :tile/folder?
         false))


(defn- union-count
  "How many distinct word ids `collections` hold together. A JS Set, since
   the ids are strings that V8 has already hashed."
  [collections]
  (let [seen (js/Set.)]
    (doseq [collection collections
            word-id    (:word-ids collection)]
      (.add seen word-id))
    (.-size seen)))


(defn- folder-tile
  "A header for the parent and a row per child. Without a parent document the header is a label with nothing
   to tap, until the user creates the parent; both count the union of what they cover."
  [state {:keys [name parent children]}]
  (let [head (if parent
               (assoc (collection-target state parent name)
                      :target/count (union-count (cons parent children))
                      :folder-head/tappable? true)
               {:target/name  name
                :target/lang  name-lang
                :target/count (union-count children)
                :folder-head/tappable? false})]
    {:tile/folder? true
     :tile/head    head
     :tile/rows    (->> children
                        (map #(vector (collections/child-name (:name %)) %))
                        (sort-by first collections/compare-names)
                        (mapv (fn [[row-name child]] (collection-target state child row-name))))}))


(defn tiles
  "Every tile in reading order: «Всё подряд», then collections and folders in the learner's order, each with
   its colour and its key. A tile but «Всё подряд» can be dragged by its `:tile/sort-key`, by a touch once
   `:tile/armed?`: when a target of it is in its editing state."
  [state]
  (->> (colours/reading-order (:collections/items state))
       (map (fn [{:keys [accent folder? key] :as entry}]
              (let [{:tile/keys [head rows] :as tile} (if folder?
                                                        (folder-tile state entry)
                                                        (plain-tile state entry))]
                (assoc tile
                       :tile/accent   accent
                       :tile/armed?   (boolean (some :target/editing?
                                                     (if folder?
                                                       (cons head rows)
                                                       [tile])))
                       :tile/key      key
                       :tile/sort-key key))))
       (cons (assoc (main-tile state) :tile/accent (colours/accent-at 0)))
       vec))


(defn page-props
  [state]
  {:tiles (tiles state)})

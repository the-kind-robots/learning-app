(ns pages.collections.presenter
  "What the themes screen shows, from the collections alone: one alphabetical sequence of tiles, «Всё
   подряд» first, names with a `/` folded into a folder tile (ADR-0013). Every predicate the view needs is
   decided here."
  (:require
   [clojure.string :as str]
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
  "One tappable thing: a tile, a folder header or a row."
  [state {:keys [id name lang count tap active? deletable?]}]
  {:id         id
   :name       name
   :lang       lang
   :count      count
   :active?    active?
   :editing?   (= id (:collections/editing-id state))
   :deletable? deletable?
   :tap        tap})


(defn- deleted?
  "Whether `collection` was deleted on this visit and stands as its trace."
  [state collection]
  (some #(= (:id collection) (:id %)) (:collections/deleted state)))


(defn- collection-target
  "The target of a collection, or its trace when it was deleted on this visit."
  [state {:keys [id name word-ids] :as collection} shown-name]
  (if (deleted? state collection)
    {:id     id
     :name   shown-name
     :lang   name-lang
     :trace? true}
    (target state
            {:id         id
             :name       shown-name
             :lang       name-lang
             :count      (count word-ids)
             :active?    (= id (:collections/active-id state))
             :deletable? true
             :tap        [[:action/handle-tab-click id]]})))


(defn- main-tile
  [state]
  (assoc (target state
                 {:id         main-target
                  :count      (:collections/total-words state)
                  :active?    (nil? (:collections/active-id state))
                  :deletable? false
                  :tap        [[:action/handle-main-tab-click]]})
         :folder? false
         :key     main-target
         :main?   true))


(defn- plain-tile
  [state collection]
  (assoc (collection-target state collection (str/trim (:name collection)))
         :key     (:id collection)
         :folder? false))


(defn- union-count
  [state collections]
  (->> collections
       (remove #(deleted? state %))
       (mapcat :word-ids)
       distinct
       count))


(defn- folder-tile
  "A header for the parent and a row per child. Without a parent document the header is a label with nothing
   to tap, until the user creates the parent; both count the union of what they cover."
  [state {:keys [name parent children]}]
  (let [head (if parent
               (assoc (collection-target state parent name)
                      :count     (union-count state (cons parent children))
                      :tappable? true)
               {:name      name
                :lang      name-lang
                :count     (union-count state children)
                :tappable? false})]
    {:key     (str "folder:" (collections/canonical name))
     :folder? true
     :head    head
     :rows    (->> children
                   (map #(vector (collections/child-name (:name %)) %))
                   (sort-by first collections/compare-names)
                   (mapv (fn [[row-name child]] (collection-target state child row-name))))}))


(defn tiles
  "Every tile in reading order: «Всё подряд», then collections and folders alphabetically, each with its colour.
   A collection deleted on this visit keeps its place and colour as its trace."
  [state]
  (->> (colours/reading-order (concat (:collections/items state) (:collections/deleted state)))
       (map-indexed (fn [position {:keys [folder? collection] :as entry}]
                      (assoc (if folder? (folder-tile state entry) (plain-tile state collection))
                             :accent
                             (colours/accent-at (inc position)))))
       (cons (assoc (main-tile state) :accent (colours/accent-at 0)))
       vec))


(defn page-props
  [state]
  {:tiles (tiles state)})

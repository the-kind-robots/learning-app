(ns collection-accents
  "The colour of a collection, one rule for every screen that shows one: the themes screen's tiles and the cards
   of the word dialog. The stylesheet has a class for each colour."
  (:require
   [clojure.string :as str]
   [domain.collections :as collections]))


(def ^:private palette
  "The colours the stylesheet has for collections, in the order they are handed out."
  [:accent/owl :accent/macaw :accent/fox :accent/starfish :accent/beetle :accent/bee :accent/cardinal :accent/peacock])


(defn accent-at
  "The colour of the tile at `position` in the reading order, «Всё подряд» being 0; they go round."
  [position]
  (nth palette (mod position (count palette))))


(defn- entries
  "The plain collections and folders of `items`, unsorted. A name with a `/` is a child of the folder before it;
   a collection named as a folder's key is its parent; a folder without one is named by its first child's key."
  [items]
  (let [by-folder (group-by #(some-> (collections/folder-key (:name %)) collections/canonical) items)
        plain     (get by-folder nil)
        by-name   (group-by #(collections/canonical (:name %)) plain)
        parent-of (fn [key] (first (get by-name key)))
        parents   (set (keep parent-of (keys (dissoc by-folder nil))))]
    (concat
     (for [collection plain
           :when      (not (parents collection))]
       {:folder? false :name (str/trim (:name collection)) :collection collection})
     (for [[key children] (dissoc by-folder nil)
           :let [parent (parent-of key)]]
       {:folder?  true
        :name     (if parent
                    (str/trim (:name parent))
                    (collections/folder-key (:name (first (sort-by :name collections/compare-names children)))))
        :parent   parent
        :children children}))))


(defn reading-order
  "The entries of `items` in the order the themes screen shows them."
  [items]
  (sort-by :name collections/compare-names (entries items)))


(defn accents
  "The colour of every collection among `items`, `{collection-id colour}`: the one the tile of its folder has."
  [items]
  (into {}
        (for [[position {:keys [children collection parent]}] (map-indexed vector (reading-order items))
              {:keys [id]} (cons collection (cons parent children))
              :when        id]
          [id (accent-at (inc position))])))

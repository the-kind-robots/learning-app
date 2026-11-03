(ns collection-accents
  "The tiles of the themes screen and their colours, one rule for every screen that shows a collection: the
   themes screen's tiles and the cards of the word dialog. The stylesheet has a class for each colour."
  (:require
   [clojure.string :as str]
   [domain.collections :as collections]))


(def ^:private palette
  "The colours the stylesheet has for collections, in the order they are handed out."
  [:accent/owl :accent/macaw :accent/fox :accent/starfish :accent/beetle :accent/bee :accent/cardinal :accent/peacock])


(defn accent-at
  "The colour of the tile made `position`-th, «Всё подряд» being 0; they go round."
  [position]
  (nth palette (mod position (count palette))))


(defn- entries
  "The plain collections and folders of `items`, unsorted, each with every collection its tile shows and the
   tile's `:key`: what the page renders it by and a drag names it by. A name with a `/` is a child of the folder before it; a collection named as a folder's key is its
   parent; a folder without one is named by its first child's key."
  [items]
  (let [by-folder (group-by #(some-> (collections/folder-key (:name %)) collections/canonical) items)
        plain     (get by-folder nil)
        by-name   (group-by #(collections/canonical (:name %)) plain)
        parent-of (fn [key] (first (get by-name key)))
        parents   (set (keep parent-of (keys (dissoc by-folder nil))))]
    (concat
     (for [collection plain
           :when      (not (parents collection))]
       {:collections [collection]
        :folder?     false
        :key         (:id collection)
        :name        (str/trim (:name collection))})
     (for [[key children] (dissoc by-folder nil)
           :let [parent (parent-of key)]]
       {:children    children
        :collections (cond->> children parent (cons parent))
        :folder?     true
        :key         (str "folder:" key)
        :name        (if parent
                       (str/trim (:name parent))
                       (collections/folder-key (:name (first (sort-by :name collections/compare-names children)))))
        :parent      parent}))))


(defn- ordered-by
  "`entries` sorted by `rank` of their collections, alphabetically where it ties."
  [rank entries]
  (sort (fn [a b]
          (let [order (compare (rank (:collections a)) (rank (:collections b)))]
            (if (zero? order)
              (collections/compare-names (:name a) (:name b))
              order)))
        entries))


(defn reading-order
  "The entries of `items` in the order the themes screen shows them — by the lowest position among a tile's
   collections, a tile without one first — each with its `:accent`, by the order the tiles were made in."
  [items]
  (->> (entries items)
       (ordered-by #(first (sort (map :created-at %))))
       (map-indexed (fn [made entry] (assoc entry :accent (accent-at (inc made)))))
       (ordered-by #(some->> (seq (keep :position %)) (reduce min)))))


(defn accents
  "The colour of every collection among `items`, `{collection-id colour}`: the one its tile has."
  [items]
  (into {}
        (for [{:keys [accent collections]} (reading-order items)
              {:keys [id]} collections]
          [id accent])))

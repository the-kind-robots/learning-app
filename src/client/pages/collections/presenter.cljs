(ns pages.collections.presenter
  "What the themes screen shows, from the collections alone: tiles in
   columns, alphabetical by rows, «Всё подряд» first; names with a `/`
   folded into one folder tile (ADR-0013). Every predicate and class the
   view needs is decided here."
  (:require
   [clojure.string :as str]
   [domain.collections :as domain]))


(def main-target
  "The `data-collection-id` of «Всё подряд», which has no document."
  "main")


(def ^:private palette-size 8)


(def default-columns 2)


(defn- accent-class
  "The palette colour, cycled by the tile's position in the order."
  [position]
  (str "tile--accent-" (inc (mod position palette-size))))


(defn- target
  "One tappable thing: a tile, a folder header or a row."
  [state {:keys [id target-id name count tap active? deletable?]}]
  {:id           target-id
   :name         name
   :count        count
   :active?      active?
   :editing?     (and deletable? (= id (:collections/editing-id state)))
   :deletable?   deletable?
   :delete-label (when deletable? (str "Удалить набор «" name "»"))
   :tap          tap})


(defn- collection-target
  [state {:keys [id name word-ids]} shown-name]
  (target state
          {:id         id
           :target-id  id
           :name       shown-name
           :count      (count word-ids)
           :active?    (= id (:collections/active-id state))
           :deletable? true
           :tap        [[:action/handle-tab-click id]]}))


(defn- main-tile
  [state]
  (assoc (target state
                 {:target-id  main-target
                  :name       "Всё подряд"
                  :count      (:collections/total-words state)
                  :active?    (nil? (:collections/active-id state))
                  :deletable? false
                  :tap        [[:action/handle-main-tab-click]]})
         :key     main-target
         :folder? false))


(defn- plain-tile
  [state collection]
  (assoc (collection-target state collection (str/trim (:name collection)))
         :key     (:id collection)
         :folder? false))


(defn- union-count
  [collections]
  (count (distinct (mapcat :word-ids collections))))


(defn- folder-tile
  "A header for the parent and a row per child. Without a parent document
   the header still counts the children's union, and its tap creates the
   parent."
  [state {:keys [name parent children]}]
  (let [head (if parent
               (assoc (collection-target state parent name)
                      :count
                      (union-count (cons parent children)))
               (target state
                       {:target-id  (str "create:" name)
                        :name       name
                        :count      (union-count children)
                        :active?    false
                        :deletable? false
                        :tap        [[:action/handle-folder-header-click name]]}))]
    {:key     (str "folder:" (domain/canonical name))
     :folder? true
     :head    head
     :rows    (->> children
                   (map #(vector (domain/child-name (:name %)) %))
                   (sort-by first domain/compare-names)
                   (mapv (fn [[row-name child]] (collection-target state child row-name))))}))


(defn- entries
  "Plain collections and folders, unsorted. A collection whose name has a
   `/` is a child of the folder before it; a collection named as a folder's
   key is that folder's parent, not a plain tile. A folder without a parent
   is named by its first child's key."
  [items]
  (let [by-folder (group-by #(some-> (domain/folder-key (:name %)) domain/canonical) items)
        plain     (get by-folder nil)
        folders   (dissoc by-folder nil)
        parent-of (fn [key] (some #(when (= key (domain/canonical (:name %))) %) plain))
        parents   (set (keep parent-of (keys folders)))]
    (concat
     (for [collection plain
           :when      (not (parents collection))]
       {:folder? false :name (str/trim (:name collection)) :collection collection})
     (for [[key children] folders
           :let [parent (parent-of key)]]
       {:folder?  true
        :name     (if parent
                    (str/trim (:name parent))
                    (domain/folder-key (:name (first (sort-by :name domain/compare-names children)))))
        :parent   parent
        :children children}))))


(defn tiles
  "Every tile in reading order with its accent: «Всё подряд», then the
   collections and folders alphabetically by name."
  [state]
  (->> (entries (:collections/items state))
       (sort-by :name domain/compare-names)
       (map (fn [{:keys [folder? collection] :as entry}]
              (if folder?
                (folder-tile state entry)
                (plain-tile state collection))))
       (cons (main-tile state))
       (map-indexed (fn [position tile] (assoc tile :accent-class (accent-class position))))
       vec))


(defn- weight
  "A height estimate, in plain tiles: a folder is its header plus half a
   tile per row."
  [{:keys [folder? rows]}]
  (if folder?
    (inc (* 0.5 (count rows)))
    1))


(defn- lightest
  "The index of the smallest weight, the leftmost on a tie — `min-key`
   would take the rightmost."
  [weights]
  (reduce (fn [best i] (if (< (weights i) (weights best)) i best))
          0
          (range 1 (count weights))))


(defn columns
  "`n` columns of tiles, each tile in the currently lightest column, so
   reading order runs by rows."
  [tiles n]
  (:columns
   (reduce (fn [{:keys [columns weights]} tile]
             (let [i (lightest weights)]
               {:columns (update columns i conj tile)
                :weights (update weights i + (weight tile))}))
           {:columns (vec (repeat n []))
            :weights (vec (repeat n 0))}
           tiles)))


(defn page-props
  [state]
  {:loading? (boolean (:collections/loading? state))
   :columns  (columns (tiles state) (or (:collections/columns state) default-columns))})

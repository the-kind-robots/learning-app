(ns pages.collections.presenter
  "What the themes screen shows, from the collections alone: one
   alphabetical sequence of tiles, «Всё подряд» first; names with a `/`
   folded into one folder tile (ADR-0013). The CSS pours that sequence into
   columns, read down each in turn. Every predicate the view needs is
   decided here."
  (:require
   [clojure.string :as str]
   [domain.collections :as collections]))


(def main-target
  "The `data-collection-id` of «Всё подряд», which has no document."
  "main")


(defn- target
  "One tappable thing: a tile, a folder header or a row."
  [state {:keys [id name count tap active? deletable?]}]
  {:id           id
   :name         name
   :count        count
   :active?      active?
   :editing?     (= id (:collections/editing-id state))
   :deletable?   deletable?
   :delete-label (when deletable? (str "Удалить набор «" name "»"))
   :tap          tap})


(defn- collection-target
  [state {:keys [id name word-ids]} shown-name]
  (target state
          {:id         id
           :name       shown-name
           :count      (count word-ids)
           :active?    (= id (:collections/active-id state))
           :deletable? true
           :tap        [[:action/handle-tab-click id]]}))


(defn- main-tile
  [state]
  (assoc (target state
                 {:id         main-target
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
  "A header for the parent and a row per child. With a parent document the
   header is that collection; without one it is a label with nothing to
   tap, until the user creates the parent through «+». Both count the
   union of what they cover."
  [state {:keys [name parent children]}]
  (let [head (if parent
               (assoc (collection-target state parent name)
                      :count     (union-count (cons parent children))
                      :tappable? true)
               {:name      name
                :count     (union-count children)
                :tappable? false})]
    {:key     (str "folder:" (collections/canonical name))
     :folder? true
     :head    head
     :rows    (->> children
                   (map #(vector (collections/child-name (:name %)) %))
                   (sort-by first collections/compare-names)
                   (mapv (fn [[row-name child]] (collection-target state child row-name))))}))


(defn- entries
  "Plain collections and folders, unsorted. A collection whose name has a
   `/` is a child of the folder before it; a collection named as a folder's
   key is that folder's parent, not a plain tile. A folder without a parent
   is named by its first child's key."
  [items]
  (let [by-folder (group-by #(some-> (collections/folder-key (:name %)) collections/canonical) items)
        plain     (get by-folder nil)
        folders   (dissoc by-folder nil)
        parent-of (fn [key] (some #(when (= key (collections/canonical (:name %))) %) plain))
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
                    (collections/folder-key (:name (first (sort-by :name collections/compare-names children)))))
        :parent   parent
        :children children}))))


(defn tiles
  "Every tile in reading order: «Всё подряд», then the collections and
   folders alphabetically by name. The accent is the position in that order,
   and the tiles are the masonry's only children, so the CSS counts them
   itself rather than being handed a class per tile."
  [state]
  (->> (entries (:collections/items state))
       (sort-by :name collections/compare-names)
       (map (fn [{:keys [folder? collection] :as entry}]
              (if folder?
                (folder-tile state entry)
                (plain-tile state collection))))
       (cons (main-tile state))
       vec))


(defn page-props
  [state]
  {:loading? (boolean (:collections/loading? state))
   :tiles    (tiles state)})

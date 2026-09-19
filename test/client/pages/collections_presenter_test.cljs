(ns client.pages.collections-presenter-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [pages.collections.presenter :as sut]))


(defn- collection
  [id name & word-ids]
  {:id id :name name :word-ids (vec word-ids)})


(defn- names
  [tiles]
  (mapv #(if (:folder? %)
           [(:name (:head %)) (mapv :name (:rows %))]
           (:name %))
        tiles))


(deftest all-words-is-pinned-first-with-the-total
  (let [[main] (sut/tiles {:collections/items [] :collections/total-words 42})]
    (is (= "Всё подряд" (:name main)))
    (is (= 42 (:count main)))
    (is (= "main" (:id main)))
    (is (true? (:active? main)) "no active id means All Words is active")
    (is (false? (:deletable? main)))
    (is (= [[:action/handle-main-tab-click]] (:tap main)))))


(deftest tiles-sort-by-locale-and-ignore-case
  (let [tiles (sut/tiles {:collections/items [(collection "b" "bogen")
                                              (collection "a" "Ärger")
                                              (collection "z" "Zug")
                                              (collection "c" "Ausflug")]})]
    (is (= ["Всё подряд" "Ärger" "Ausflug" "bogen" "Zug"] (names tiles)))))


(deftest a-slash-makes-a-folder
  (testing "key and row text trimmed, deeper slashes kept in the row, one child is a folder"
    (let [tiles (sut/tiles {:collections/items [(collection "k2" " Kurs /Kapitel 2 ")
                                                (collection "k1" "Kurs / Kapitel 1")
                                                (collection "deep" "Kurs / A / B")
                                                (collection "g" "Grammatik / Konnektoren")
                                                (collection "a" "Alltag")]})]
      (is (= ["Всё подряд" "Alltag" ["Grammatik" ["Konnektoren"]] ["Kurs" ["A / B" "Kapitel 1" "Kapitel 2"]]]
             (names tiles))
          "folders sort by key among plain tiles; rows alphabetical"))))


(deftest the-header-is-the-parent-and-counts-the-union
  (let [items      [(collection "kurs" "Kurs" "a" "b")
                    (collection "k1" "Kurs / Kapitel 1" "b" "c")
                    (collection "k2" "Kurs / Kapitel 2" "d")]
        [_ folder] (sut/tiles {:collections/items items :collections/active-id "kurs" :collections/editing-id "k2"})
        {:keys [head rows]} folder]
    (is (true? (:folder? folder)))
    (is (= "Kurs" (:name head)))
    (is (= "kurs" (:id head)) "the header carries the parent's id")
    (is (true? (:tappable? head)))
    (is (= 4 (:count head)) "a, b, c, d — b once")
    (is (true? (:active? head)))
    (is (true? (:deletable? head)))
    (is (= [[:action/handle-tab-click "kurs"]] (:tap head)))
    (is (= [2 1] (mapv :count rows)))
    (is (= [false true] (mapv :editing? rows)))
    (is (= "Удалить набор «Kapitel 2»" (:delete-label (second rows))))
    (is (nil? (some #(= "kurs" (:id %)) (sut/tiles {:collections/items items})))
        "the parent is not also a plain tile")))


(deftest a-folder-without-a-parent-has-a-label-for-a-header
  (let [[_ folder] (sut/tiles {:collections/items [(collection "g" "Grammatik / Konnektoren" "x" "y")]})
        head       (:head folder)]
    (is (= {:name "Grammatik" :count 2 :tappable? false} head)
        "a caption with the children's union: no id, no tap, nothing to delete")
    (is (= [2] (mapv :count (:rows folder))))))


(deftest the-accent-cycles-by-position
  (let [tiles (sut/tiles {:collections/items (map #(collection (str %) (str "n" %)) (range 9))})]
    (is (= "tile--accent-1" (:accent-class (first tiles))))
    (is (= "tile--accent-8" (:accent-class (nth tiles 7))))
    (is (= "tile--accent-1" (:accent-class (nth tiles 8))))
    (is (= "tile--accent-2" (:accent-class (nth tiles 9))))))


(deftest page-props-are-one-sequence-of-tiles
  (let [props (sut/page-props {:collections/items    [(collection "b" "B") (collection "a" "A")]
                               :collections/loading? nil})]
    (is (false? (:loading? props)))
    (is (= ["Всё подряд" "A" "B"] (mapv :name (:tiles props)))
        "one alphabetical sequence; the columns are the stylesheet's job")))

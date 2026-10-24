(ns client.collection-accents-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [collection-accents :as sut]))


(def ^:private folders
  [{:id "c:kurs" :name "Kurs" :word-ids ["a" "b"]}
   {:id "c:k1" :name "Kurs / Kapitel 1" :word-ids ["b" "c"]}
   {:id "c:deep" :name "Kurs / A / B" :word-ids ["e"]}
   {:id "c:kursx" :name "Kursus / 1" :word-ids ["x"]}
   {:id "c:gram" :name "Grammatik" :word-ids ["g"]}])


(deftest a-collection-has-the-colour-of-its-folder-tile
  (is (= {"c:gram"  :accent/macaw
          "c:kurs"  :accent/fox
          "c:k1"    :accent/fox
          "c:deep"  :accent/fox
          "c:kursx" :accent/starfish}
         (sut/accents folders)))
  (testing "the colours go round after the last"
    (is (= [:accent/macaw :accent/fox :accent/starfish :accent/beetle :accent/bee :accent/cardinal :accent/peacock
            :accent/owl :accent/macaw]
           (let [names ["a" "b" "c" "d" "e" "f" "g" "h" "i"]
                 all   (map-indexed (fn [i n] {:id n :name n :word-ids [(str i)]}) names)]
             (mapv (sut/accents all) names))))))

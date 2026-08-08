(ns client.pages.lesson-view-test
  "Token-info card in the lesson page render tree (GH-257): the click on an
   annotated answer word saves :modal/type :token-info, and the page must
   actually render the dialog for that state — after the Replicant migration
   the dialog function existed but nothing called it."
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [domain.lesson :as domain]
   [pages.lesson.view :as sut]))


(defn- lesson-page-state
  [modal]
  (merge {:lesson/state (domain/initial-state
                         [{:id "word-1" :value "der Hund" :translation "пёс"}]
                         []
                         :first)}
         modal))


(defn- contains-node?
  [tree tag]
  (->> (tree-seq coll? seq tree)
       (some #(= tag %))
       boolean))


(deftest page-renders-token-info-dialog-for-modal-state
  (testing "modal state :token-info puts the dialog into the render tree"
    (let [page (sut/page (lesson-page-state
                          {:modal/data {:dictionary-form "die Katze"
                                        :state       :unknown-word
                                        :translation "кошка"}
                           :modal/type :token-info}))]
      (is (contains-node? page :dialog#token-info-dialog)))))


(deftest page-omits-token-info-dialog-without-modal-state
  (testing "no modal state — no dialog in the render tree"
    (is (not (contains-node? (sut/page (lesson-page-state {}))
                             :dialog#token-info-dialog)))))

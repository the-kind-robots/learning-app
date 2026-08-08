(ns client.pages.lesson-view-test
  "Token-info card in the lesson page render tree (GH-257, GH-273): the click
   on an annotated answer word saves :modal/type :token-info, and the page
   must render the anchored popover shell for that state — after the
   Replicant migration the card existed but nothing rendered it, and the
   first fix rendered it as a centered modal dialog instead of a popover."
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


(deftest page-renders-token-popover-for-modal-state
  (testing "modal state :token-info puts the anchored popover into the render tree"
    (let [page (sut/page (lesson-page-state
                          {:modal/data {:dictionary-form "die Katze"
                                        :state           :unknown-word
                                        :translation     "кошка"
                                        :word-index      2}
                           :modal/type :token-info}))]
      (is (contains-node? page :div#popover.popover))
      (is (not (contains-node? page :dialog#token-info-dialog))))))


(deftest page-omits-token-popover-without-modal-state
  (testing "no modal state — no popover in the render tree"
    (is (not (contains-node? (sut/page (lesson-page-state {}))
                             :div#popover.popover)))))

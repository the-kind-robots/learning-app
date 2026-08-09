(ns client.pages.lesson-view-test
  "Answer-hint popover in the lesson page render tree (GH-257, GH-273): the
   annotated answer is marked up in state, a click stores only the open word
   index, and the page renders the anchored popover for it — after the
   Replicant migration the card existed but nothing rendered it, and the
   first fix rendered it as a centered modal dialog instead of a popover."
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [pages.lesson.view :as sut]))


(def ^:private example-trial
  {:type      "example"
   :word-id   "word-1"
   :prompt    "Пёс спит"
   :answer    "Der Hund schlaeft."
   :structure [{:usedForm       "Hund"
                :dictionaryForm "der Hund"
                :translation    "пёс"
                :wordIndex      1}]
   :locked?   false})


(defn- lesson-page-state
  [hint-state]
  (merge {:lesson/state {:trials           [example-trial]
                         :remaining-trials [example-trial]
                         :current-trial    example-trial
                         :last-result      {:correct? true
                                            :answer   "Der Hund schlaeft."}}}
         hint-state))


(defn- contains-node?
  [tree tag]
  (->> (tree-seq coll? seq tree)
       (some #(= tag %))
       boolean))


(deftest page-renders-answer-hint-popover-for-open-index
  (testing "open hint index puts the anchored popover into the render tree"
    (let [page (sut/page (lesson-page-state
                          {:lesson/answer-hints    {1 :unknown-word}
                           :lesson/open-hint-index 1}))]
      (is (contains-node? page :div#popover.popover))
      (is (contains-node? page :button.token-card__button))
      (is (not (contains-node? page :dialog#token-info-dialog))))))


(deftest page-omits-answer-hint-popover-without-open-index
  (testing "no open hint index — no popover in the render tree"
    (is (not (contains-node? (sut/page (lesson-page-state {}))
                             :div#popover.popover)))))

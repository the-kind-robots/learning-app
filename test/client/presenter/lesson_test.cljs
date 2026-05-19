(ns client.presenter.lesson-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [domain.lesson :as domain]
   [pages.lesson.presenter :as sut]))


(deftest footer-props-returns-nil-without-result
  (testing "footer stays in input mode before answering"
    (let [state (domain/initial-state
                 [{:id "word-1" :value "der Hund" :translation "пёс"}]
                 []
                 :first)]
      (is (nil? (sut/footer-props state))))))


(deftest footer-props-includes-user-answer-for-example-trial
  (testing "wrong example answer includes both user and correct sentences"
    (let [state {:trials           [{:type      "example"
                                     :word-id   "word-1"
                                     :prompt    "Пёс спит"
                                     :answer    "Der Hund schlaeft."
                                     :structure [{:usedForm       "Hund"
                                                  :dictionaryForm "der Hund"
                                                  :translation    "пёс"
                                                  :wordIndex      1}]
                                     :locked?   false}]
                 :remaining-trials [{:type      "example"
                                     :word-id   "word-1"
                                     :prompt    "Пёс спит"
                                     :answer    "Der Hund schlaeft."
                                     :structure [{:usedForm       "Hund"
                                                  :dictionaryForm "der Hund"
                                                  :translation    "пёс"
                                                  :wordIndex      1}]
                                     :locked?   false}]
                 :current-trial    {:type      "example"
                                    :word-id   "word-1"
                                    :prompt    "Пёс спит"
                                    :answer    "Der Hund schlaeft."
                                    :structure [{:usedForm       "Hund"
                                                 :dictionaryForm "der Hund"
                                                 :translation    "пёс"
                                                 :wordIndex      1}]
                                    :locked?   false}
                 :last-result      {:correct? false
                                    :answer   "Mein Hund schlaeft"}}
          props (sut/footer-props state)]
      (is (= :error (:variant props)))
      (is (= "Mein Hund schlaeft" (:user-answer props)))
      (is (= "Der Hund schlaeft." (:correct-answer props)))
      (is (= [{:type :plain-word :text "Der" :word-index 0}
              {:type        :annotated-word
               :text        "Hund"
               :used-form   "Hund"
               :dictionary-form "der Hund"
               :translation "пёс"
               :word-index  1}
              {:type :plain-word :text "schlaeft." :word-index 2}]
             (:correct-answer-segments props))))))


(deftest footer-props-includes-user-answer-for-word-trial
  (testing "wrong word answer includes both user and correct values"
    (let [state (domain/initial-state
                 [{:id "word-1" :value "der Hund" :translation "пёс"}]
                 []
                 :first)
          state (domain/check-answer state "die Katze")
          props (sut/footer-props state)]
      (is (= :error (:variant props)))
      (is (= "die Katze" (:user-answer props)))
      (is (= "der Hund" (:correct-answer props))))))

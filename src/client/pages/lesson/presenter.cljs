(ns pages.lesson.presenter
  (:require
   [domain.lesson :as domain]))


(defn challenge-props
  [state]
  (let [trial (domain/current-trial state)]
    {:prompt      (:prompt trial)
     :is-example? (domain/example-trial? trial)}))


(defn progress-props
  [state]
  (domain/progress state))


(defn footer-props
  [state]
  (when-let [result (domain/last-result state)]
    (let [trial (domain/current-trial state)]
      {:variant        (if (:correct? result) :success :error)
       :correct-answer (domain/expected-answer state)
       :correct-answer-segments (when (domain/example-trial? trial)
                                  (domain/answer-segments trial))
       :user-answer    (:answer result)
       :finished?      (domain/finished? state)})))

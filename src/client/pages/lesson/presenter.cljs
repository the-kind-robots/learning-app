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


(defn- token-info-data
  [state]
  (when (= :token-info (:modal/type state))
    (:modal/data state)))


(defn token-popover-props
  "Props for the token-info popover, or nil when it is closed. The card
   auto-dismisses only for a word already stored with this translation."
  [state]
  (when-let [data (token-info-data state)]
    (assoc data :data-dismiss (when (= :known-with-translation (:state data)) "900"))))


(defn- answer-segments
  [trial open-token-index]
  (mapv (fn [{:keys [word-index] :as segment}]
          (assoc segment :expanded? (= word-index open-token-index)))
        (domain/answer-segments trial)))


(defn footer-props
  [state]
  (let [lesson-state (:lesson/state state)]
    (when-let [result (domain/last-result lesson-state)]
      (let [trial (domain/current-trial lesson-state)]
        {:variant        (if (:correct? result) :success :error)
         :correct-answer (domain/expected-answer lesson-state)
         :correct-answer-segments (when (domain/example-trial? trial)
                                    (answer-segments
                                     trial
                                     (:word-index (token-info-data state))))
         :user-answer    (:answer result)
         :finished?      (domain/finished? lesson-state)}))))

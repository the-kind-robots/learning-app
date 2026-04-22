(ns presenter.lesson
  "Transforms lesson state into view props.
   Views receive only what they need to render - no domain knowledge required."
  (:require
   [domain.lesson :as domain]))


(defn challenge-props
  "Props for the challenge display (prompt + instruction)."
  [state]
  (let [trial (domain/current-trial state)]
    {:prompt      (:prompt trial)
     :is-example? (domain/example-trial? trial)}))


(defn progress-props
  "Props for progress bar."
  [state]
  (domain/progress state))


(defn footer-props
  "Props for the footer area (input, success, or error state).
   Returns nil when no result yet (show input form).
   Returns map with :variant (:success/:error) and display data."
  [state]
  (when-let [result (domain/last-result state)]
    (let [trial (domain/current-trial state)]
      {:variant                 (if (:correct? result) :success :error)
       :correct-answer          (domain/expected-answer state)
       :correct-answer-segments (when (domain/example-trial? trial)
                                  (domain/answer-segments trial))
       :user-answer             (:answer result)
       :finished?               (domain/finished? state)})))

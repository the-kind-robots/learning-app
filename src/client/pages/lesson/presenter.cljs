(ns pages.lesson.presenter
  (:require
   [clojure.string :as str]
   [domain.lesson :as lesson]))


(defn challenge-props
  [state]
  (let [trial (lesson/current-trial state)]
    {:kind   (cond
               (lesson/example-trial? trial) :trial/example
               (lesson/phrase-trial? trial)  :trial/phrase
               :else                         :trial/word)
     :prompt (:prompt trial)}))


(defn input-props
  "Props for the answer field: the answer to build from tiles on an example,
   and the longest answer taken."
  [state]
  (let [lesson-state (:lesson/state state)]
    {:correct-answer (when (lesson/example-trial? (lesson/current-trial lesson-state))
                       (lesson/expected-answer lesson-state))
     :max-length     lesson/max-answer-length}))


(defn progress-props
  [state]
  (lesson/progress state))


(defn answer-hint-props
  "Props for the hint card over the opened answer word, or nil when closed: the word, its translation and its
   `:word-state` (`domain.vocabulary/translation-state`). A word already stored with this translation
   auto-dismisses the card."
  [state]
  (when-let [{:keys [dictionary-form translation word-index]}
             (when-let [open-index (:lesson/open-hint-index state)]
               (->> (lesson/answer-segments (lesson/current-trial (:lesson/state state)))
                    (filter #(= open-index (:word-index %)))
                    first))]
    (let [word-state (get (:lesson/answer-hints state) word-index)]
      {:word         dictionary-form
       :translation  translation
       :word-index   word-index
       :data-dismiss (when (= :word-state/known-with-translation word-state) "900")
       :word-state   word-state})))


(defn footer-props
  [state]
  (let [lesson-state (:lesson/state state)]
    (when-let [result (lesson/last-result lesson-state)]
      (let [trial (lesson/current-trial lesson-state)]
        {:variant        (if (:correct? result) :success :error)
         :correct-answer (lesson/expected-answer lesson-state)
         :correct-answer-segments (when (lesson/example-trial? trial)
                                    (mapv (fn [{:keys [type word-index] :as segment}]
                                            (assoc segment
                                                   :annotated? (= :annotated-word type)
                                                   :expanded?  (= word-index (:lesson/open-hint-index state))))
                                          (lesson/answer-segments trial)))
         :user-answer    (when-not (str/blank? (:answer result))
                           (:answer result))
         :finished?      (lesson/finished? lesson-state)}))))

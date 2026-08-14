(ns pages.lesson.presenter
  (:require
   [domain.lesson :as domain]))


(defn challenge-props
  [state]
  (let [trial (domain/current-trial state)]
    {:prompt      (:prompt trial)
     :instruction (cond
                    (domain/example-trial? trial) "Переведите предложение на немецкий"
                    (domain/phrase-trial? trial)  "Переведите фразу на немецкий"
                    :else                         "Переведите слово на немецкий")}))


(defn progress-props
  [state]
  (domain/progress state))


(defn input-props
  [state]
  {:prefill (:lesson/retry-answer state)})


(defn- open-hint-segment
  [state]
  (when-let [open-index (:lesson/open-hint-index state)]
    (->> (domain/answer-segments (domain/current-trial (:lesson/state state)))
         (filter #(= open-index (:word-index %)))
         first)))


(defn answer-hint-props
  "Props for the hint card over the opened answer word, or nil when closed.
   The card names the word, its translation, and — depending on the word's
   vocabulary state — either a status note or an add action. A word already
   stored with this translation auto-dismisses the card."
  [state]
  (when-let [{:keys [dictionary-form translation word-index]} (open-hint-segment state)]
    (let [word-state (get (:lesson/answer-hints state) word-index)]
      {:word         dictionary-form
       :translation  translation
       :word-index   word-index
       :data-dismiss (when (= :known-with-translation word-state) "900")
       :status-note  (when (= :known-with-translation word-state) "✓ В словаре")
       :action-label (case word-state
                       :known-missing-translation "+ ДОБАВИТЬ ПЕРЕВОД"
                       :unknown-word "+ В СЛОВАРЬ"
                       nil)})))


(defn- answer-segments
  [trial open-hint-index]
  (mapv (fn [{:keys [word-index] :as segment}]
          (assoc segment :expanded? (= word-index open-hint-index)))
        (domain/answer-segments trial)))


(defn footer-props
  [state]
  (let [lesson-state (:lesson/state state)]
    (when-let [result (domain/last-result lesson-state)]
      (let [trial (domain/current-trial lesson-state)]
        (cond-> {:variant        (if (:correct? result) :success :error)
                 :correct-answer (domain/expected-answer lesson-state)
                 :correct-answer-segments (when (domain/example-trial? trial)
                                            (answer-segments
                                             trial
                                             (:lesson/open-hint-index state)))
                 :user-answer    (:answer result)
                 :finished?      (domain/finished? lesson-state)}
          (and (not (:correct? result)) (domain/phrase-trial? trial))
          (assoc :retry? true :retry-answer (:answer result)))))))

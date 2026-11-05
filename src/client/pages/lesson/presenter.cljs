(ns pages.lesson.presenter
  (:require
   [clojure.string :as str]
   [domain.lesson :as lesson]))


(defn- trial-challenge
  [trial]
  {:kind   (cond
             (lesson/example-trial? trial) :trial/example
             (lesson/phrase-trial? trial)  :trial/phrase
             :else                         :trial/word)
   :prompt (:prompt trial)})


(defn paging-props
  "Props for paging the trials: the `:page` shown, the pages either side of it and the `:current-page`; whether
   the first trial is shown, and whether the current one is; whether the current trial is answered (`:answered?`)
   and the lesson over (`:finished?`)."
  [state]
  (let [lesson-state (:lesson/state state)
        viewed       (:lesson/viewed-trial state)
        current-page (count (lesson/past-trials lesson-state))
        page         (or viewed current-page)]
    {:answered?      (some? (lesson/last-result lesson-state))
     :current-page   current-page
     :current-shown? (nil? viewed)
     :finished?      (lesson/finished? lesson-state)
     :first-shown?   (= 0 page)
     :next-page      (inc page)
     :page           page
     :previous-page  (dec page)}))


(defn input-props
  "Props for the answer field: the answer to build from tiles on an example, and the longest answer taken;
   whether the current trial is shown."
  [state]
  (let [lesson-state (:lesson/state state)]
    {:correct-answer (when (lesson/example-trial? (lesson/current-trial lesson-state))
                       (lesson/expected-answer lesson-state))
     :current-shown? (nil? (:lesson/viewed-trial state))
     :max-length     lesson/max-answer-length}))


(defn progress-props
  "The progress of the trial shown: the past one viewed, otherwise the current one."
  [state]
  (if-let [viewed (:lesson/viewed-trial state)]
    (-> state :lesson/state (lesson/past-progress viewed))
    (-> state :lesson/state lesson/progress)))


(defn answer-hint-props
  "Props for the hint card over the opened answer word, or nil when closed: the word, its translation and its
   `:word-state` (`domain.vocabulary/translation-state`), known for the current trial only. A word already
   stored with this translation auto-dismisses the card."
  [state]
  (when-let [{:keys [dictionary-form translation word-index]}
             (when-let [open-index (:lesson/open-hint-index state)]
               ;; The answer on the panel: the past trial's viewed, otherwise the current one's.
               (->> (lesson/answer-segments (if-let [viewed (:lesson/viewed-trial state)]
                                              (-> state :lesson/state lesson/past-trials (get viewed) :trial)
                                              (-> state :lesson/state lesson/current-trial)))
                    (filter #(= open-index (:word-index %)))
                    first))]
    (let [word-state (when-not (:lesson/viewed-trial state)
                       (get (:lesson/answer-hints state) word-index))]
      {:word         dictionary-form
       :translation  translation
       :word-index   word-index
       :data-dismiss (when (= :word-state/known-with-translation word-state) "900")
       :word-state   word-state})))


(defn- answer-props
  "The correct answer of `trial`, and for an example its words, the hinted ones tappable."
  [state trial]
  {:correct-answer (:answer trial)
   :correct-answer-segments (when (lesson/example-trial? trial)
                              (for [{:keys [type word-index] :as segment} (lesson/answer-segments trial)]
                                (assoc segment
                                       :annotated? (= :annotated-word type)
                                       :expanded?  (= word-index (:lesson/open-hint-index state)))))})


(defn footer-props
  [state]
  (let [lesson-state (:lesson/state state)]
    (when-let [{:keys [answer correct?]} (lesson/last-result lesson-state)]
      (merge (answer-props state (lesson/current-trial lesson-state))
             {:variant        (if correct? :success :error)
              :user-answer    (when-not (str/blank? answer) answer)
              :current-shown? (nil? (:lesson/viewed-trial state))
              :finished?      (lesson/finished? lesson-state)}))))


(defn strip-props
  "Props for the strip of trials: its pages, the trials answered then the current one, each a challenge with
   `:page`, `:current?` and `:shown?`; the `:page` shown and the `:current-page`; whether the current trial is
   shown; whether the lesson is over (`:finished?`), its panels then offering the way home; and the answer of the
   past one viewed."
  [state]
  (let [lesson-state (:lesson/state state)
        past (lesson/past-trials lesson-state)
        {:keys [current-page current-shown? finished? page]} (paging-props state)]
    {:current-page   current-page
     :current-shown? current-shown?
     :finished?      finished?
     :page           page
     :pages          (->> (conj (mapv :trial past) (lesson/current-trial lesson-state))
                          (map-indexed (fn [index trial]
                                         (assoc (trial-challenge trial)
                                                :current? (= index current-page)
                                                :page     index
                                                :shown?   (= index page)))))
     :viewed-answer  (when-let [{:keys [attempts trial]} (get past (:lesson/viewed-trial state))]
                       (assoc (answer-props state trial)
                              :attempts   attempts
                              :first-try? (= 1 attempts)))}))

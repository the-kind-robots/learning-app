(ns use-cases.lesson
  (:require
   [domain.lesson :as domain]
   [lambdaisland.glogi :as log]
   [use-cases.vocabulary :as vocabulary]))


(defn- state
  [progress-store]
  ((:progress-store/get-lesson progress-store)))


(def max-answer-length
  1000)


(defn- clamp-answer
  [answer]
  (let [answer (or answer "")]
    (if (> (count answer) max-answer-length)
      (subs answer 0 max-answer-length)
      answer)))


(defn- lesson-learnable
  [{id :_id kind :kind value :value translation :translation}]
  {:id          id
   :kind        kind
   :translation translation
   :value       value})


(defn ^:async start!
  "Start a new lesson. Returns {:lesson-state ...} or {:error ...}.

   opts:
     :learnables-per-lesson — how many words and phrases to include (default 3)
     :trial-selector    — strategy for picking the next trial (:first or :random, default nil → random)"
  [{:keys [collections examples progress-store] :as capabilities}
   {:keys [learnables-per-lesson trial-selector]
    :or   {learnables-per-lesson domain/default-learnables-per-lesson}}]
  (try
    (let [{selected :words} (await (vocabulary/list-active
                                    capabilities
                                    {:order :asc :limit learnables-per-lesson}))]
      (if-not (seq selected)
        {:error :no-words-available}
        (let [collection-id   ((:collections/active-id collections))
              learnables      (mapv lesson-learnable selected)
              word-ids        (mapv :id learnables)
              lesson-examples (await ((:examples/list examples) word-ids collection-id))
              lesson-state    (domain/initial-state learnables lesson-examples trial-selector)
              {:keys [rev]}   (await ((:progress-store/save-lesson! progress-store) lesson-state))]
          {:lesson-state (assoc lesson-state :_rev rev)})))
    (catch js/Error err
      (log/error :lesson/start-failed {:error (ex-message err)})
      {:error :lesson-start-failed})))


(defn ^:async finish!
  [{:keys [progress-store]}]
  (await ((:progress-store/remove-lesson! progress-store))))


(defn ^:async restart!
  "Always starts a fresh lesson session by removing any persisted lesson first."
  [capabilities]
  (await (finish! capabilities))
  (await (start! capabilities {})))


(defn ^:async check-answer!
  "Check the user's answer. Returns {:lesson-state ...}."
  [{:keys [progress-store] :as capabilities} answer]
  (let [current-state (await (state progress-store))
        answer        (clamp-answer answer)]
    (if-not current-state
      (do
        (log/warn :lesson/check-answer-missing {:answer answer})
        {:error :lesson-not-found :lesson-state nil})
      (let [current-trial  (domain/current-trial current-state)
            checked        (domain/check-answer current-state answer)
            phrase-review? (domain/phrase-review-due? current-state current-trial)
            lesson-state   (cond-> checked
                             phrase-review? (domain/mark-trial-reviewed current-trial))]
        (try
          (when (or (domain/word-trial? current-trial) phrase-review?)
            (await (vocabulary/add-review
                    capabilities
                    (:word-id current-trial)
                    (-> lesson-state domain/last-result :correct?)
                    (:prompt current-trial))))
          (let [{:keys [rev]} (await ((:progress-store/save-lesson! progress-store) lesson-state))]
            {:lesson-state (assoc lesson-state :_rev rev)})
          (catch js/Error err
            (log/error :lesson/check-answer-save-failed {:error (ex-message err)})
            {:error :lesson-save-failed :lesson-state lesson-state}))))))


(defn ^:async advance!
  "Select the next trial. Returns {:lesson-state ...} or {:error ...}."
  [{:keys [progress-store]}]
  (let [lesson-state (await (state progress-store))]
    (if-not lesson-state
      (do
        (log/warn :advance-lesson/missing-state {})
        {:error :lesson-not-found})
      (when-let [next-state (domain/advance lesson-state)]
        (try
          (let [{:keys [rev]} (await ((:progress-store/save-lesson! progress-store) next-state))]
            {:lesson-state (assoc next-state :_rev rev)})
          (catch js/Error err
            (log/error :advance-lesson/save-failed {:error (ex-message err)})
            {:error :lesson-save-failed}))))))


(defn- token-state
  [existing translation]
  (cond
    (nil? existing)
    :unknown-word

    (some #(= translation (:value %)) (:translation existing))
    :known-with-translation

    :else
    :known-missing-translation))


(defn ^:async token-info
  "Return token info for lesson answer annotation card."
  [capabilities dictionary-form translation]
  (let [existing (await (vocabulary/find-duplicate capabilities dictionary-form))]
    {:dictionary-form dictionary-form
     :translation translation
     :state       (token-state existing translation)}))


(defn ^:async answer-annotations
  "Vocabulary state per annotated word of the trial's answer:
   {word-index :unknown-word | :known-missing-translation | :known-with-translation}."
  [capabilities trial]
  (let [segments (filterv #(= :annotated-word (:type %))
                          (domain/answer-segments trial))
        infos    (await (js/Promise.all
                         (into-array
                          (map #(token-info capabilities (:dictionary-form %) (:translation %))
                               segments))))]
    (zipmap (map :word-index segments)
            (map :state infos))))

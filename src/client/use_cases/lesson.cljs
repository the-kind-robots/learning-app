(ns use-cases.lesson
  (:require
   [domain.lesson :as domain]
   [lambdaisland.glogi :as log]
   [use-cases.collections :as collections]
   [use-cases.examples :as examples]
   [use-cases.vocabulary :as vocabulary]))


(def max-answer-length
  1000)


(defn- clamp-answer
  [answer]
  (let [answer (or answer "")]
    (if (> (count answer) max-answer-length)
      (subs answer 0 max-answer-length)
      answer)))


(defn- lesson-vocab
  [{:keys [id kind value translation]}]
  {:id          id
   :kind        kind
   :translation translation
   :value       value})


(defn start
  "A new lesson out of the learner's data in memory. Returns {:lesson-state
   ...} or {:error :no-words-available}. Pure but for the draw: the words are
   drawn at random from the most due (`domain/pick-vocab`).

   opts:
     :vocab-per-lesson  — how many words and phrases to include (default 3)
     :vocab-pool-size   — how many of the most due to draw them from (default 20)
     :trial-selector    — strategy for picking the next trial (:first or :random, default nil → random)"
  [memory collection-id
   {:keys [vocab-per-lesson vocab-pool-size trial-selector]
    :or   {vocab-per-lesson domain/default-vocab-per-lesson
           vocab-pool-size  domain/default-vocab-pool-size}}
   now-ms]
  ;; Every word in scope, unranked: `pick-vocab` owns the whole selection
  ;; policy, ties included.
  (let [words    (vocabulary/scope memory (collections/active-scope memory collection-id))
        selected (domain/pick-vocab words
                                    #(vocabulary/urgency-of now-ms %)
                                    vocab-pool-size
                                    vocab-per-lesson)]
    (if-not (seq selected)
      {:error :no-words-available}
      (let [vocab    (mapv lesson-vocab selected)
            word-ids (set (map :id vocab))]
        ;; Which examples a read in this collection sees is one rule, and it
        ;; lives in `use-cases.examples`.
        {:lesson-state (domain/initial-state
                        vocab
                        (-> (into []
                                  (comp (mapcat #(get-in memory [:examples-by-word %]))
                                        (map (:examples memory)))
                                  word-ids)
                            (examples/visible-in collection-id))
                        trial-selector)}))))


(defn ^:async begin!
  "Stores the lesson just started, over whichever one is stored. Run after
   the screen is painted: the lesson on screen is the one in app state, and
   the document is what answers are written against."
  [{:keys [lessons]} lesson-state]
  (await ((:lessons/save! lessons) lesson-state)))


(defn ^:async finish!
  [{:keys [lessons]}]
  (await ((:lessons/remove! lessons))))


(defn ^:async check-answer!
  "Check the user's answer against `current-state`, the lesson on screen.
   Returns {:lesson-state ...}."
  [capabilities current-state answer]
  (let [answer (clamp-answer answer)]
    (if-not current-state
      (do
        (log/warn :lesson/check-answer-missing {:answer answer})
        {:error :lesson-not-found :lesson-state nil})
      (let [current-trial (domain/current-trial current-state)
            lesson-state  (domain/check-answer current-state answer)]
        (try
          ;; Every graded attempt is written, however many a lesson holds: a
          ;; run of lapses is the log being honest, not noise. Example trials
          ;; keep writing nothing — they grade a word already reviewed here.
          (when-not (domain/example-trial? current-trial)
            (await (vocabulary/add-review
                    capabilities
                    (:word-id current-trial)
                    (-> lesson-state domain/last-result :correct?)
                    (:prompt current-trial))))
          (await ((:lessons/save! (:lessons capabilities)) lesson-state))
          {:lesson-state lesson-state}
          (catch js/Error err
            (log/error :lesson/check-answer-save-failed {:error (ex-message err)})
            {:error :lesson-save-failed :lesson-state lesson-state}))))))


(defn ^:async advance!
  "Select the trial after `lesson-state`, the lesson on screen. Returns
   {:lesson-state ...} or {:error ...}."
  [{:keys [lessons]} lesson-state]
  (if-not lesson-state
    (do
      (log/warn :advance-lesson/missing-state {})
      {:error :lesson-not-found})
    (when-let [next-state (domain/advance lesson-state)]
      (try
        (await ((:lessons/save! lessons) next-state))
        {:lesson-state next-state}
        (catch js/Error err
          (log/error :advance-lesson/save-failed {:error (ex-message err)})
          {:error :lesson-save-failed})))))


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

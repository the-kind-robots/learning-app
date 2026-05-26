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


(defn- lesson-word
  [{id :_id value :value translation :translation}]
  {:id          id
   :value       value
   :translation translation})


(defn ^:async start!
  "Start a new lesson. Returns {:lesson-state ...} or {:error ...}.

   opts:
     :words-per-lesson  — how many vocabulary words to include (default 3)
     :trial-selector    — strategy for picking the next trial (:first or :random, default nil → random)"
  [{:keys [collections examples progress-store] :as capabilities}
   {:keys [words-per-lesson trial-selector]
    :or   {words-per-lesson domain/default-words-per-lesson}}]
  (try
    (let [{selected-words :words} (await (vocabulary/list-active
                                          capabilities
                                          {:order :asc :limit words-per-lesson}))]
      (if-not (seq selected-words)
        {:error :no-words-available}
        (let [collection-id   ((:collections/active-id collections))
              lesson-words    (mapv lesson-word selected-words)
              word-ids        (mapv :id lesson-words)
              lesson-examples (await ((:examples/list examples) word-ids collection-id))
              lesson-state    (domain/initial-state lesson-words lesson-examples trial-selector)
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
      (let [current-trial (domain/current-trial current-state)
            lesson-state  (domain/check-answer current-state answer)]
        (try
          (when (domain/word-trial? current-trial)
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
  (let [existing (await (vocabulary/find-duplicate-by-value capabilities dictionary-form))]
    {:dictionary-form dictionary-form
     :translation translation
     :state       (token-state existing translation)}))


(defn ^:async add-word-from-structure!
  "Add dictionary form + translation from lesson example structure into vocabulary."
  [{:keys [collections examples] :as capabilities} dictionary-form translation]
  (let [result      (await (vocabulary/add! capabilities dictionary-form translation))
        active-id   ((:collections/active-id collections))
        active-name (when active-id
                      (:name (await ((:collections/get collections) active-id))))]
    (when (:created? result)
      ((:examples/request! examples)
       (:word-id result)
       active-id
       active-name))
    result))

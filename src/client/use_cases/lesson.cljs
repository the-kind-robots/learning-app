(ns use-cases.lesson
  (:require
   [dbs :as dbs]
   [domain.lesson :as domain]
   [lambdaisland.glogi :as log]
   [repo.examples :as examples]
   [use-cases.vocabulary :as vocabulary]
   [utils :as utils]))


(defn- state
  [dbs]
  (dbs/get dbs "lesson" domain/lesson-id))


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
  "Start a new lesson. Returns {:lesson-state ...} or {:error ...}."
  ([dbs]
   (start! dbs {}))
  ([dbs
    {:keys [words-per-lesson trial-selector]
     :or   {words-per-lesson domain/default-words-per-lesson}}]
   (try
     (let [{selected-words :words} (await (vocabulary/list dbs {:order :asc :limit words-per-lesson}))]
       (if-not (seq selected-words)
         {:error :no-words-available}
         (let [lesson-words    (mapv lesson-word selected-words)
               word-ids        (mapv :id lesson-words)
               lesson-examples (await (examples/list dbs word-ids))
               lesson-state    (domain/initial-state lesson-words lesson-examples trial-selector (utils/now-iso))
               {:keys [rev]}   (await (dbs/insert dbs lesson-state))]
           {:lesson-state (assoc lesson-state :_rev rev)})))
     (catch js/Error err
       (log/error :lesson/start-failed {:error (ex-message err)})
       {:error :lesson-start-failed}))))


(defn ^:async finish!
  [dbs]
  (let [existing (await (state dbs))]
    (when existing
      (await (dbs/remove dbs existing)))))


(defn ^:async restart!
  "Always starts a fresh lesson session by removing any persisted lesson first."
  ([dbs]
   (restart! dbs {}))
  ([dbs opts]
   (await (finish! dbs))
   (await (start! dbs opts))))


(defn ^:async check-answer!
  "Check the user's answer. Returns {:lesson-state ...}."
  [dbs answer]
  (let [current-state (await (state dbs))
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
                    dbs
                    (:word-id current-trial)
                    (-> lesson-state domain/last-result :correct?)
                    (:prompt current-trial))))
          (let [{:keys [rev]} (await (dbs/insert dbs lesson-state))]
            {:lesson-state (assoc lesson-state :_rev rev)})
          (catch js/Error err
            (log/error :lesson/check-answer-save-failed {:error (ex-message err)})
            {:error :lesson-save-failed :lesson-state lesson-state}))))))


(defn ^:async advance!
  "Select the next trial. Returns {:lesson-state ...} or {:error ...}."
  [dbs]
  (let [lesson-state (await (state dbs))]
    (if-not lesson-state
      (do
        (log/warn :advance-lesson/missing-state {})
        {:error :lesson-not-found})
      (when-let [next-state (domain/advance lesson-state)]
        (try
          (let [{:keys [rev]} (await (dbs/insert dbs next-state))]
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
  [dbs dictionary-form translation]
  (let [existing (await (vocabulary/find-duplicate-by-value dbs dictionary-form))]
    {:dictionary-form dictionary-form
     :translation translation
     :state       (token-state existing translation)}))


(defn ^:async add-word-from-structure!
  "Add dictionary form + translation from lesson example structure into vocabulary."
  [dbs dictionary-form translation]
  (let [result (await (vocabulary/add! dbs dictionary-form translation))]
    (when (:created? result)
      (examples/create-fetch-task! (:word-id result)))
    result))

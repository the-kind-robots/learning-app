(ns lesson
  (:require
   [dbs :as dbs]
   [domain.lesson :as domain]
   [examples :as examples]
   [lambdaisland.glogi :as log]
   [promesa.core :as p]
   [utils :as utils]
   [vocabulary :as vocabulary]))


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
  [{:keys [_id value translation]}]
  {:id          _id
   :value       value
   :translation translation})


(defn start!
  "Start a new lesson. Fetches words and examples, creates and persists lesson state.
   Returns {:lesson-state ...} or {:error ...}."
  ([dbs]
   (start! dbs {}))
  ([dbs
    {:keys [words-per-lesson trial-selector]
     :or   {words-per-lesson domain/default-words-per-lesson}}]
   (p/catch
     (p/let [{selected-words :words} (vocabulary/list dbs {:order :asc :limit words-per-lesson})]
       (if-not (seq selected-words)
         {:error :no-words-available}
         (p/let [lesson-words  (mapv lesson-word selected-words)
                 word-ids      (mapv :id lesson-words)
                 examples      (examples/list dbs word-ids)
                 lesson-state  (domain/initial-state lesson-words examples trial-selector (utils/now-iso))
                 {:keys [rev]} (dbs/insert dbs lesson-state)]
           {:lesson-state (assoc lesson-state :_rev rev)})))
     (fn [err]
       (log/error :lesson/start-failed {:error (ex-message err)})
       {:error :lesson-start-failed}))))


(defn finish!
  [dbs]
  (p/let [existing (state dbs)]
    (when existing
      (dbs/remove dbs existing))))


(defn restart!
  "Always starts a fresh lesson session by removing any persisted lesson first.
   Returns {:lesson-state ...} or {:error ...}."
  ([dbs]
   (restart! dbs {}))
  ([dbs opts]
   (p/do
     (finish! dbs)
     (start! dbs opts))))


(defn check-answer!
  "Check the user's answer for the current trial.
   Returns {:lesson-state ...}. On error returns {:error :keyword :lesson-state ...}."
  [dbs answer]
  (p/let [current-state (state dbs)
          answer        (clamp-answer answer)]
    (if-not current-state
      (do
        (log/warn :lesson/check-answer-missing {:answer answer})
        {:error        :lesson-not-found
         :lesson-state nil})
      (let [current-trial (domain/current-trial current-state)
            lesson-state  (domain/check-answer current-state answer)]
        (p/catch
          (p/do
            (when (domain/word-trial? current-trial)
              (vocabulary/add-review
               dbs
               (:word-id current-trial)
               (-> lesson-state domain/last-result :correct?)
               (:prompt current-trial)))
            (p/let [{:keys [rev]} (dbs/insert dbs lesson-state)]
              {:lesson-state (assoc lesson-state :_rev rev)}))
          (fn [err]
            (log/error :lesson/check-answer-save-failed {:error (ex-message err)})
            {:error        :lesson-save-failed
             :lesson-state lesson-state}))))))


(defn advance!
  "Select the next trial from remaining trials.
   Returns {:lesson-state ...}, {:error ...}, or nil if finished."
  [dbs]
  (p/let [lesson-state (state dbs)]
    (if-not lesson-state
      (do
        (log/warn :advance-lesson/missing-state {})
        {:error :lesson-not-found})
      (when-let [next-state (domain/advance lesson-state)]
        (p/catch
          (p/let [{:keys [rev]} (dbs/insert dbs next-state)]
            {:lesson-state (assoc next-state :_rev rev)})
          (fn [err]
            (log/error :advance-lesson/save-failed {:error (ex-message err)})
            {:error :lesson-save-failed}))))))


(defn token-info
  "Return token info for lesson answer annotation card."
  [dbs dictionary-form translation]
  (p/let [existing (vocabulary/find-duplicate-by-value dbs dictionary-form)]
    {:dictionary-form dictionary-form
     :translation     translation
     :known?          (some? existing)}))


(defn add-word-from-structure!
  "Add dictionary form from lesson example structure into vocabulary."
  [dbs dictionary-form translation]
  (p/let [result (vocabulary/add! dbs dictionary-form translation)]
    (when (:created? result)
      (examples/create-fetch-task! (:word-id result)))
    result))

(ns pages.lesson.effects
  (:require
   [domain.lesson :as domain]
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [pages.lesson.popover :as popover]
   [use-cases.lesson :as lesson]
   [use-cases.vocabulary :as vocabulary]))


(nxr/register-action! :action/go-to-lesson
  (fn go-to-lesson [_]
    [[:effect/navigate :page/lesson]]))


(nxr/register-effect! :effect/load-lesson
  (fn ^:async load-lesson
    [{:keys [capabilities dispatch]} _]
    (try
      (let [{:keys [lesson-state error]} (await (lesson/restart! capabilities))]
        (dispatch [[:action/show-lesson {:lesson-state lesson-state :error error}]]))
      (catch js/Error err
        (log/error :effect/load-lesson {:error (str err)})))))


(nxr/register-effect! :effect/check-answer
  (fn ^:async check-answer
    [{:keys [capabilities dispatch]} _ answer]
    (try
      (let [{:keys [lesson-state]} (await (lesson/check-answer! capabilities answer))]
        (when lesson-state
          (dispatch [[:action/update-lesson lesson-state]])
          ;; The revealed answer carries hints for its annotated words; their
          ;; vocabulary states are looked up once here, not on every click.
          (let [trial (domain/current-trial lesson-state)]
            (when (domain/example-trial? trial)
              (let [hints (await (lesson/answer-annotations capabilities trial))]
                (dispatch [[:action/annotate-answer hints]]))))))
      (catch js/Error err
        (log/error :effect/check-answer {:error (str err)})))))


(nxr/register-effect! :effect/next-trial
  (fn ^:async next-trial
    [{:keys [capabilities dispatch]} _]
    (try
      (let [{:keys [lesson-state]} (await (lesson/advance! capabilities))]
        (when lesson-state
          (dispatch [[:action/update-lesson lesson-state]])))
      (catch js/Error err
        (log/error :effect/next-trial {:error (str err)})))))


(nxr/register-effect! :effect/end-lesson
  (fn ^:async end-lesson
    [{:keys [capabilities]} _]
    (try
      (await (lesson/finish! capabilities))
      (when-let [replace (get-in capabilities [:navigation :navigation/replace])]
        (replace :page/home))
      (catch js/Error err
        (log/error :effect/end-lesson {:error (str err)})))))


(nxr/register-effect! :effect/show-token-popover
  (fn show-token-popover
    [{:keys [dispatch dispatch-data]} _]
    (popover/open! (:replicant/node dispatch-data)
                   #(dispatch [[:action/close-answer-hint]]))))


(nxr/register-effect! :effect/reposition-token-popover
  (fn reposition-token-popover
    [_ _]
    (popover/reposition!)))


(nxr/register-effect! :effect/add-token
  (fn ^:async add-token
    [{:keys [capabilities dispatch]} system {:keys [dictionary-form translation word-index]}]
    (try
      (await (vocabulary/add! capabilities dictionary-form translation))
      (let [hints (:lesson/answer-hints (deref (:store system)))]
        (dispatch [[:action/annotate-answer
                    (assoc hints word-index :known-with-translation)]]))
      (catch js/Error err
        (log/error :effect/add-token {:error (str err)})))))

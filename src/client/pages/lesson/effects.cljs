(ns pages.lesson.effects
  (:require
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [pages.lesson.popover :as popover]
   [use-cases.lesson :as lesson]
   [use-cases.vocabulary :as vocabulary]))


(nxr/register-action! :action/go-to-lesson
  (fn go-to-lesson [_]
    [[:effect/navigate :page/lesson]]))


(nxr/register-effect! :effect/check-answer
  ;; The answer is graded and shown at once, an example's word hints with it; a word's review is written after.
  (fn ^:async check-answer
    [{{:keys [learner world]} :capabilities :keys [dispatch]} _ lesson-state answer]
    (try
      (when-let [{checked :lesson-state :keys [answer-hints review]}
                 (lesson/check-answer! learner world lesson-state answer)]
        (dispatch [[:action/update-lesson lesson-state checked]])
        (when answer-hints
          (dispatch [[:action/annotate-answer checked answer-hints]]))
        (await review))
      (catch js/Error err
        (log/error :effect/check-answer {:error (str err)})))))


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
    [{{:keys [learner world]} :capabilities :keys [dispatch]} _
     {:keys [dictionary-form hints lesson-state translation word-index]}]
    (try
      (await (vocabulary/add! learner world {:translation translation :value dictionary-form}))
      ;; The saved hint map re-renders the open card; the popover's on-update then repositions the shell.
      (dispatch [[:action/annotate-answer
                  lesson-state
                  (assoc hints word-index :word-state/known-with-translation)]])
      (catch js/Error err
        (log/error :effect/add-token {:error (str err)})))))

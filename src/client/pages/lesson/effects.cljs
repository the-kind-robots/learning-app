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


(nxr/register-effect! :effect/record-review
  (fn ^:async record-review
    [{:keys [capabilities]} _ lesson-state]
    (try
      (await (lesson/record-review! capabilities lesson-state))
      (catch js/Error err
        (log/error :effect/record-review {:error (str err)})))))


(nxr/register-effect! :effect/look-up-answer-annotations
  ;; The revealed answer carries hints for its annotated words; their
  ;; vocabulary states are looked up once here, not on every click.
  (fn look-up-answer-annotations
    [{:keys [capabilities dispatch]} _ lesson-state]
    (try
      (let [hints (lesson/answer-annotations capabilities (domain/current-trial lesson-state))]
        (dispatch [[:action/annotate-answer lesson-state hints]]))
      (catch js/Error err
        (log/error :effect/look-up-answer-annotations {:error (str err)})))))


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
    [{:keys [capabilities dispatch]} _ {:keys [dictionary-form hints lesson-state translation word-index]}]
    (try
      (await (vocabulary/add! capabilities dictionary-form translation :word nil))
      ;; The saved hint map re-renders the open card to its added state;
      ;; the popover's on-update then repositions the shell.
      (dispatch [[:action/annotate-answer
                  lesson-state
                  (assoc hints word-index :word-state/known-with-translation)]])
      (catch js/Error err
        (log/error :effect/add-token {:error (str err)})))))

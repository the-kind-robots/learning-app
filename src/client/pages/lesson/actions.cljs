(ns pages.lesson.actions
  (:require
   [domain.lesson :as domain]
   [nexus.registry :as nxr]))


;; An answer is checked and its review written before the result is shown,
;; and the reader may have left the lesson, or started another, in the
;; meantime. A result is shown only over the lesson it was computed from.


(nxr/register-action! :action/update-lesson
  (fn update-lesson [state from lesson-state]
    (when (= from (:lesson/state state))
      [[:effect/save
        {:lesson/answer-hints nil
         :lesson/open-hint-index nil
         :lesson/state        lesson-state}]])))


(nxr/register-action! :action/annotate-answer
  (fn annotate-answer [state lesson-state hints]
    (when (= lesson-state (:lesson/state state))
      [[:effect/save {:lesson/answer-hints hints}]])))


(nxr/register-action! :action/check-answer
  (fn check-answer [state answer]
    [[:effect/check-answer (:lesson/state state) answer]]))


(nxr/register-action! :action/next-trial
  (fn next-trial [state]
    (let [from (:lesson/state state)]
      (when-let [lesson-state (some-> from domain/advance)]
        [[:action/update-lesson from lesson-state]]))))


(nxr/register-action! :action/open-answer-hint
  (fn open-answer-hint [_ word-index]
    [[:effect/save {:lesson/open-hint-index word-index}]
     [:effect/show-token-popover]]))


(nxr/register-action! :action/close-answer-hint
  (fn close-answer-hint [_]
    [[:effect/save {:lesson/open-hint-index nil}]]))


(nxr/register-action! :action/reposition-token-popover
  (fn reposition-token-popover [_]
    [[:effect/reposition-token-popover]]))


(nxr/register-action! :action/save-lesson-word
  (fn save-lesson-word [state payload]
    [[:effect/add-token
      (assoc payload
             :hints        (:lesson/answer-hints state)
             :lesson-state (:lesson/state state))]]))


(nxr/register-action! :action/focus-lesson-input
  (fn focus-lesson-input [_]
    [[:effect/mobile-autofocus "lesson-answer"]]))


(nxr/register-action! :action/focus-continue-button
  (fn focus-continue-button [_ selector]
    [[:effect/focus-child selector]]))

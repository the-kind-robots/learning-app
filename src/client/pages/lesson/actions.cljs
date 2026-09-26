(ns pages.lesson.actions
  (:require
   [nexus.registry :as nxr]))


(nxr/register-action! :action/update-lesson
  (fn update-lesson [_ lesson-state]
    [[:effect/save
      {:lesson/state        lesson-state
       :lesson/answer-hints nil
       :lesson/open-hint-index nil}]]))


(nxr/register-action! :action/annotate-answer
  (fn annotate-answer [_ hints]
    [[:effect/save {:lesson/answer-hints hints}]]))


(nxr/register-action! :action/check-answer
  (fn check-answer [state answer]
    [[:effect/check-answer (:lesson/state state) answer]]))


(nxr/register-action! :action/next-trial
  (fn next-trial [state]
    [[:effect/next-trial (:lesson/state state)]]))


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
    [[:effect/add-token (assoc payload :hints (:lesson/answer-hints state))]]))


(nxr/register-action! :action/focus-lesson-input
  (fn focus-lesson-input [_]
    [[:effect/mobile-autofocus "lesson-answer"]]))


(nxr/register-action! :action/focus-continue-button
  (fn focus-continue-button [_ selector]
    [[:effect/focus-child selector]]))

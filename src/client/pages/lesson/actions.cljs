(ns pages.lesson.actions
  (:require
   [nexus.registry :as nxr]))


(nxr/register-action! :action/open-lesson
  ;; Entering the route: nothing of the last lesson until this one is read.
  (fn open-lesson [_]
    [[:effect/save
      {:lesson/answer-hints    nil
       :lesson/empty?          false
       :lesson/loading?        true
       :lesson/open-hint-index nil
       :lesson/state           nil}]]))


(nxr/register-action! :action/show-lesson
  (fn show-lesson [_ {:keys [lesson-state error]}]
    [[:effect/save
      {:lesson/empty?   (boolean error)
       :lesson/loading? false
       :lesson/state    (when-not error lesson-state)}]]))


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
  (fn check-answer [_ answer]
    [[:effect/check-answer answer]]))


(nxr/register-action! :action/next-trial
  (fn next-trial [_]
    [[:effect/next-trial]]))


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

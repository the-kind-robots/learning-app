(ns pages.lesson.actions
  (:require
   [nexus.registry :as nxr]))


(nxr/register-action! :action/show-lesson
  (fn show-lesson [_ {:keys [lesson-state error]}]
    [[:effect/save
      {:page/current  :page/lesson
       :page/load     nil
       :lesson/empty? (boolean error)
       :lesson/state  (when-not error lesson-state)}]]))


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


(def ^:private end-lesson-handler (fn [_] [[:effect/end-lesson]]))


(nxr/register-action! :action/cancel-lesson end-lesson-handler)


(nxr/register-action! :action/finish-lesson end-lesson-handler)


(nxr/register-action! :action/open-answer-hint
  (fn open-answer-hint [_ word-index]
    [[:effect/save {:lesson/open-hint-index word-index}]]))


(nxr/register-action! :action/close-answer-hint
  (fn close-answer-hint [_]
    [[:effect/save {:lesson/open-hint-index nil}]]))


(nxr/register-action! :action/show-token-popover
  (fn show-token-popover [_ word-index]
    [[:effect/show-token-popover word-index]]))


(nxr/register-action! :action/save-lesson-word
  (fn save-lesson-word [_ payload]
    [[:effect/add-token payload]]))


(nxr/register-action! :action/focus-lesson-input
  (fn focus-lesson-input [_]
    [[:effect/mobile-autofocus "lesson-answer"]]))


(nxr/register-action! :action/focus-continue-button
  (fn focus-continue-button [_ selector]
    [[:effect/focus-child selector]]))

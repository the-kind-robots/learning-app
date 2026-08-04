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
    [[:effect/save {:lesson/state lesson-state}]]))


(nxr/register-action! :action/check-answer
  (fn check-answer [_ answer]
    [[:effect/check-answer answer]]))


(nxr/register-action! :action/next-trial
  (fn next-trial [_]
    [[:effect/next-trial]]))


(def ^:private end-lesson-handler (fn [_] [[:effect/end-lesson]]))


(nxr/register-action! :action/cancel-lesson end-lesson-handler)


(nxr/register-action! :action/finish-lesson end-lesson-handler)


(nxr/register-action! :action/open-modal
  (fn open-modal [_ data]
    [[:effect/save {:modal/type :token-info :modal/data data}]]))


(nxr/register-action! :action/close-modal
  (fn close-modal [_]
    [[:effect/save {:modal/type nil :modal/data nil}]]))


(nxr/register-action! :action/view-token-info
  (fn view-token-info [_ {:keys [dictionary-form translation]}]
    [[:effect/open-token-info {:dictionary-form dictionary-form :translation translation}]]))


(nxr/register-action! :action/save-lesson-word
  (fn save-lesson-word [_ {:keys [dictionary-form translation]}]
    [[:effect/add-token {:dictionary-form dictionary-form :translation translation}]]))


(nxr/register-action! :action/focus-lesson-input
  (fn focus-lesson-input [_]
    [[:effect/mobile-autofocus "lesson-answer"]]))


(nxr/register-action! :action/focus-continue-button
  (fn focus-continue-button [_ selector]
    [[:effect/focus-child selector]]))

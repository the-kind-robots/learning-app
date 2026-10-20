(ns pages.lesson.actions
  (:require
   [domain.lesson :as domain]
   [nexus.registry :as nxr]
   [use-cases.lesson :as lesson]))


;; Effects that finish later bring back a lesson shown only over the lesson it was computed from:
;; the reader may have left, or started another.


(defn- shown
  "The lesson screen with `lesson-state` on it, fresh: no answer revealed, no
   hint open."
  [{:keys [lesson-state error]}]
  {:lesson/answer-hints nil
   :lesson/empty?       (boolean error)
   :lesson/open-hint-index nil
   :lesson/state        lesson-state
   :page/current        :page/lesson})


(nxr/register-action! :action/open-lesson
  ;; The lesson is drawn from memory in the task of the tap and lives in app state only. Entered while
  ;; memory loads, behind the splash, it is drawn once memory is in place (`:action/refresh-page`).
  (fn open-lesson [state {:keys [active-collection learner now-ms random]}]
    [[:effect/save
      (shown (when (= :status/loaded (:learner/status state))
               (lesson/start learner active-collection {:random random} now-ms)))]]))


(nxr/register-action! :action/update-lesson
  (fn update-lesson [state from lesson-state]
    (when (= from (:lesson/state state))
      [[:effect/save
        (shown {:lesson-state lesson-state})]])))


(nxr/register-action! :action/annotate-answer
  (fn annotate-answer [state lesson-state hints]
    (when (= lesson-state (:lesson/state state))
      [[:effect/save {:lesson/answer-hints hints}]])))


(nxr/register-action! :action/check-answer
  ;; The answer is graded and shown at once; the review is written, and the word hints looked up, after.
  (fn check-answer [state answer]
    (let [from (:lesson/state state)]
      (cond
        (nil? from)
        [[:effect/log-warning :lesson/check-answer-missing {:answer answer}]]

        (nil? (domain/last-result from))
        (let [checked  (domain/check-answer from answer)
              example? (domain/example-trial? (domain/current-trial checked))]
          [[:effect/save (shown {:lesson-state checked})]
           (if example?
             [:effect/look-up-answer-annotations checked]
             [:effect/record-review checked])])))))


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

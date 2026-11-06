(ns pages.lesson.actions
  (:require
   [nexus.registry :as nxr]
   [use-cases.lesson :as lesson]))


;; Effects that finish later bring back a lesson shown only over the lesson it was computed from:
;; the reader may have left, or started another.


(defn- shown
  "The lesson screen with `lesson-state` on it, fresh: no answer revealed, no
   hint open, no answer started yet."
  [{:keys [lesson-state error]}]
  {:lesson/answer-hints   nil
   :lesson/answer-started nil
   :lesson/empty?         (boolean error)
   :lesson/open-hint-index nil
   :lesson/state          lesson-state
   :lesson/viewed-trial   nil
   :page/current          :page/lesson})


(nxr/register-action! :action/view-trial
  ;; The strip goes to `page`: a past trial, or the current one at `current-page` (viewed: nil). A hint open
  ;; over the other closes. On a past trial the main button takes the focus, so `Enter` presses it: the field of
  ;; a trial waiting for its answer, inert meanwhile, lost it.
  (fn view-trial [state page current-page]
    (let [viewed (when (< page current-page) page)]
      (when (not= viewed (:lesson/viewed-trial state))
        (cond-> [[:effect/save
                  {:lesson/open-hint-index nil
                   :lesson/viewed-trial    viewed}]]
          viewed
          (conj [:effect/focus "lesson-main-button"]))))))


(nxr/register-action! :action/open-lesson
  ;; The lesson is drawn from memory in the task of the tap and lives in app state only. Entered while
  ;; memory loads, behind the splash, it is drawn once memory is in place (`:action/refresh-page`).
  (fn open-lesson [state {:keys [active-collection language learner now-ms random]}]
    [[:effect/save
      (shown (when (= :status/loaded (:learner/status state))
               (lesson/start learner language active-collection {:random random} now-ms)))]]))


(nxr/register-action! :action/update-lesson
  (fn update-lesson [state from lesson-state]
    (when (= from (:lesson/state state))
      [[:effect/save
        (shown {:lesson-state lesson-state})]])))


(nxr/register-action! :action/annotate-answer
  (fn annotate-answer [state lesson-state hints]
    (when (= lesson-state (:lesson/state state))
      [[:effect/save {:lesson/answer-hints hints}]])))


(nxr/register-action! :action/start-answer
  ;; The trial is on screen: its time to answer counts from here.
  (fn start-answer [_ context]
    [[:effect/save {:lesson/answer-started (select-keys context [:uptime-ms :visibility-changes])}]]))


(nxr/register-action! :action/check-answer
  (fn check-answer [state answer]
    (if-let [lesson-state (:lesson/state state)]
      [[:effect/check-answer lesson-state answer (:lesson/answer-started state)]]
      [[:effect/log-warning :lesson/check-answer-missing {:answer answer}]])))


(nxr/register-action! :action/press-next
  ;; «ДАЛЕЕ», or «ЕЩЁ УРОК» once nothing is left: from a past trial back to the current one (a hint open over
  ;; it closes), otherwise on to the next trial. With no trial left, a new lesson: the route stays, so it is
  ;; drawn here, not by the route.
  (fn press-next [state]
    (let [to (some-> state :lesson/state lesson/next-trial)]
      (cond
        (nil? to)
        [[:effect/enter :action/open-lesson]]

        (:lesson/viewed-trial state)
        [[:effect/save
          {:lesson/open-hint-index nil
           :lesson/viewed-trial    nil}]]

        :else
        [[:effect/save (shown {:lesson-state to})]]))))


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

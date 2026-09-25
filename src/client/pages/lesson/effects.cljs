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
  ;; The lesson is drawn from memory and on screen in the task of the tap. It
  ;; lives in app state only: leaving the screen drops it. Opened before the
  ;; reviews are in memory, the screen waits, and the lesson is drawn when
  ;; they arrive (`:action/refresh-page`).
  (fn open-lesson [state {:keys [active-id now-ms]}]
    [[:effect/save
      (shown (when (= :full (:learner/readiness state))
               (lesson/start (:learner/memory state) active-id {} now-ms)))]]))


(nxr/register-effect! :effect/check-answer
  (fn ^:async check-answer
    [{:keys [capabilities dispatch]} _ current-state answer]
    (try
      (let [{:keys [lesson-state]} (await (lesson/check-answer! capabilities current-state answer))]
        (when lesson-state
          (dispatch [[:action/update-lesson current-state lesson-state]])
          ;; The revealed answer carries hints for its annotated words; their
          ;; vocabulary states are looked up once here, not on every click.
          (let [trial (domain/current-trial lesson-state)]
            (when (domain/example-trial? trial)
              (let [hints (await (lesson/answer-annotations capabilities trial))]
                (dispatch [[:action/annotate-answer lesson-state hints]]))))))
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
    [{:keys [capabilities dispatch]} _ {:keys [dictionary-form hints lesson-state translation word-index]}]
    (try
      (await (vocabulary/add! capabilities dictionary-form translation :word))
      ;; The saved hint map re-renders the open card to its added state;
      ;; the popover's on-update then repositions the shell.
      (dispatch [[:action/annotate-answer
                  lesson-state
                  (assoc hints word-index :known-with-translation)]])
      (catch js/Error err
        (log/error :effect/add-token {:error (str err)})))))

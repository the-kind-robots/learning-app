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
  {:lesson/answer-hints    nil
   :lesson/empty?          (boolean error)
   :lesson/open-hint-index nil
   :lesson/state           lesson-state
   :lesson/waiting?        false
   :page/current           :page/lesson})


(nxr/register-effect! :effect/open-lesson
  ;; The lesson is drawn from memory and on screen in the task of the tap;
  ;; storing it waits until the screen is painted. Opened before memory is
  ;; ready, the screen waits for it, and the memory that arrives draws the
  ;; lesson (`application/memory-changer`).
  (fn open-lesson
    [{:keys [dispatch]} {:keys [store]} {:keys [active-id now-ms]}]
    (let [state @store]
      (if-not (:learner/ready? state)
        (swap! store merge (assoc (shown {}) :lesson/waiting? true))
        (let [{:keys [lesson-state] :as started}
              (lesson/start (:learner/memory state) active-id {} now-ms)]
          (swap! store merge (shown started))
          (when lesson-state
            (dispatch [[:effect/after-paint [[:effect/begin-lesson lesson-state]]]])))))))


(nxr/register-effect! :effect/begin-lesson
  (fn ^:async begin-lesson
    [{:keys [capabilities]} _ lesson-state]
    (try
      (await (lesson/begin! capabilities lesson-state))
      (catch :default err
        (log/error :effect/begin-lesson {:error (str err)})))))


(nxr/register-effect! :effect/check-answer
  (fn ^:async check-answer
    [{:keys [capabilities dispatch]} _ current-state answer]
    (try
      (let [{:keys [lesson-state]} (await (lesson/check-answer! capabilities current-state answer))]
        (when lesson-state
          (dispatch [[:action/update-lesson lesson-state]])
          ;; The revealed answer carries hints for its annotated words; their
          ;; vocabulary states are looked up once here, not on every click.
          (let [trial (domain/current-trial lesson-state)]
            (when (domain/example-trial? trial)
              (let [hints (await (lesson/answer-annotations capabilities trial))]
                (dispatch [[:action/annotate-answer hints]]))))))
      (catch js/Error err
        (log/error :effect/check-answer {:error (str err)})))))


(nxr/register-effect! :effect/next-trial
  (fn ^:async next-trial
    [{:keys [capabilities dispatch]} _ current-state]
    (try
      (let [{:keys [lesson-state]} (await (lesson/advance! capabilities current-state))]
        (when lesson-state
          (dispatch [[:action/update-lesson lesson-state]])))
      (catch js/Error err
        (log/error :effect/next-trial {:error (str err)})))))


;; Run by the route's `:stop`, after the navigation away and after the paint
;; of the screen it went to: it only ends.
(nxr/register-effect! :effect/end-lesson
  (fn ^:async end-lesson
    [{:keys [capabilities]} _]
    (try
      (await (lesson/finish! capabilities))
      (catch js/Error err
        (log/error :effect/end-lesson {:error (str err)})))))


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
    [{:keys [capabilities dispatch]} _ {:keys [dictionary-form hints translation word-index]}]
    (try
      (await (vocabulary/add! capabilities dictionary-form translation :word))
      ;; The saved hint map re-renders the open card to its added state;
      ;; the popover's on-update then repositions the shell.
      (dispatch [[:action/annotate-answer
                  (assoc hints word-index :known-with-translation)]])
      (catch js/Error err
        (log/error :effect/add-token {:error (str err)})))))

(ns pages.lesson.effects
  (:require
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [pages.lesson.popover :as popover]
   [use-cases.lesson :as lesson]
   [use-cases.vocabulary :as vocabulary]))


(nxr/register-action! :action/go-to-lesson
  (fn go-to-lesson [_]
    [[:effect/navigate :page/lesson]]))


(nxr/register-effect! :effect/load-lesson
  (fn ^:async load-lesson
    [{:keys [capabilities dispatch]} _]
    (try
      (let [{:keys [lesson-state error]} (await (lesson/restart! capabilities))]
        (dispatch [[:action/show-lesson {:lesson-state lesson-state :error error}]]))
      (catch js/Error err
        (log/error :effect/load-lesson {:error (str err)})))))


(nxr/register-effect! :effect/check-answer
  (fn ^:async check-answer
    [{:keys [capabilities dispatch]} _ answer]
    (try
      (let [{:keys [lesson-state]} (await (lesson/check-answer! capabilities answer))]
        (when lesson-state
          (dispatch [[:action/update-lesson lesson-state]])))
      (catch js/Error err
        (log/error :effect/check-answer {:error (str err)})))))


(nxr/register-effect! :effect/next-trial
  (fn ^:async next-trial
    [{:keys [capabilities dispatch]} _]
    (try
      (let [{:keys [lesson-state]} (await (lesson/advance! capabilities))]
        (when lesson-state
          (dispatch [[:action/update-lesson lesson-state]])))
      (catch js/Error err
        (log/error :effect/next-trial {:error (str err)})))))


(nxr/register-effect! :effect/end-lesson
  (fn ^:async end-lesson
    [{:keys [capabilities]} _]
    (try
      (await (lesson/finish! capabilities))
      (when-let [replace (get-in capabilities [:navigation :navigation/replace])]
        (replace :page/home))
      (catch js/Error err
        (log/error :effect/end-lesson {:error (str err)})))))


(nxr/register-effect! :effect/open-token-info
  (fn ^:async open-token-info
    [{:keys [capabilities dispatch]} _ {:keys [dictionary-form translation word-index]}]
    (try
      (let [td (await (lesson/token-info capabilities dictionary-form translation))]
        (dispatch [[:action/open-modal (assoc td :word-index word-index)]]))
      (catch js/Error err
        (log/error :effect/open-token-info {:error (str err)})))))


(nxr/register-effect! :effect/show-token-popover
  (fn show-token-popover
    [{:keys [dispatch]} _ word-index]
    (let [anchor (js/document.querySelector
                  (str ".lesson__answer-token[data-word-index=\"" word-index "\"]"))]
      (if anchor
        (popover/open! anchor #(dispatch [[:action/close-modal]]))
        (dispatch [[:action/close-modal]])))))


(nxr/register-effect! :effect/add-token
  (fn ^:async add-token
    [{:keys [capabilities dispatch]} _ {:keys [dictionary-form translation word-index]}]
    (try
      (await (vocabulary/add! capabilities dictionary-form translation))
      (dispatch [[:action/open-modal
                  {:dictionary-form dictionary-form
                   :state           :known-with-translation
                   :translation     translation
                   :word-index      word-index}]])
      (catch js/Error err
        (log/error :effect/add-token {:error (str err)})))))

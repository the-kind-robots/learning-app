(ns pages.lesson.effects
  (:require
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [use-cases.lesson :as lesson]))


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
    [{:keys [capabilities dispatch]} _ {:keys [dictionary-form translation]}]
    (try
      (let [td (await (lesson/token-info capabilities dictionary-form translation))]
        (dispatch [[:action/open-modal td]])
        (when (= :known-with-translation (:state td))
          (js/setTimeout #(dispatch [[:action/close-modal]]) 900)))
      (catch js/Error err
        (log/error :effect/open-token-info {:error (str err)})))))


(nxr/register-effect! :effect/add-token
  (fn ^:async add-token
    [{:keys [capabilities dispatch]} _ {:keys [dictionary-form translation]}]
    (try
      (await (lesson/add-word-from-structure! capabilities dictionary-form translation))
      (dispatch [[:action/open-modal
                  {:dictionary-form dictionary-form
                   :translation translation
                   :state       :known-with-translation}]])
      (js/setTimeout #(dispatch [[:action/close-modal]]) 900)
      (catch js/Error err
        (log/error :effect/add-token {:error (str err)})))))

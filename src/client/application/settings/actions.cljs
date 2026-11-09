(ns application.settings.actions
  "The settings, opened from the word mark: the learner's language and the device's colour scheme."
  (:require
   [nexus.registry :as nxr]))


(nxr/register-action! :action/choose-language
  (fn choose-language [_ language]
    [[:effect/choose-language language]]))


(nxr/register-action! :action/choose-scheme
  (fn choose-scheme [_ scheme]
    [[:effect/set-theme scheme]]))

(ns application.settings.actions
  "The settings, opened from the word mark: the learner's language."
  (:require
   [nexus.registry :as nxr]))


(nxr/register-action! :action/choose-language
  (fn choose-language [_ language]
    [[:effect/choose-language language]]))

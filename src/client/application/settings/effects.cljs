(ns application.settings.effects
  (:require
   [nexus.registry :as nxr]
   [use-cases.settings :as settings]))


(nxr/register-effect! :effect/choose-language
  ;; The interface re-renders in the language once memory takes the write.
  (fn choose-language [{:keys [capabilities]} _ language]
    (settings/choose-language! (:learner capabilities) language)
    nil))

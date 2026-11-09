(ns application.settings.effects
  (:require
   [nexus.registry :as nxr]
   [ports.page :as page]
   [use-cases.settings :as settings]
   [view-transition]))


(nxr/register-effect! :effect/choose-language
  ;; The interface re-renders in the language once memory takes the write.
  (fn choose-language [{:keys [capabilities]} _ language]
    (settings/choose-language! (:learner capabilities) language)
    nil))


(nxr/register-effect! :effect/set-theme
  ;; On a phone the whole screen dissolves into the scheme, every point at one pace; elsewhere it changes at once.
  ;; The radio pressed stays checked until the next render reads the pick.
  (fn set-theme [{:keys [capabilities]} _ scheme]
    (view-transition/change! :scheme #(page/choose-scheme! (:page capabilities) scheme))
    nil))

(ns use-cases.settings
  "The learner's settings, which follow them to every device of the account."
  (:require
   [i18n :as i18n]
   [ports.learner :as learner]
   [ports.page :as page]))


(defn language
  "The language the learner speaks: their pick, else the system's (`i18n/language`)."
  [learner page]
  (i18n/language (learner/language learner) (page/system-language page)))


(defn choose-language!
  "Makes `language` the learner's. A promise as `ports.learner/change!` gives."
  [learner language]
  (learner/change! learner [[:entity/settings "settings" #(assoc % :language language)]]))

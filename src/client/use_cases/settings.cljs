(ns use-cases.settings
  "The learner's settings, which follow them to every device of the account."
  (:require
   [ports.learner :as learner]))


(defn choose-language!
  "Makes `language` the learner's. A promise as `ports.learner/change!` gives."
  [learner language]
  (learner/change! learner [[:entity/settings "settings" #(assoc % :language language)]]))

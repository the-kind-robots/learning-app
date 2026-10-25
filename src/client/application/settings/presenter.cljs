(ns application.settings.presenter
  (:require
   [i18n :as i18n]))


(defn language
  "The language the interface speaks: the learner's pick, else the system's (`i18n/language`)."
  [state]
  (i18n/language (:learner/language state) (:system/language state)))


(defn props
  "What the settings show: a language for each dictionary. A German system with no pick asks, when there is
   a choice: the settings open at start, closed only by a pick."
  [state]
  (let [current (language state)]
    {:asking?   (and (= :status/loaded (:learner/status state))
                     (nil? (:learner/language state))
                     (i18n/asks-language? (:system/language state)))
     :languages (mapv (fn [code] {:code code :name (i18n/language-name code) :selected? (= code current)})
                      i18n/languages)}))

(ns client.i18n-test
  (:require
   [cljs.test :refer-macros [are deftest is]]
   [i18n :as sut]))


(deftest a-learner-without-a-pick-gets-the-system-language-it-has-a-dictionary-for
  (are [languages tag language] (= language (sut/system-language languages tag))
   ["en" "fr" "ru" "tr"] "tr-TR" "tr"
   ["en" "fr" "ru" "tr"] "FR" "fr"
   ["en" "fr" "ru" "tr"] "ja-JP" "en"
   ["en" "fr" "ru" "tr"] nil "en"
   ["ru"] "tr-TR" "ru"
   ["ru"] "en-GB" "ru"))


(deftest a-german-system-asks-only-when-there-is-a-choice
  (are [languages tag asks?] (= asks? (sut/asks-language? languages tag))
   ["en" "ru"] "de-DE" true
   ["en" "ru"] "de"    true
   ["ru"]      "de-DE" false
   ["en" "ru"] "ru-RU" false))


(deftest every-language-file-is-a-language-named-in-it
  (is (= ["en" "fr" "ru" "tr"] sut/languages))
  (are [language name] (= name (sut/language-name language))
   "en" "English"
   "fr" "Français"
   "ru" "Русский"
   "tr" "Türkçe"))

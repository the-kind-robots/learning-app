(ns application.settings.view)


(defn menu
  "The settings, a popover under the word mark: the learner's language, applied the moment it is checked.
   The browser opens and closes it. While the learner is asked it is no popover but a plain block: shown, with
   the focus on the language checked, and nothing closes it; the pick makes it a popover again, a closed one."
  [{:keys [asking? languages]}]
  [:div.settings-menu
   {:id         "settings"
    ;; A new key when asking starts or ends, so the block that asks is mounted, and takes the focus, anew.
    :replicant/key (if asking? :asking :menu)
    :replicant/on-mount (when asking? [[:effect/focus-child "input:checked"]])
    :aria-label [:i18n :settings/title]
    :popover    (when-not asking? "auto")
    :role       "dialog"}
   ;; Radios: one language of four, the arrows move between them; the group is named, the name not shown.
   [:fieldset.settings-menu__group
    {:aria-label [:i18n :settings/language]}
    (for [{:keys [code selected?] :as language} languages]
      [:label.settings-menu__language
       {:replicant/key code
        :lang code}
       [:input.settings-menu__radio
        {:checked selected?
         :name    "language"
         :type    "radio"
         :value   code
         ;; A click, not a change: the asked learner may keep the language already checked, and arrow keys click too.
         :on      {:click [[:action/choose-language code]]}}]
       ;; Each language by its own name: a learner finds theirs whatever the interface speaks.
       (:name language)
       ;; The flag adorns the name: the name alone is read out. A round badge in the palette's colours.
       (when-let [flag (get {"en" [[:rect.settings-menu__flag-blue {:height 40 :width 40}]
                                   [:rect.settings-menu__flag-white
                                    {:height 7 :transform "rotate(45 20 20)" :width 60 :x -10 :y 16.5}]
                                   [:rect.settings-menu__flag-white
                                    {:height 7 :transform "rotate(-45 20 20)" :width 60 :x -10 :y 16.5}]
                                   [:rect.settings-menu__flag-white {:height 40 :width 12 :x 14}]
                                   [:rect.settings-menu__flag-white {:height 12 :width 40 :y 14}]
                                   [:rect.settings-menu__flag-red {:height 40 :width 6 :x 17}]
                                   [:rect.settings-menu__flag-red {:height 6 :width 40 :y 17}]]
                             "fr" [[:rect.settings-menu__flag-blue {:height 40 :width 14}]
                                   [:rect.settings-menu__flag-white {:height 40 :width 13 :x 13.5}]
                                   [:rect.settings-menu__flag-red {:height 40 :width 14 :x 26}]]
                             "ru" [[:rect.settings-menu__flag-white {:height 14 :width 40}]
                                   [:rect.settings-menu__flag-blue {:height 13 :width 40 :y 13.5}]
                                   [:rect.settings-menu__flag-red {:height 14 :width 40 :y 26}]]
                             "tr" [[:rect.settings-menu__flag-red {:height 40 :width 40}]
                                   [:circle.settings-menu__flag-white {:cx 16 :cy 20 :r 10}]
                                   [:circle.settings-menu__flag-red {:cx 18.5 :cy 20 :r 8}]
                                   [:path.settings-menu__flag-white {:d "M23.9 20l9-2.9-5.6 7.7v-9.5l5.6 7.7z"}]]}
                            code)]
         (into [:svg.settings-menu__flag
                {:aria-hidden "true"
                 :viewBox     "0 0 40 40"}]
               flag))])]])

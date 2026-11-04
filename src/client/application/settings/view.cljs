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
   [:fieldset.group
    {:aria-label [:i18n :settings/language]}
    (for [{:keys [code selected?] :as language} languages]
      [:label.language
       {:replicant/key code
        :lang code}
       [:input.radio
        {:checked selected?
         :name    "language"
         :type    "radio"
         :value   code
         ;; A click, not a change: the asked learner may keep the language already checked, and arrow keys click too.
         :on      {:click [[:action/choose-language code]]}}]
       ;; Each language by its own name: a learner finds theirs whatever the interface speaks.
       (:name language)
       ;; The flag adorns the name: the name alone is read out. A small flat stripe in the palette's colours.
       ;; zprint: as written
       (when-let [flag (get {"en" [[:rect.flag-blue {:height 40 :width 60}]
                                   ;; A wide white edge keeps the red cross off the blue: the two are near in lightness.
                                   [:rect.flag-white {:height 40 :width 15 :x 22.5}]
                                   [:rect.flag-white {:height 15 :width 60 :y 12.5}]
                                   [:rect.flag-red {:height 40 :width 5 :x 27.5}]
                                   [:rect.flag-red {:height 5 :width 60 :y 17.5}]]
                             "fr" [[:rect.flag-blue {:height 40 :width 20}]
                                   [:rect.flag-white {:height 40 :width 20 :x 20}]
                                   [:rect.flag-red {:height 40 :width 20 :x 40}]]
                             "ru" [[:rect.flag-white {:height 13.34 :width 60}]
                                   [:rect.flag-blue {:height 13.34 :width 60 :y 13.33}]
                                   [:rect.flag-red {:height 13.34 :width 60 :y 26.66}]]
                             "tr" [[:rect.flag-red {:height 40 :width 60}]
                                   [:circle.flag-white {:cx 21.25 :cy 20 :r 10}]
                                   [:circle.flag-red {:cx 23.75 :cy 20 :r 8}]
                                   [:path.flag-white
                                    {:d "M29.167 20l9.045 2.939-5.59-7.694v9.511l5.59-7.694z"}]]}
                            code)]
         (into [:svg.flag
                {:aria-hidden "true"
                 :viewBox     "0 0 60 40"}]
               flag))])]])

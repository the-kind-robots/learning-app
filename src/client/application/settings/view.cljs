(ns application.settings.view
  (:require
   [replicant.alias :as alias]))


(alias/defalias menu
  "The settings, a popover under the word mark: the learner's language and the device's colour scheme, each
   applied the moment it is checked. The browser opens and closes it. While the learner is asked it is no
   popover but a plain block of the languages alone: shown, with the focus on the language checked, and nothing
   closes it; the pick makes it a popover again, a closed one."
  [{:menu/keys [asking? languages schemes]}]
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
       ;; The flag adorns the name: the name alone is read out. A small flat stripe in the palette's colours.
       ;; zprint: as written
       (when-let [flag (get {"en" [[:rect.settings-menu__flag-blue {:height 40 :width 60}]
                                   ;; A wide white edge keeps the red cross off the blue: the two are near in lightness.
                                   [:rect.settings-menu__flag-white {:height 40 :width 15 :x 22.5}]
                                   [:rect.settings-menu__flag-white {:height 15 :width 60 :y 12.5}]
                                   [:rect.settings-menu__flag-red {:height 40 :width 5 :x 27.5}]
                                   [:rect.settings-menu__flag-red {:height 5 :width 60 :y 17.5}]]
                             "fr" [[:rect.settings-menu__flag-blue {:height 40 :width 20}]
                                   [:rect.settings-menu__flag-white {:height 40 :width 20 :x 20}]
                                   [:rect.settings-menu__flag-red {:height 40 :width 20 :x 40}]]
                             "ru" [[:rect.settings-menu__flag-white {:height 13.34 :width 60}]
                                   [:rect.settings-menu__flag-blue {:height 13.34 :width 60 :y 13.33}]
                                   [:rect.settings-menu__flag-red {:height 13.34 :width 60 :y 26.66}]]
                             "tr" [[:rect.settings-menu__flag-red {:height 40 :width 60}]
                                   [:circle.settings-menu__flag-white {:cx 21.25 :cy 20 :r 10}]
                                   [:circle.settings-menu__flag-red {:cx 23.75 :cy 20 :r 8}]
                                   [:path.settings-menu__flag-white
                                    {:d "M29.167 20l9.045 2.939-5.59-7.694v9.511l5.59-7.694z"}]]}
                            code)]
         (into [:svg.settings-menu__flag
                {:aria-hidden "true"
                 :viewBox     "0 0 60 40"}]
               flag))])]
   ;; The schemes, one row of three under a rule: an icon over each name, the name read out.
   (when-not asking?
     (list
      [:hr.settings-menu__rule]
      [:fieldset.settings-menu__schemes
       {:aria-label [:i18n :settings/scheme]}
       (for [{:keys [scheme selected?]} schemes]
         [:label.settings-menu__scheme
          {:replicant/key scheme}
          [:input.settings-menu__radio
           {:checked selected?
            :name    "scheme"
            :type    "radio"
            :value   (name scheme)
            :on      {:click [[:action/choose-scheme scheme]]}}]
          ;; zprint: as written
          [:svg.settings-menu__scheme-icon
           {:aria-hidden "true"
            :viewBox     "0 0 24 24"}
           (case scheme
             :scheme/system (list
                             [:circle {:cx 12 :cy 12 :r 8.5}]
                             [:path {:d "M12 3.5a8.5 8.5 0 0 1 0 17z" :fill "currentColor" :stroke "none"}])
             :scheme/light  (list
                             [:circle {:cx 12 :cy 12 :r 4}]
                             [:path
                              {:d (str "M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4"
                                       "M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4")}])
             :scheme/dark   [:path {:d "M19.5 14.6A8 8 0 0 1 9.4 4.5a8 8 0 1 0 10.1 10.1z"}])]
          (case scheme
            :scheme/system [:i18n :settings/scheme-system]
            :scheme/light  [:i18n :settings/scheme-light]
            :scheme/dark   [:i18n :settings/scheme-dark])])]))])

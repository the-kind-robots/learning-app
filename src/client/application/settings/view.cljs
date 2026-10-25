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
   ;; Radios: one language of four, the arrows move between them, and the legend names the group.
   [:fieldset.settings-menu__group
    [:legend.settings-menu__label [:i18n :settings/language]]
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
       ;; A check beside the fill: the language in use is told apart by more than colour.
       (when selected?
         [:svg.settings-menu__check
          {:aria-hidden    "true"
           :fill           "none"
           :stroke         "currentColor"
           :stroke-linecap "round"
           :stroke-linejoin "round"
           :stroke-width   "3"
           :viewBox        "0 0 24 24"}
          [:polyline {:points "5 12.5 10 17.5 19 7"}]])])]])

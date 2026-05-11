(ns pages.home.view)


(defn- suggestion-item
  [{:keys [lemma translation]}]
  [:li.suggestions__item
   {:role "option"
    :data-ac-value lemma
    :data-ac-secondary translation}
   [:span {:lang "de"} lemma]
   (when translation [:span.suggestions__translation {:lang "ru"} translation])])


(defn- add-form
  [{:home/keys [suggestions prefill add-error]}]
  [:form.home__add-form
   {:id "home-add-form"
    :autocapitalize "none"
    :autocorrect "off"
    :on {:submit [[:effect/prevent-default]
                  [:action/add-word
                   {:value       [:event.form.field/value "value"]
                    :translation [:event.form.field/value "translation"]}]]}}
   [:fieldset.home__add-fieldset
    [:legend.home__add-legend "Добавить слово"]
    [:word-autocomplete
     [:div.home__add-form-row
      [:div.autocomplete
       [:label.home__add-form-label {:for "new-word-value"} "Слово (немецкий)"]
       [:input.home__add-form-input
        {:id "new-word-value"
         :name "value"
         :autocapitalize "none"
         :autocomplete "off"
         :autocorrect "off"
         :enterkeyhint "next"
         :spellcheck "false"
         :data-ac-role "word"
         :required true
         :lang "de"
         :default-value (or prefill "")
         :placeholder "Новое слово"
         :on {:input [[:action/look-up-word [:event.target/value]]]}}]
       [:ul.suggestions
        {:data-ac-role "list"}
        (for [s suggestions]
          (suggestion-item s))]]
      [:span.home__add-form-arrow "→"]
      [:div.home__add-translation
       [:label.home__add-form-label {:for "new-word-translation"} "Перевод (русский)"]
       [:input.home__add-form-input
        {:id             "new-word-translation"
         :name           "translation"
         :autocapitalize "none"
         :autocomplete   "off"
         :autocorrect    "off"
         :enterkeyhint   "done"
         :spellcheck     "false"
         :data-ac-role   "translation"
         :lang           "ru"
         :placeholder    "Перевод"
         :required       true
         :class          (when (= :empty-translations add-error) "home__add-form-input--error")}]]]]
    [:button.home__add-form-submit.big-button.big-button--request-stable
     {:type "submit"} "ДОБАВИТЬ"]]])


(defn page
  [state]
  (let [{:home/keys [empty-vocab?]} state]
    [:div.home
     {:data-vk-overlay true}
     [:header.home__intro
      [:h1.home__title "Главная"]
      [:p.home__subtitle "Быстро добавляйте слова и учите немецкий даже без сети."]]

     [:main.home__content
      [:section#home-add-panel.home__add
       {:replicant/on-mount [[:action/focus-word-input]]}
       [:header.home__add-header
        [:h2.home__panel-title "Быстрое добавление"]
        [:button#home-words-button.home__words-button
         {:type        "button"
          :class       (when empty-vocab? "home__words-button--hidden")
          :aria-hidden (when empty-vocab? "true")
          :tabindex    (when empty-vocab? "-1")
          :on          {:click [[:action/go-to-words]]}}
         "Список слов"]]

       (add-form state)]]

     [:footer#home-lesson-footer.home__footer.page-footer
      {:hidden empty-vocab?}
      [:h2.home__lesson-title "Урок"]
      [:div.page-footer__action
       [:button.home__lesson-button.big-button.green-button
        {:on {:click [[:action/go-to-lesson]]}}
        "НАЧАТЬ УРОК"]]]]))

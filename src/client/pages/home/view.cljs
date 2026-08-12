(ns pages.home.view
  (:require
   [pages.home.presenter :as presenter]))


(defn- suggestion-item
  [{:keys [lemma phrase? translations exact?]} active?]
  [:li.suggestions__item
   {:role        "option"
    :data-active (when active? "")
    :data-exact  (when exact? "")
    :on          {:click       [[:action/select-suggestion
                                 {:lemma        lemma
                                  :phrase?      phrase?
                                  :translations translations
                                  :focus-id     "new-word-translation"}]]
                  ;; Keep input focus during the click so :blur on the input
                  ;; doesn't fire and clear the suggestions before :click runs.
                  :pointerdown [[:effect/prevent-default]]}}
   [:span {:lang "de"} lemma]
   (when phrase?
     [:span.suggestions__badge "фраза"])])


(defn- add-form
  [{:keys [add-error add-warning copy phrase-mode? show-chip? suggestions translation word]}]
  (let [{:keys [items active]} suggestions]
    [:form.home__add-form
     {:id "home-add-form"
      :autocapitalize "none"
      :autocorrect "off"
      :on {:submit [[:effect/prevent-default]
                    [:action/add-word
                     {:value       [:event.form.field/value "value"]
                      :translation [:event.form.field/value "translation"]
                      :focus-id    "new-word-value"}]]}}
     [:fieldset.home__add-fieldset
      [:legend.home__add-legend (:legend copy)]
      [:div.home__add-form-row
       {:class (when phrase-mode? "home__add-form-row--phrase")}
       [:div.autocomplete
        [:div.home__add-label-row
         [:label.home__add-form-label {:for "new-word-value"} (:value-label copy)]
         (when show-chip?
           [:button.home__mode-chip
            {:type "button"
             :on   {:click       [[:action/toggle-add-mode]]
                    ;; Keep input focus through the tap, as with suggestions.
                    :pointerdown [[:effect/prevent-default]]}}
            (:chip-label copy)])]
        [:textarea.home__add-form-input.home__add-form-textarea
         {:id           "new-word-value"
          :name         "value"
          :rows         1
          :autocapitalize "none"
          :autocomplete "off"
          :autocorrect  "off"
          :enterkeyhint "next"
          :spellcheck   "false"
          :required     true
          :maxlength    200
          :lang         "de"
          :value        word
          :placeholder  (:placeholder copy)
          :on           {:blur    [[:action/dismiss-suggestions]]
                         :focus   [[:effect/autogrow-target]]
                         :input   [[:action/update-word [:event.target/value]]
                                   [:effect/autogrow-target]]
                         :keydown [[:action/handler-word-keydown
                                    {:key      [:event.keyboard/key]
                                     :shift?   [:event.keyboard/shift?]
                                     :focus-id "new-word-translation"
                                     :scroll-selector ".suggestions [data-active]"}]]}}]
        [:ul.suggestions
         (for [item items]
           (suggestion-item item (= item active)))]
        (when add-warning
          [:p.home__add-warning add-warning])]
       [:span.home__add-form-arrow "→"]
       [:div.home__add-translation
        [:label.home__add-form-label {:for "new-word-translation"} "Перевод (русский)"]
        [:textarea.home__add-form-input.home__add-form-textarea
         {:id           "new-word-translation"
          :name         "translation"
          :rows         1
          :autocapitalize "none"
          :autocomplete "off"
          :autocorrect  "off"
          :enterkeyhint "done"
          :spellcheck   "false"
          :maxlength    500
          :lang         "ru"
          :value        translation
          :placeholder  (or (first (:translations active)) "Перевод")
          :required     true
          :class        (when (= :empty-translations add-error)
                          "home__add-form-input--error")
          :on           {:focus   [[:effect/autogrow-target]]
                         :input   [[:action/update-translation [:event.target/value]]
                                   [:effect/autogrow-target]]
                         :keydown [[:action/submit-if-enter
                                    {:key    [:event.keyboard/key]
                                     :shift? [:event.keyboard/shift?]}]]}}]]]
      [:button.home__add-form-submit.big-button.big-button--request-stable
       {:type "submit"} "ДОБАВИТЬ"]]]))


(defn- collection-heading
  [collection-name]
  [:h2.home__collection-heading
   {:contenteditable "plaintext-only"
    :spellcheck "false"
    :replicant/key "collection-heading"
    :on {:blur    [[:effect/rename-active-collection
                    [:event.target/text-content]]]
         :keydown [[:action/handle-collection-rename-keydown
                    [:event.keyboard/key]]]}}
   collection-name])


(defn page
  [state]
  (let [{:keys [active-collection empty-vocab? form]} (presenter/page-props state)]
    [:div.home
     {:data-vk-overlay true}
     [:header.home__intro
      [:h1.home__title "Главная"]
      [:p.home__subtitle
       {:style {:visibility (if empty-vocab? "visible" "hidden")}}
       "Быстро добавляйте слова и учите немецкий даже без сети."]]

     [:div.home__heading-slot
      (when active-collection
        (collection-heading (:name active-collection)))]

     [:main.home__content
      [:section#home-add-panel.home__add
       {:replicant/on-mount [[:action/focus-word-input "new-word-value"]]}
       [:header.home__add-header
        [:h2.home__panel-title (get-in form [:copy :legend])]
        [:button#home-words-button.home__words-button
         {:type        "button"
          :class       (when empty-vocab? "home__words-button--hidden")
          :aria-hidden (when empty-vocab? "true")
          :tabindex    (when empty-vocab? "-1")
          :on          {:click [[:action/go-to-words]]}}
         "Список слов"]]

       (add-form form)]]

     [:footer#home-lesson-footer.home__footer.page-footer
      {:hidden empty-vocab?}
      [:h2.home__lesson-title "Урок"]
      [:div.page-footer__action
       [:button.home__lesson-button.big-button.green-button
        {:on {:click [[:action/go-to-lesson]]}}
        "НАЧАТЬ УРОК"]]]]))

(ns pages.home.view
  (:require
   [pages.home.presenter :as presenter]))


(defn- suggestion-item
  [{:keys [active? completes? lemma phrase? pos translations exact?]}]
  [:li.item
   {:role        "option"
    :data-active (when active? "")
    :data-exact  (when exact? "")
    :aria-disabled (when-not completes? "true")
    :on          (if completes?
                   {:click       [[:action/select-suggestion
                                   {:lemma        lemma
                                    :pos          pos
                                    :translations translations
                                    :focus-id     "new-word-translation"}]]
                    ;; Keep input focus during the click, or :blur clears the suggestions before :click runs.
                    :pointerdown [[:effect/prevent-default]]}
                   ;; An inert row still swallows the press, or focus would leave the field and :blur clear the list.
                   {:pointerdown [[:effect/prevent-default]]})}
   [:span
    {:lang "de"}
    lemma]
   (when phrase?
     [:span.badge
      [:i18n :home/phrase-badge]])])


(defn- add-form
  [{:keys [error phrase? pos suggestions translation translation-invalid? word]}]
  (let [{:keys [items]} suggestions]
    [:form.form
     {:id "home-add-form"
      :autocapitalize "none"
      :autocorrect "off"
      :on {:submit [[:effect/prevent-default]
                    [:action/add-word
                     {:value       [:event.form.field/value "value"]
                      :translation [:event.form.field/value "translation"]
                      :pos         pos
                      :focus-id    "new-word-value"}]]}}
     [:fieldset.fieldset
      ;; The mode has no control of its own: the legend and the label say which the form is about to save.
      [:legend.visually-hidden
       (if phrase? [:i18n :phrase/add] [:i18n :word/add])]
      [:div.row
       [:div.autocomplete
        [:div
         [:label.visually-hidden
          {:for "new-word-value"}
          (if phrase? [:i18n :home/phrase-label] [:i18n :home/word-label])]]
        [:textarea.field
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
          :placeholder  (if phrase? [:i18n :home/phrase-placeholder] [:i18n :home/word-placeholder])
          :on           {:blur    [[:action/dismiss-suggestions]]
                         :focus   [[:effect/autogrow-target]]
                         :input   [[:action/update-word [:event.target/value]]
                                   [:effect/autogrow-target]]
                         :keydown [[:action/handler-word-keydown
                                    (merge {:key      [:event.keyboard/key]
                                            :shift?   [:event.keyboard/shift?]
                                            :focus-id "new-word-translation"
                                            :scroll-selector ".suggestions [data-active]"}
                                           (select-keys suggestions [:marked :next-idx :previous-idx]))]]}}]
        [:ul.suggestions
         (for [item items]
           (suggestion-item item))]]
       [:div.translation
        [:label.visually-hidden
         {:for "new-word-translation"}
         [:i18n :home/translation-label]]
        [:textarea.field
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
          :placeholder  [:i18n :home/translation-placeholder]
          :required     true
          :aria-invalid (when translation-invalid? "true")
          :on           {:focus   [[:effect/autogrow-target]]
                         :input   [[:action/update-translation [:event.target/value]]
                                   [:effect/autogrow-target]]
                         ;; A translation may span several lines, so a bare Enter belongs to the text; submitting takes a
                         ;; modifier, or the button, the only path on a phone.
                         :keydown [[:action/submit-if-ctrl-enter
                                    {:key   [:event.keyboard/key]
                                     :ctrl? [:event.keyboard/ctrl?]
                                     :meta? [:event.keyboard/meta?]}]]}}]]]
      ;; Every add error is said in words; a border alone looked like nothing happened. A failed save
      ;; is the app's fault and nothing the user does fixes it, so the text says only that.
      (when error
        [:p.error
         {:role "alert"}
         (case error
           :error/empty-translations [:i18n :home/empty-translations]
           :error/save-failed        (if phrase?
                                       [:i18n :home/phrase-save-failed]
                                       [:i18n :home/word-save-failed]))])
      [:button.submit.big-button
       {:type        "submit"
        :data-steady true}
       [:i18n :home/add]]]]))


(defn- collection-heading
  [collection-name]
  [:h2.heading
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
     [:header.intro
      [:h1.visually-hidden
       [:i18n :home/title]]
      [:p.subtitle
       {:style {:visibility (if empty-vocab? "visible" "hidden")}}
       [:i18n :home/subtitle]]]

     [:div
      (when active-collection
        (collection-heading (:name active-collection)))]

     [:main.content
      [:section#home-add-panel.add
       {:replicant/on-mount [[:action/focus-word-input "new-word-value"]]}
       [:header.add-header
        [:h2.panel-title
         (if (:phrase? form) [:i18n :phrase/add] [:i18n :word/add])]
        [:button#home-words-button.words-button
         {:type        "button"
          :aria-hidden (when empty-vocab? "true")
          :tabindex    (when empty-vocab? "-1")
          :on          {:click [[:action/go-to-words]]}}
         [:i18n :home/words]]]

       (add-form form)]]

     [:footer#home-lesson-footer.footer.page-footer
      {:hidden empty-vocab?}
      [:h2.visually-hidden
       [:i18n :lesson/title]]
      [:div.footer-action
       [:button.lesson-button.big-button.green-button
        {:on {:click [[:action/go-to-lesson]]}}
        [:i18n :lesson/start]]]]]))

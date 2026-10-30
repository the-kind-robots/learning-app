(ns pages.words.view
  (:require
   [clojure.string :as str]
   [pages.words.presenter :as presenter]))


(defn- word-list-item
  [{:keys [id retention-band retention-color retention-percent translation value]}]
  [:li.word-item
   {:id (str "word-" id)}
   [:button.word-item__display
    {:type "button"
     :on   {:click [[:effect/enter :action/open-word-edit id]]}}
    [:span.word-item__text
     [:span.word-item__value {:lang "de"} value]
     [:span.word-item__translation {:lang "ru"} translation]]
    [:div.word-item__retention
     {:style {:background-color retention-color}
      :title [:i18n
              (case retention-band
                :retention/excellent :words/retention-excellent
                :retention/good      :words/retention-good
                :retention/due       :words/retention-due
                :retention/new       :words/retention-new)
              {:percent retention-percent}]}]]])


(defn- sentinel
  "The end of the loaded rows. It is keyed so appending rows in front of it
   reuses the node, and the observer watching it survives the render."
  []
  [:li.word-list__sentinel
   {:replicant/key        :word-list-sentinel
    :replicant/on-mount   [[:effect/observe-words-sentinel]]
    :replicant/on-unmount [[:effect/unobserve-words-sentinel]]}])


(defn- example-card
  "The card of one collection's example. Each text area lies over the same text set as a paragraph, so the
   bold word shows and writing moves nothing; a status takes the translation's place."
  [{:keys [accent collection-id regenerate-disabled? regenerating? sentence status-shown? title translation failed?]}]
  (let [text (str (:before sentence) (:hit sentence) (:after sentence))]
    [:section.word-example
     {:aria-labelledby (str "word-example-" collection-id)
      :replicant/key collection-id
      :class (case accent
               :accent/owl      "word-example--owl"
               :accent/macaw    "word-example--macaw"
               :accent/fox      "word-example--fox"
               :accent/starfish "word-example--starfish"
               :accent/beetle   "word-example--beetle"
               :accent/bee      "word-example--bee"
               :accent/cardinal "word-example--cardinal"
               :accent/peacock  "word-example--peacock"
               nil              "word-example--bare")}
     [:h3.word-example__title {:id (str "word-example-" collection-id)} (or title [:i18n :example/no-theme])]
     [:div.word-example__sentence
      {:class (when regenerating? "word-example__sentence--pending")}
      [:p.word-example__text {:aria-hidden "true" :lang "de"}
       (:before sentence)
       [:strong (:hit sentence)]
       (:after sentence)]
      [:textarea.word-example__field
       {:aria-label     [:i18n :example/sentence]
        :autocapitalize "sentences"
        :autocorrect    "off"
        :lang           "de"
        :readonly       regenerate-disabled?
        :rows           1
        :spellcheck     "false"
        :value          text
        :on             {:input [[:action/edit-example collection-id :value [:event.target/value]]]}}]]
     [:div.word-example__translation
      [:p.word-example__text.word-example__text--translation
       {:aria-hidden "true"
        :class       (when status-shown? "word-example__text--hidden")}
       translation]
      [:textarea.word-example__field.word-example__field--translation
       {:aria-label  [:i18n :example/translation]
        :autocorrect "off"
        :class       (when status-shown? "word-example__field--hidden")
        :readonly    regenerate-disabled?
        :rows        1
        :spellcheck  "false"
        :value       translation
        :on          {:input [[:action/edit-example collection-id :translation [:event.target/value]]]}}]
      [:p.word-example__status
       {:aria-live "polite"
        :class     [(when failed? "word-example__status--failed")
                    (when-not status-shown? "word-example__text--hidden")]}
       (cond
         regenerating? [:i18n :example/regenerating]
         failed?       [:i18n :example/failed])]]
     [:div.word-example__spacer]
     [:button.word-example__regenerate
      {:disabled regenerate-disabled?
       :type     "button"
       :on       {:click [[:effect/enter :action/regenerate-example collection-id]]}}
      [:svg.word-example__icon
       {:aria-hidden    "true"
        :class          (when regenerating? "word-example__icon--spin")
        :fill           "none"
        :stroke         "currentColor"
        :stroke-linecap "round"
        :stroke-linejoin "round"
        :stroke-width   "3"
        :viewBox        "0 0 24 24"}
       [:path {:d "M19.5 12a7.5 7.5 0 1 1-2.2-5.3"}]
       [:path {:d "M19.5 4.5v4.5H15"}]]
      [:i18n :example/regenerate]]]))


(defn- edit-dialog
  [{:keys [examples id replaces reset-disabled? save-disabled? stored-word translation-rows translations word
           word-multiline? word-taken?]}
   in-collection?]
  [:dialog.word-edit-dialog.modal
   {:replicant/key id
    :replicant/on-mount [[:action/open-dialog]]
    :on {:click [[:action/dismiss-on-backdrop [:event/self-click?]]]
         :close [[:action/close-word-edit]]}}
   ;; The form takes the focus the dialog opens with: a field would show its fill and, on a phone, the keyboard.
   [:form.word-edit-dialog__form
    {:autocapitalize "none"
     :autocorrect "off"
     :autofocus   true
     :tabindex    "-1"
     :on          {:submit [[:effect/prevent-default]
                            [:action/save-word
                             {:id          id
                              :translation [:event.form.field/value "translation"]
                              :value       [:event.form.field/value "value"]}]]}}
    [:div.word-edit-dialog__body
     [:div.word-edit-dialog__head
      ;; A textarea where the word may run over several lines, as a phrase does.
      [(if word-multiline?
         :textarea.word-edit-dialog__word.word-edit-dialog__word--phrase
         :input.word-edit-dialog__word)
       {:aria-invalid (when word-taken? "true")
        :aria-label   [:i18n :word-edit/word]
        :autocapitalize "none"
        :autocomplete "off"
        :autocorrect  "off"
        :lang         "de"
        :name         "value"
        :rows         (when word-multiline? 2)
        :spellcheck   "false"
        :value        word
        :on           {:input [[:action/edit-spelling [:event.target/value]]]}}]
      (when word-taken?
        [:div.word-edit-dialog__error {:role "alert"} [:i18n :word-edit/taken]])
      ;; One translation a line. Enter belongs to the text; Ctrl/Cmd+Enter and the save button submit.
      [:textarea.word-edit-dialog__translations
       {:aria-label   [:i18n :word-edit/translations]
        :autocapitalize "none"
        :autocomplete "off"
        :autocorrect  "off"
        :lang         "ru"
        :name         "translation"
        :rows         translation-rows
        :spellcheck   "false"
        :value        (str/join "\n" translations)
        :on           {:input   [[:action/edit-translations [:event.target/value]]
                                 [:effect/autogrow-target]]
                       :keydown [[:action/submit-if-ctrl-enter
                                  {:key   [:event.keyboard/key]
                                   :ctrl? [:event.keyboard/ctrl?]
                                   :meta? [:event.keyboard/meta?]}]]}}]
      (when replaces
        [:div.word-edit-dialog__notice {:role "status"}
         [:i18n :word-edit/replaces {:word replaces}]])]
     [:div.word-examples
      (map example-card examples)
      [:div.word-examples__end {:aria-hidden "true"}]]]
    [:div.word-edit-dialog__footer
     [:button.word-edit-dialog__delete
      {:aria-label [:i18n :word-edit/delete]
       :type       "button"
       :on         {:click [[:action/remove-word
                             {:confirm-message (if in-collection?
                                                 [:i18n :word-edit/remove-confirm {:word stored-word}]
                                                 [:i18n :word-edit/delete-confirm {:word stored-word}])
                              :id    id
                              :value stored-word}]]}}
      [:svg
       {:aria-hidden    "true"
        :fill           "none"
        :height         "22"
        :stroke         "currentColor"
        :stroke-linecap "round"
        :stroke-linejoin "round"
        :stroke-width   "2"
        :viewBox        "0 0 24 24"
        :width          "22"}
       [:path {:d "M4 7h16"}]
       [:path {:d "M10 11v6"}]
       [:path {:d "M14 11v6"}]
       [:path {:d "M6 7l1 13h10l1-13"}]
       [:path {:d "M9 7V4h6v3"}]]]
     [:div.word-edit-dialog__spacer]
     [:button.word-edit-dialog__reset
      {:disabled reset-disabled?
       :type     "button"
       :on       {:click [[:action/reset-word-edit]]}}
      [:i18n :word-edit/reset]]
     [:button.word-edit-dialog__save {:type "submit" :disabled save-disabled?} [:i18n :word-edit/save]]]]])


(defn- empty-state
  [kind]
  [:div.vocabulary__empty-state
   [:p.vocabulary__empty-state-text
    (case kind
      :empty/first-run  [:i18n :words/empty]
      :empty/no-matches [:i18n :words/no-matches])]
   [:p.vocabulary__empty-state-hint
    (case kind
      :empty/first-run  [:i18n :words/empty-hint]
      :empty/no-matches [:i18n :words/no-matches-hint])]
   (case kind
     :empty/first-run  [:button.vocabulary__empty-state-cta
                        {:on {:click [[:action/go-to-home]]}}
                        [:i18n :word/add]]
     :empty/no-matches nil)])


(defn page
  [state]
  (let [{:keys [editing in-collection? items known? more? search vocabulary?] placeholder :empty-state}
        (presenter/page-props state)]
    [:div.vocabulary
     (when editing (edit-dialog editing in-collection?))
     (cond
       (not known?)
       nil

       (not vocabulary?)
       [:div.vocabulary__list.vocabulary__list--empty
        [:ul.word-list
         {:id "word-list"}
         [:li.word-list__empty
          (empty-state placeholder)]]]

       :else
       (list
        ;; The shell's corner ✕ leaves the screen; the heading stays for assistive technology only.
        [:h1.vocabulary__title.visually-hidden [:i18n :words/title]]
        [:div.vocabulary__list
         [:ul.word-list
          {:id "word-list"}
          (if placeholder
            [:li.word-list__empty (empty-state placeholder)]
            (concat (map word-list-item items)
                    (when more? [(sentinel)])))]]
        ;; The search field sits under the thumb. It closes the column rather than joining the footer: the
        ;; footer is fixed and stays behind an open keyboard, the field rides on top of it.
        [:form.vocabulary__search-bar
         {:autocapitalize "none"
          :autocorrect "off"
          :on {:submit [[:effect/prevent-default]]}}
         [:div.page-footer__action
          [:div.input
           [:span.input__search-icon]
           [:input#words-search.input__input-area.input__input-area--icon
            {:replicant/on-mount [[:effect/mobile-autofocus "words-search"]]
             :autocapitalize "none"
             :autocomplete   "off"
             :autocorrect    "off"
             :enterkeyhint   "search"
             :placeholder    [:i18n :words/search]
             :spellcheck     "false"
             :default-value  search
             :on             {:input [[:action/search-words [:event.target/value]]]}}]]]]
        [:footer.vocabulary__footer.page-footer
         [:div.page-footer__action
          [:button.vocabulary__start.big-button.green-button
           {:on {:click [[:action/go-to-lesson]]}}
           [:i18n :lesson/start]]]]))]))

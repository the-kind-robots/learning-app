(ns pages.words.view
  (:require
   [pages.words.presenter :as presenter]))


(defn- word-list-item
  [{:keys [id phrase? retention-band retention-color retention-percent translation value]}]
  [:li.word-item
   {:id (str "word-" id)}
   [:button.word-item__display
    {:type "button"
     :on   {:click [[:action/open-word-edit
                     {:id id :phrase? phrase? :value value :translation translation}]]}}
    [:div.word-item__retention
     {:style {:background-color retention-color}
      :title (str (case retention-band
                    :retention/excellent "Отлично запомнено"
                    :retention/good      "Хорошо изучено"
                    :retention/due       "Нужно повторить"
                    :retention/new       "Новое слово")
                  " (" retention-percent "%)")}]
    [:span.word-item__value
     {:lang "de" :class (when phrase? "word-item__value--phrase")}
     value]
    [:span.word-item__translation {:lang "ru"} translation]
    [:span.word-item__arrow.word-item__chevron "→"]]])


(defn- sentinel
  "The end of the loaded rows. It is keyed so appending rows in front of it
   reuses the node, and the observer watching it survives the render."
  []
  [:li.word-list__sentinel
   {:replicant/key        :word-list-sentinel
    :replicant/on-mount   [[:effect/observe-words-sentinel]]
    :replicant/on-unmount [[:effect/unobserve-words-sentinel]]}])


(defn- edit-dialog
  [{:keys [id phrase? renamed? save-disabled? spelling-taken? translation value]} in-collection?]
  [:dialog.word-edit-dialog.modal
   {:replicant/key id
    :replicant/on-mount [[:action/open-dialog]]
    :on {:close [[:action/close-word-edit]]}}
   [:form.word-edit-dialog__form
    {:autocapitalize "none"
     :autocorrect "off"
     :on {:submit [[:effect/prevent-default]
                   [:action/save-word {:id          id
                                       :translation [:event.form.field/value "translation"]
                                       :value       [:event.form.field/value "value"]}]]}}
    [:div.word-edit-dialog__field
     [:label.word-edit-dialog__label {:for "word-edit-value"} "Слово"]
     ;; A textarea for a phrase, whose text may run over several lines.
     [(if phrase?
        :textarea.word-edit-dialog__input.word-edit-dialog__input--multiline
        :input.word-edit-dialog__input)
      {:id            "word-edit-value"
       :name          "value"
       :rows          (when phrase? 2)
       :class         ["word-edit-dialog__input--word"
                       (when renamed? "word-edit-dialog__input--renamed")
                       (when spelling-taken? "word-edit-dialog__input--invalid")]
       :autocapitalize "none"
       :autocomplete  "off"
       :autocorrect   "off"
       :lang          "de"
       :spellcheck    "false"
       :aria-invalid  (when spelling-taken? "true")
       :default-value value
       :on            {:input [[:action/edit-spelling [:event.target/value]]]}}
      (when phrase? value)]
     (when (and renamed? (not spelling-taken?))
       [:p.word-edit-dialog__notice {:role "status"}
        (str "Сохранится как новое слово в тех же темах. «" value "» удалится вместе с прогрессом и примерами.")])
     (when spelling-taken?
       [:p.word-edit-dialog__error {:role "alert"} "Такое слово уже есть"])]
    [:div.word-edit-dialog__field
     [:label.word-edit-dialog__label {:for "word-edit-translation"} "Перевод"]
     ;; A textarea for both kinds: a translation may span several lines, and an `input` swallows line breaks.
     ;; Enter belongs to the text; Ctrl/Cmd+Enter and the save button submit, as on the add form.
     [:textarea.word-edit-dialog__input.word-edit-dialog__input--multiline
      {:id           "word-edit-translation"
       :name         "translation"
       :rows         (if phrase? 2 1)
       :autocapitalize "none"
       :autocomplete "off"
       :autocorrect  "off"
       :enterkeyhint "done"
       :lang         "ru"
       :placeholder  "Перевод"
       :spellcheck   "false"
       :autofocus    true
       :replicant/on-mount [[:action/move-cursor-to-end]]
       :on           {:input   [[:effect/autogrow-target]]
                      :keydown [[:action/submit-if-ctrl-enter
                                 {:key   [:event.keyboard/key]
                                  :ctrl? [:event.keyboard/ctrl?]
                                  :meta? [:event.keyboard/meta?]}]]}}
      ;; A textarea ignores a value attribute: the default rides as child text.
      translation]]
    [:div.word-edit-dialog__actions
     [:button.word-edit-dialog__save {:type "submit" :disabled save-disabled?} "Сохранить"]
     [:button.word-edit-dialog__cancel
      {:type "button"
       :on   {:click [[:action/close-word-edit]]}}
      "Отмена"]
     [:button.word-edit-dialog__delete
      {:type "button"
       :on   {:click [[:action/remove-word
                       {:confirm-message (if in-collection?
                                           (str "Убрать «" value "» из набора?")
                                           (str "Удалить «" value "» окончательно?"))
                        :id              id
                        :value           value}]]}}
      "Удалить слово"]]]])


(defn- empty-state
  [kind]
  [:div.vocabulary__empty-state
   [:p.vocabulary__empty-state-text
    (case kind
      :empty/first-run  "Слов пока нет"
      :empty/no-matches "Ничего не найдено")]
   [:p.vocabulary__empty-state-hint
    (case kind
      :empty/first-run  "Добавьте первое слово на главной странице"
      :empty/no-matches "Попробуйте другой запрос")]
   (case kind
     :empty/first-run  [:button.vocabulary__empty-state-cta
                        {:on {:click [[:action/go-to-home]]}}
                        "Добавить слово"]
     :empty/no-matches nil)])


(defn page
  [state]
  (let [{:keys [editing in-collection? items known? more? search vocabulary?] placeholder :empty-state}
        (presenter/page-props state)]
    ;; `data-vk-overlay`, as on home: the keyboard covers the lesson button fixed at the bottom while the
    ;; search bar, which closes the column, stands on the keyboard's inset.
    [:div.vocabulary
     {:data-vk-overlay true}
     (when editing (edit-dialog editing in-collection?))
     (cond
       (not known?)
       nil

       (not vocabulary?)
       [:div.vocabulary__list
        [:ul.word-list
         {:id "word-list"}
         [:li.word-list__empty.word-list__empty--no-words
          (empty-state placeholder)]]]

       :else
       (list
        ;; The shell's corner ✕ leaves the screen; the heading stays for assistive technology only.
        [:h1.vocabulary__title.visually-hidden "Мои слова"]
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
           [:input.input__input-area.input__input-area--icon
            {:autocapitalize "none"
             :autocomplete   "off"
             :autocorrect    "off"
             :enterkeyhint   "search"
             :placeholder    "Поиск"
             :spellcheck     "false"
             :default-value  search
             :on             {:input [[:action/search-words [:event.target/value]]]}}]]]]
        [:footer.vocabulary__footer.page-footer
         [:div.page-footer__action
          [:button.vocabulary__start.big-button.green-button
           {:on {:click [[:action/go-to-lesson]]}}
           "НАЧАТЬ УРОК"]]]))]))

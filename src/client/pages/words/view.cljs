(ns pages.words.view
  (:require
   [utils :as utils]))


(defn retention-text
  [level]
  (cond
    (>= level 80) "Отлично запомнено"
    (>= level 50) "Хорошо изучено"
    (>= level 20) "Нужно повторить"
    :else         "Новое слово"))


(defn- word-list-item
  [{:keys [id phrase? retention-level translation value]}]
  [:li.word-item
   {:id (str "word-" id)}
   [:button.word-item__display
    {:type "button"
     :on   {:click [[:action/open-word-edit
                     {:id id :phrase? phrase? :value value :translation translation}]]}}
    [:div.word-item__retention
     {:style {:background-color (utils/prozent->color retention-level)}
      :title (str (retention-text retention-level) " (" (int retention-level) "%)")}]
    [:span.word-item__value
     {:lang "de" :class (when phrase? "word-item__value--phrase")}
     value]
    [:span.word-item__translation {:lang "ru"} translation]
    [:span.word-item__arrow.word-item__chevron "→"]]])


(defn- edit-dialog
  [{:keys [id phrase? value translation]}]
  [:dialog.word-edit-dialog.modal
   {:replicant/key id
    :replicant/on-mount [[:action/open-dialog]]
    :on {:close [[:action/close-word-edit]]}}
   [:form.word-edit-dialog__form
    {:autocapitalize "none"
     :autocorrect "off"
     :on {:submit [[:effect/prevent-default]
                   [:action/save-word {:id id :translation [:event.form.field/value "translation"]}]]}}
    [:div.word-edit-dialog__inputs
     {:class (when phrase? "word-edit-dialog__inputs--phrase")}
     [:span.word-edit-dialog__value {:lang "de"} value]
     [:span.word-edit-dialog__arrow {:aria-hidden "true"} "→"]
     ;; A textarea for both kinds: a word's translation may span several lines
     ;; too (GH-365), and an `input` silently swallows the line breaks it is
     ;; given. Enter therefore belongs to the text here — Ctrl/Cmd+Enter and
     ;; the save button submit, as on the add form.
     [:textarea.word-edit-dialog__input.word-edit-dialog__input--multiline
      {:name         "translation"
       :rows         (if phrase? 2 1)
       :class        (when phrase? "word-edit-dialog__input--phrase")
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
      ;; textarea ignores a value attribute — the default rides as child text
      translation]]
    [:div.word-edit-dialog__actions
     [:button.word-edit-dialog__save {:type "submit"} "Сохранить"]
     [:button.word-edit-dialog__cancel
      {:type "button"
       :on   {:click [[:action/close-word-edit]]}}
      "Отмена"]
     [:button.word-edit-dialog__delete
      {:type "button"
       :on   {:click [[:action/remove-word {:id id :value value}]]}}
      "Удалить"]]]])


(defn page
  [state]
  (let [{:words/keys [items search editing]} state]
    [:div.vocabulary
     {:data-vk-overlay true}
     (when editing (edit-dialog editing))
     (if (empty? items)
       [:div.vocabulary__list
        [:ul.word-list
         {:id "word-list"}
         [:li.word-list__empty.word-list__empty--no-words
          [:div.vocabulary__empty-state
           [:p.vocabulary__empty-state-text "Слов пока нет"]
           [:p.vocabulary__empty-state-hint "Добавьте первое слово на главной странице"]
           [:button.vocabulary__empty-state-cta
            {:on {:click [[:action/go-to-home]]}}
            "Добавить слово"]]]]]
       (list
        [:header.vocabulary__header
         [:button.vocabulary__back
          {:on {:click [[:action/go-to-home]]}}
          "← Назад"]
         [:h1.vocabulary__title "Мои слова"]]
        [:form.vocabulary__search
         {:autocapitalize "none"
          :autocorrect "off"
          :on {:submit [[:effect/prevent-default]]}}
         [:div.input
          [:span.input__search-icon]
          [:input.input__input-area.input__input-area--icon
           {:autocapitalize "none"
            :autocomplete   "off"
            :autocorrect    "off"
            :enterkeyhint   "search"
            :placeholder    "Поиск"
            :spellcheck     "false"
            :default-value  (or search "")
            :on             {:input [[:action/search-words [:event.target/value]]]}}]]]
        [:div.vocabulary__list
         [:ul.word-list
          {:id "word-list"}
          (if (seq items)
            (for [word items] (word-list-item word))
            [:li.word-list__empty
             [:div.vocabulary__empty-state
              (if (and search (not= "" search))
                (list
                 [:p.vocabulary__empty-state-text "Ничего не найдено"]
                 [:p.vocabulary__empty-state-hint "Попробуйте другой запрос"])
                (list
                 [:p.vocabulary__empty-state-text "Слов пока нет"]
                 [:p.vocabulary__empty-state-hint "Добавьте первое слово на главной странице"]))]])]]
        [:footer.vocabulary__footer.page-footer
         [:div.page-footer__action
          [:button.vocabulary__start.big-button.green-button
           {:on {:click [[:action/go-to-lesson]]}}
           "НАЧАТЬ УРОК"]]]))]))

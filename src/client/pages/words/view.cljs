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
  [{:keys [id retention-level translation value]}]
  [:li.word-item
   {:id (str "word-" id)}
   [:button.word-item__display
    {:type "button"
     :on   {:click [[:action/open-word-edit {:id id :value value :translation translation}]]}}
    [:div.word-item__retention
     {:style {:background-color (utils/prozent->color retention-level)}
      :title (str (retention-text retention-level) " (" (int retention-level) "%)")}]
    [:span.word-item__value {:lang "de"} value]
    [:span.word-item__translation {:lang "ru"} translation]
    [:span.word-item__arrow.word-item__chevron "→"]]])


(defn- edit-dialog
  [{:keys [id value translation]}]
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
     [:span.word-edit-dialog__value {:lang "de"} value]
     [:span.word-edit-dialog__arrow {:aria-hidden "true"} "→"]
     [:input.word-edit-dialog__input
      {:name           "translation"
       :autocapitalize "none"
       :autocomplete   "off"
       :autocorrect    "off"
       :enterkeyhint   "done"
       :lang           "ru"
       :placeholder    "Перевод"
       :spellcheck     "false"
       :default-value  translation
       :autofocus      true
       :replicant/on-mount [[:action/move-cursor-to-end]]}]]
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


(defn- more-dialog
  []
  [:dialog.more-dialog.modal
   {:replicant/on-mount [[:action/open-dialog]]
    :on {:close [[:action/close-more-menu]]}}
   [:div.more-dialog__content
    [:button.more-dialog__item
     {:type "button"
      :on   {:click [[:effect/export-data]]}}
     "Экспорт данных"]
    [:label.more-dialog__item
     {:role "button"}
     [:input
      {:type   "file"
       :accept ".json,application/json"
       :style  {:display "none"}
       :on     {:change [[:effect/import-file]]}}]
     "Импорт данных"]
    [:button.more-dialog__item.more-dialog__item--cancel
     {:type "button"
      :on   {:click [[:action/close-more-menu]]}}
     "Отмена"]]])


(defn page
  [state]
  (let [{:words/keys [items search editing menu-open?]} state]
    [:div.vocabulary
     {:data-vk-overlay true}
     (when editing (edit-dialog editing))
     (when menu-open? (more-dialog))
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
         [:h1.vocabulary__title "Мои слова"]
         [:button.vocabulary__menu
          {:type  "button"
           :title "Дополнительно"
           :on    {:click [[:action/open-more-menu]]}}
          "⋮"]]
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


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
     (if phrase?
       [:textarea.word-edit-dialog__input.word-edit-dialog__input--phrase
        {:name         "translation"
         :rows         2
         :autocapitalize "none"
         :autocomplete "off"
         :autocorrect  "off"
         :enterkeyhint "done"
         :lang         "ru"
         :placeholder  "Перевод"
         :spellcheck   "false"
         :autofocus    true
         :replicant/on-mount [[:action/move-cursor-to-end]]
         :on           {:input [[:effect/autogrow-target]]}}
        ;; textarea ignores a value attribute — the default rides as child text
        translation]
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
         :replicant/on-mount [[:action/move-cursor-to-end]]}])]
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


(defn- empty-state
  [{:keys [cta hint text]}]
  [:div.vocabulary__empty-state
   [:p.vocabulary__empty-state-text text]
   [:p.vocabulary__empty-state-hint hint]
   (when cta
     [:button.vocabulary__empty-state-cta
      {:on {:click [[:action/go-to-home]]}}
      cta])])


(defn page
  [state]
  (let [{:words/keys [items search editing vocabulary?] placeholder :words/empty-state} state]
    [:div.vocabulary
     {:data-vk-overlay true}
     (when editing (edit-dialog editing))
     (if-not vocabulary?
       [:div.vocabulary__list
        [:ul.word-list
         {:id "word-list"}
         [:li.word-list__empty.word-list__empty--no-words
          (empty-state placeholder)]]]
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
            :default-value  search
            :on             {:input [[:action/search-words [:event.target/value]]]}}]]]
        [:div.vocabulary__list
         [:ul.word-list
          {:id "word-list"}
          (if placeholder
            [:li.word-list__empty (empty-state placeholder)]
            (for [word items] (word-list-item word)))]]
        [:footer.vocabulary__footer.page-footer
         [:div.page-footer__action
          [:button.vocabulary__start.big-button.green-button
           {:on {:click [[:action/go-to-lesson]]}}
           "НАЧАТЬ УРОК"]]]))]))

(ns views.vocabulary
  "Pure view functions for word-related UI. Data → Hiccup."
  (:require
   [clojure.string :as str]
   [utils :as utils]))


(defn retention-text
  "Returns human-readable retention level description."
  [level]
  (cond
    (>= level 80) "Отлично запомнено"
    (>= level 50) "Хорошо изучено"
    (>= level 20) "Нужно повторить"
    :else         "Новое слово"))


(defn word-list-item
  "Renders a single word item. Button loads edit form into the global
   <dialog id=\"word-edit-dialog\">, which opens on htmx afterSwap."
  [{:keys [id retention-level translation value]}]
  (let [item-id (str "word-" id)]
    [:li.word-item
     {:id item-id}
     [:button.word-item__display
      {:type      "button"
       :hx-get    (str "/words/" id "/edit")
       :hx-target "#modal"
       :hx-swap   "innerHTML"}
      [:div.word-item__retention
       {:style {:background-color (utils/prozent->color retention-level)}
        :title (str (retention-text retention-level) " (" (int retention-level) "%)")}]
      [:span.word-item__value {:lang "de"} value]
      [:span.word-item__translation {:lang "ru"} translation]
      [:span.word-item__arrow.word-item__chevron "→"]]]))


(defn edit-form
  "Form rendered inside #word-edit-dialog. Server pre-fills hx-* attrs
   so no runtime attribute wiring is needed."
  [{:keys [id value translation]}]
  (let [item-id  (str "word-" id)
        word-url (str "/words/" id)]
    [:form.word-edit-dialog__form
     {:hx-put    word-url
      :hx-target (str "#" item-id)
      :hx-swap   "outerHTML"
      :hx-on:htmx:after-request
      "if(event.detail.successful) this.closest('dialog').close()"}
     [:div.word-edit-dialog__inputs
      [:span.word-edit-dialog__value {:lang "de"} value]
      [:span.word-edit-dialog__arrow {:aria-hidden "true"} "→"]
      [:input.word-edit-dialog__input
       {:name           "translation"
        :autocapitalize "off"
        :autocomplete   "off"
        :autocorrect    "off"
        :lang           "ru"
        :placeholder    "Перевод"
        :value          translation
        :autofocus      true
        :hx-on:focus    "this.setSelectionRange(this.value.length, this.value.length)"}]]
     [:div.word-edit-dialog__actions
      [:button.word-edit-dialog__save  {:type "submit"} "Сохранить"]
      [:button.word-edit-dialog__cancel
       {:type        "button"
        :hx-on:click "this.closest('dialog').close()"}
       "Отмена"]
      [:button.word-edit-dialog__delete
       {:type        "button"
        :hx-delete   word-url
        :hx-swap     "none"
        :hx-confirm  (str "Удалить «" value "»?")}
       "Удалить"]]]))


(defn- word-items+sentinel
  "Builds word items and optional infinite-scroll sentinel."
  [{:keys [words-query show-more? words] :or {show-more? true}}]
  (when (seq words)
    (list
     (for [word words]
       (word-list-item word))
     (when show-more?
       [:li.word-list__sentinel
        {:aria-hidden "true"
         :hx-get      (utils/build-url
                       "/words"
                       (-> words-query
                           (assoc :fragment "chunk")
                           (update :offset + (:limit words-query))))
         :hx-swap     "outerHTML"
         :hx-target   "this"
         :hx-trigger  "intersect once"}]))))


(defn word-list
  "Renders the word list shell (UL) for HTMX swaps."
  [opts]
  [:ul.word-list
   {:id "word-list"}
   (or (word-items+sentinel opts)
       [:li.word-list__empty
        {:class (when (str/blank? (:search opts))
                  "word-list__empty--no-words")}
        (if (utils/non-blank (:search opts))
          [:div.vocabulary__empty-state
           [:p.vocabulary__empty-state-text "Ничего не найдено"]
           [:p.vocabulary__empty-state-hint "Попробуйте другой запрос"]]
          [:div.vocabulary__empty-state
           [:p.vocabulary__empty-state-text "Слов пока нет"]
           [:p.vocabulary__empty-state-hint "Добавьте первое слово на главной странице"]
           [:button.vocabulary__empty-state-cta
            {:hx-get "/home" :hx-push-url "true" :hx-swap "innerHTML" :hx-target "#app"}
            "Добавить слово"]])])])


(defn word-list-chunk
  "Renders a list chunk for infinite scroll (LI nodes only)."
  [opts]
  (word-items+sentinel opts))


(defn validation-error-inputs
  "Renders OOB error inputs for validation failures. Preserves user's values."
  [{:keys [value-blank? translation-blank?]}]
  (let [base-input-classes "home__add-form-input home__add-form-input--error"]
    (cond-> (list)
      value-blank?
      (conj
       [:input
        {:class          base-input-classes
         :id             "new-word-value"
         :name           "value"
         :autocapitalize "off"
         :autocomplete   "off"
         :autocorrect    "off"
         :data-ac-role   "word"
         :hx-get         "/dictionary-entries"
         :hx-trigger     "input changed delay:300ms"
         :hx-sync        "this:replace"
         :hx-target      "next [data-ac-role='list']"
         :hx-swap        "innerHTML"
         :hx-include     "this"
         :hx-swap-oob    "true"
         :lang           "de"
         :placeholder    "Новое слово"
         :required       true
         :value          ""}])

      translation-blank?
      (conj
       [:input
        {:class        base-input-classes
         :hx-swap-oob  "true"
         :id           "new-word-translation"
         :name         "translation"
         :data-ac-role "translation"
         :autocapitalize "off"
         :autocomplete "off"
         :autocorrect  "off"
         :lang         "ru"
         :placeholder  "Перевод"
         :required     true
         :value        ""}]))))


(defn- state-marker
  [empty?]
  [:span#vocabulary-state.vocabulary__state
   {:class  (if empty? "vocabulary__state--empty" "vocabulary__state--ready")
    :hidden true}])


(defn page
  "Words page. When empty? is true, shows empty state without header/search/footer.
   When :words is provided, renders the list inline (no extra load request)."
  [& {:keys [empty? search words show-more?]}]
  [:div.vocabulary
   {:data-vk-overlay true}
   [:span#vocabulary-state.vocabulary__state
    {:class  (if empty? "vocabulary__state--empty" "vocabulary__state--ready")
     :hidden true}]
   (if empty?
     [:div.vocabulary__list
      [:ul.word-list
       {:id "word-list"}
       [:li.word-list__empty.word-list__empty--no-words
        [:div.vocabulary__empty-state
         [:p.vocabulary__empty-state-text "Слов пока нет"]
         [:p.vocabulary__empty-state-hint "Добавьте первое слово на главной странице"]
         [:button.vocabulary__empty-state-cta
          {:hx-get "/home" :hx-push-url "true" :hx-swap "innerHTML" :hx-target "#app"}
          "Добавить слово"]]]]]
     (let [words-query   {:offset 0 :limit 10}
           base-list-url (utils/build-url "/words" words-query)]
       (list
        [:header.vocabulary__header
         [:button.vocabulary__back
          {:hx-get      "/home"
           :hx-push-url "true"
           :hx-swap     "innerHTML"
           :hx-target   "#app"}
          "← Назад"]
         [:h1.vocabulary__title "Мои слова"]]
        [:form.vocabulary__search
         [:div.input
          [:span.input__search-icon]
          [:input.input__input-area.input__input-area--icon
           {:autocomplete "off"
            :hx-get       base-list-url
            :placeholder  "Поиск"
            :hx-target    "#word-list"
            :hx-swap      "outerHTML"
            :hx-sync      "this:replace"
            :hx-trigger   "input changed delay:500ms, keyup[key=='Enter']"
            :name         "search"}]]]
        [:div.vocabulary__list
         (word-list
          {:words-query words-query
           :search      search
           :show-more?  show-more?
           :words       words})]
        [:footer.vocabulary__footer.page-footer
         [:div.page-footer__action
          [:button.vocabulary__start.big-button.green-button
           {:hx-get       "/lesson"
            :hx-indicator "#loader"
            :hx-push-url  "true"
            :hx-swap      "innerHTML"
            :hx-target    "#app"}
           "НАЧАТЬ УРОК"]]])))])

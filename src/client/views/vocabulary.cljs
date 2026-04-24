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
  "Form rendered inside #modal. Server pre-fills hx-* attrs
   so no runtime attribute wiring is needed."
  [{:keys [id value translation]}]
  (let [item-id  (str "word-" id)
        word-url (str "/words/" id)]
    [:form.word-edit-dialog__form
     {:id        (str "word-edit-form-" id)
      :hx-put    word-url
      :hx-target (str "#" item-id)
      :hx-swap   "outerHTML"
      :hx-on:htmx:after-request
      "if(event.detail.successful) this.closest('dialog').close()"}
     [:section.word-edit-dialog__word
      [:div.word-edit-dialog__word-heading
       [:h2.word-edit-dialog__word-title {:lang "de"} value]]
      [:label.word-edit-dialog__field
       [:span.word-edit-dialog__label "Перевод"]
       [:input.word-edit-dialog__input
        {:name           "translation"
         :autocapitalize "off"
         :autocomplete   "off"
         :autocorrect    "off"
         :lang           "ru"
         :placeholder    "Перевод"
         :value          translation
         :autofocus      true
         :hx-on:focus    "this.setSelectionRange(this.value.length, this.value.length)"}]]]]))


(defn- word-delete-button
  [{:keys [id value]}]
  [:button.word-edit-dialog__delete
   {:type        "button"
    :hx-delete   (str "/words/" id)
    :hx-swap     "none"
    :hx-confirm  (str "Удалить «" value "»?")}
   [:span.word-edit-dialog__delete-icon {:aria-hidden "true"} "⌫"]
   "Удалить слово"])


(defn word-footer
  [{:keys [id] :as word}]
  [:footer.word-edit-dialog__footer
   (word-delete-button word)
   [:div.word-edit-dialog__footer-actions
    [:button.word-edit-dialog__cancel
     {:type        "button"
      :hx-on:click "this.closest('dialog').close()"}
     "Отмена"]
    [:button.word-edit-dialog__save
     {:type "submit" :form (str "word-edit-form-" id)}
     "Сохранить"]]])


(defn- example-source-label
  [source]
  (case (or source "fetched")
    "user"    "Свой пример"
    "fetched" "Автоматический"
    "Пример"))


(defn- fetched-source?
  [source]
  (= "fetched" (or source "fetched")))


(defn- source-icon
  [source]
  (if (= "user" source) "✎" "⌕"))


(defn- example-badge
  [source]
  [:span.word-example__badge
   {:class (if (= "user" source)
             "word-example__badge--user"
             "word-example__badge--fetched")}
   [:span.word-example__badge-icon {:aria-hidden "true"} (source-icon source)]
   (example-source-label source)])


(defn- example-edit-card
  [{:keys [_id word-id value translation source]}]
  [:article.word-example.word-example--editing
   {:id (str "example-" _id)}
   [:header.word-example__header
    (example-badge source)]
   [:form.word-example__inline-edit
    {:hx-patch  (str "/examples/" _id)
     :hx-target "closest .word-examples"
     :hx-swap   "outerHTML"}
    [:label.word-example__field
     [:span.word-example__field-label "Пример"]
     [:textarea.word-example__textarea
      {:name     "value"
       :lang     "de"
       :required true
       :rows     2}
      value]]
    [:label.word-example__field
     [:span.word-example__field-label "Перевод"]
     [:input.word-example__input
      {:name     "translation"
       :lang     "ru"
       :required true
       :value    translation}]]
    [:div.word-example__edit-actions
     [:button.word-example__secondary
      {:type      "button"
       :hx-get    (str "/words/" word-id "/examples")
       :hx-target "closest .word-examples"
       :hx-swap   "outerHTML"}
      "Отмена"]
     [:button.word-example__save {:type "submit"} "Сохранить"]]]])


(defn- example-card
  [{:keys [_id source value translation] :as example} edit-id]
  (if (and (= "user" source) (= _id edit-id))
    (example-edit-card example)
    [:article.word-example
     {:id (str "example-" _id)}
     [:header.word-example__header
      (example-badge source)
      [:div.word-example__actions
       (when (fetched-source? source)
         [:button.word-example__icon-button.word-example__icon-button--refresh
          {:type       "button"
           :aria-label "Обновить автоматический пример"
           :title      "Обновить автоматический пример"
           :hx-put     (str "/examples/" _id "/refetch")
           :hx-target  "closest .word-examples"
           :hx-swap    "outerHTML"}
          "↻"])
       (when (= "user" source)
         [:button.word-example__icon-button
          {:type       "button"
           :aria-label "Редактировать пример"
           :title      "Редактировать пример"
           :hx-get     (str "/examples/" _id "/edit")
           :hx-target  "closest .word-examples"
           :hx-swap    "outerHTML"}
          "✎"])
       [:button.word-example__icon-button
        {:type       "button"
         :aria-label "Удалить пример"
         :title      "Удалить пример"
         :hx-delete  (str "/examples/" _id)
         :hx-target  "closest .word-examples"
         :hx-swap    "outerHTML"
         :hx-confirm "Удалить пример?"}
        "⌫"]]]
     [:p.word-example__value {:lang "de"} value]
     [:p.word-example__translation {:lang "ru"} translation]]))


(defn- fetch-text
  [{:keys [state last-error]}]
  (case state
    :ready       "Пример готов"
    :fetching    "Ищем пример"
    :failed      (str "Не удалось найти пример"
                      (when (utils/non-blank last-error)
                        (str ": " last-error)))
    :no-example  "Примера пока нет"
    "Пример"))


(defn example-fetch
  [{:keys [id]} fetch-state]
  [:article.word-example.word-example--ghost
   {:id (str "word-example-fetch-" id)}
   [:header.word-example__header
    (example-badge "fetched")]
   [:p.word-example__value "Автоматический пример удалён"]
   [:p.word-example__translation "Быстрый способ получить естественный пример."]
   [:p.word-examples__state (fetch-text fetch-state)]
   [:form
    {:hx-put    (str "/words/" id "/example-fetch")
     :hx-target (str "#word-example-fetch-" id)
     :hx-swap   "outerHTML"}
    [:button.word-example__ghost-button {:type "submit"}
     (if (= :failed (:state fetch-state))
       "Попробовать ещё раз"
       "Сгенерировать")]]])


(defn examples-panel
  [{:keys [word examples fetch-state edit-id]}]
  (let [{:keys [id]} word
        fetched?     (some #(fetched-source? (:source %)) examples)
        example-count (count examples)]
    [:section.word-examples
     {:id (str "word-examples-" id)}
     [:header.word-examples__header
      [:h3.word-examples__title
       "Примеры"
       [:span.word-examples__count (str " · " example-count)]]]
     (if (seq examples)
       [:div.word-examples__list
        (for [example examples]
          (example-card example edit-id))]
       [:p.word-examples__empty "Добавьте хотя бы один пример, чтобы слово лучше запоминалось."])
     (when-not fetched?
       (example-fetch word
                      (if (= :ready (:state fetch-state))
                        {:state :no-example}
                        fetch-state)))
     [:form.word-examples__add
      {:hx-post   (str "/words/" id "/examples")
       :hx-target (str "#word-examples-" id)
       :hx-swap   "outerHTML"}
      [:h4.word-examples__add-title
       [:span.word-examples__add-icon {:aria-hidden "true"} "+"]
       "Добавить свой пример"]
      [:input.word-examples__add-input
       {:name "value" :lang "de" :placeholder "Предложение на немецком" :required true}]
      [:input.word-examples__add-input
       {:name "translation" :lang "ru" :placeholder "Перевод на русский" :required true}]
      [:button.word-examples__add-button {:type "submit"} "Добавить"]]]))


(defn word-detail
  [{:keys [word examples fetch-state]}]
  [:div.word-detail
   [:header.word-detail__header
    [:span.word-detail__eyebrow "Редактировать слово"]
    [:button.word-detail__close
     {:type        "button"
      :aria-label  "Закрыть"
      :hx-on:click "this.closest('dialog').close()"}
     "×"]]
   [:div.word-detail__content
    (edit-form word)
    (examples-panel {:word word :examples examples :fetch-state fetch-state})]
   (word-footer word)])


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

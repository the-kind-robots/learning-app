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
  "Renders a single word item in the list."
  [{:keys [id retention-level translation value]} & {:keys [editing? oob?]}]
  (let [item-id (str "word-" id)]
    [:li.word-item
     (cond-> {:id item-id
              :hx-on:htmx:after-settle "matchMedia('(min-width: 700px)').matches && htmx.find(this, 'input')?.focus({preventScroll:true})"}
       editing? (assoc :class "word-item--editing")
       oob?     (assoc :hx-swap-oob "outerHTML"))
     (if editing?
       ;; Edit mode - show inputs
       [:form.word-item__form
        {:hx-put     (str "/words/" id)
         :hx-trigger "submit"
         :hx-swap    "outerHTML"
         :hx-target  (str "#" item-id)}
        [:div.word-item__inputs
         [:span.word-item__value {:lang "de"} value]
         [:span.word-item__arrow "→"]
        [:input.word-item__input
         {:name           "translation"
           :autocapitalize "off"
           :autocomplete   "off"
           :autocorrect    "off"
           :lang           "ru" :placeholder "Перевод" :value translation}]]
        [:div.word-item__actions
         [:button.word-item__save {:type "submit"} "Сохранить"]
         [:button.word-item__cancel
          {:type      "button"
           :hx-get    (str "/words/" id)
           :hx-target (str "#" item-id)
           :hx-swap   "outerHTML"
           :hx-trigger "click, keydown[key=='Escape'] from:body"}
          "Отмена"]
         [:button.word-item__delete
          {:type       "button"
           :hx-delete  (str "/words/" id)
           :hx-confirm (str "Удалить «" value "»?")
           :hx-trigger "click"
           :hx-include "closest .vocabulary"
           :hx-swap    "outerHTML"
           :hx-target  "#word-list"}
          "Удалить"]]]
       ;; Display mode - show text, tap to edit
       [:div.word-item__display
        {:hx-get      (str "/words/" id "?edit=true")
         :hx-vals     "js:{editing: document.querySelector('#word-list-editing-id')?.dataset.editingId || ''}"
         :hx-on:click "matchMedia('(min-width: 700px)').matches && this.scrollIntoView({block:'nearest'})"
         :hx-target   (str "#" item-id)
         :hx-swap     "outerHTML"}
        [:div.word-item__retention
         {:style {:background-color (utils/prozent->color retention-level)}
          :title (str (retention-text retention-level) " (" (int retention-level) "%)")}]
        [:span.word-item__value {:lang "de"} value]
        [:span.word-item__translation {:lang "ru"} translation]
        [:span.word-item__arrow.word-item__chevron "→"]])]))


(defn- word-items+sentinel
  "Builds word items and optional infinite-scroll sentinel."
  [{:keys [editing-id words-query show-more? words] :or { show-more? true}}]
  (when (seq words)
    (list
     (for [word words]
       (word-list-item word :editing? (= (:id word) editing-id)))
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


(defn editing-marker
  [editing-id & {:keys [oob?]}]
  [:span#word-list-editing-id
   (cond-> {:data-editing-id (or editing-id "")
            :hidden          true}
     oob? (assoc :hx-swap-oob "true"))])


(defn word-list
  "Renders the word list shell (UL) for HTMX swaps."
  [opts]
  (list
   (editing-marker (:editing-id opts))
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
             "Добавить слово"]])])]))


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
  (if empty?
    [:div.vocabulary
     (state-marker true)
     [:div.vocabulary__list
      [:ul.word-list
       {:id "word-list"}
       [:li.word-list__empty.word-list__empty--no-words
        [:div.vocabulary__empty-state
         [:p.vocabulary__empty-state-text "Слов пока нет"]
         [:p.vocabulary__empty-state-hint "Добавьте первое слово на главной странице"]
         [:button.vocabulary__empty-state-cta
          {:hx-get "/home" :hx-push-url "true" :hx-swap "innerHTML" :hx-target "#app"}
          "Добавить слово"]]]]]]
    (let [words-query   {:offset 0 :limit 10}
          base-list-url (utils/build-url "/words" words-query)]
      [:div.vocabulary
       (state-marker false)
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
          "НАЧАТЬ УРОК"]]]])))

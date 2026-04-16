(ns views.home
  "Home page view.")


(def add-panel-id
  "home-add-panel")


(def add-form-id
  "home-add-form")


(def home-add-panel-after-settle-script
  (str
   "if (event.target && event.target.id === 'home-add-form' && "
   "matchMedia('(pointer:fine)').matches) { "
   "var panel = this; "
   "setTimeout(function() { "
   "var input = htmx.find(panel, '#new-word-value'); "
   "if (input) input.focus(); "
   "}, 0); "
   "}"))


(defn- add-form
  [{:keys [oob?]}]
  [:form.home__add-form
   (cond-> {:id            add-form-id
            :hx-post       "/words"
            :hx-push-url   "false"
            :hx-swap       "none"
            :hx-disabled-elt "find button[type='submit']"
            :hx-disinherit "hx-disabled-elt"}
     oob? (assoc :hx-swap-oob (str "outerHTML:#" add-form-id)))
   [:fieldset.home__add-fieldset
    [:legend.home__add-legend
     "Добавить слово"]
    [:word-autocomplete
     [:div.home__add-form-row
      [:div.autocomplete
       [:label.home__add-form-label
        {:for "new-word-value"}
        "Слово (немецкий)"]
       [:input.home__add-form-input
        {:id             "new-word-value"
         :name           "value"
         :autocapitalize "off"
         :autocomplete   "off"
         :autocorrect    "off"
         :data-ac-role   "word"
         :hx-get         "/dictionary-entries"
         :hx-include     "this"
         :hx-sync        "this:replace"
         :hx-target      "next [data-ac-role='list']"
         :hx-trigger     "input changed delay:300ms"
         :hx-swap        "innerHTML"
         :required       true
         :lang           "de"
         :placeholder    "Новое слово"}]
       [:ul.suggestions
        {:data-ac-role "list"}]]
      [:span.home__add-form-arrow
       "→"]
      [:div.home__add-translation
       [:label.home__add-form-label
        {:for "new-word-translation"}
        "Перевод (русский)"]
       [:input.home__add-form-input
        {:id           "new-word-translation"
         :name         "translation"
         :autocapitalize "off"
         :autocomplete "off"
         :autocorrect  "off"
         :data-ac-role "translation"
         :lang         "ru"
         :placeholder  "Перевод"
         :required     true}]]]]

    [:button.home__add-form-submit.big-button.big-button--request-stable
     {:type "submit"}
     "ДОБАВИТЬ"]]])


(defn add-success
  [& {:keys [first-word?]}]
  (list
   (add-form {:oob? true})
   [:div
    {:hx-swap-oob (str "beforeend:#" add-panel-id)}
    [:div
     {:hx-ext "class-tools"
      :apply-parent-classes "add home__add--success, remove home__add--success:300ms"}]]
   (when first-word?
     (list
      [:button#home-words-button.home__words-button
       {:type        "button"
        :hx-swap-oob "true"
        :hx-get      "/words"
        :hx-push-url "true"
        :hx-swap     "innerHTML"
        :hx-target   "#app"}
       "Список слов"]
      [:footer#home-lesson-footer.home__footer.page-footer
       {:hx-swap-oob "true"}
       [:h2.home__lesson-title "Урок"]
       [:div.page-footer__action
        [:button.home__lesson-button.big-button.green-button
         {:hx-get       "/lesson"
          :hx-indicator "#loader"
          :hx-push-url  "true"
          :hx-swap      "innerHTML"
          :hx-target    "#app"}
         "НАЧАТЬ УРОК"]]]))))


(defn page
  [& {:keys [empty-vocab?]}]
  [:div.home
   [:header.home__intro
    [:h1.home__title
     "Главная"]
    [:p.home__subtitle
     "Быстро добавляйте слова и учите немецкий даже без сети."]]

   [:main.home__content
    [:section#home-add-panel.home__add
     {"hx-on:htmx:after-settle"
      home-add-panel-after-settle-script}
     [:header.home__add-header
      [:h2.home__panel-title
       "Быстрое добавление"]
      [:button#home-words-button.home__words-button
       {:type    "button"
        :hidden  empty-vocab?
        :hx-get  "/words"
        :hx-push-url "true"
        :hx-swap "innerHTML"
        :hx-target   "#app"}
       "Список слов"]]

     (add-form {:oob? false})]]

   [:footer#home-lesson-footer.home__footer.page-footer
    {:hidden empty-vocab?}
    [:h2.home__lesson-title
     "Урок"]
    [:div.page-footer__action
     [:button.home__lesson-button.big-button.green-button
      {:hx-get       "/lesson"
       :hx-indicator "#loader"
       :hx-push-url  "true"
       :hx-swap      "innerHTML"
       :hx-target    "#app"}
      "НАЧАТЬ УРОК"]]]])

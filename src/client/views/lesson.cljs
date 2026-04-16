(ns views.lesson
  "Lesson page views."
  (:require
   [presenter.lesson :as presenter.lesson]))


(defn progress
  [state attrs]
  (let [value (presenter.lesson/progress-props state)]
    [:div#lesson-progress.lesson__progress-shell
     attrs
     [:div.lesson__progress
      {:role           "progressbar"
       :aria-label     "Прогресс урока"
       :aria-valuemin  0
       :aria-valuemax  100
       :aria-valuenow  value
       :aria-valuetext (str value "%")}
      [:div#lesson-progress-bar.lesson__progress-value
       {:style {:width (str value "%")}}]]]))


(defn challenge
  "Renders a challenge - prompt text and instruction."
  [state & {:as attrs}]
  (let [{:keys [prompt is-example? gloss-mismatch?]} (presenter.lesson/challenge-props state)]
    [:div#lesson-challenge.lesson__challenge
     attrs
     [:h2.lesson__prompt
      {:lang "ru"}
      prompt]
     (when gloss-mismatch?
       [:p.lesson__gloss-warning "Перевод слова может быть неточным"])
     [:p.lesson__instruction
      (if is-example?
        "Переведите предложение на немецкий"
        "Переведите слово на немецкий")]]))


(defn input
  []
  [:footer#lesson-footer
   ;; User focuses input manually (avoids auto keyboard open/layout jumps).
   [:form.lesson__footer.lesson__footer--input.page-footer
    {:hx-post "/lesson/answer" :hx-target "#lesson-footer" :hx-swap "outerHTML"}
    [:label.lesson__input-label
     {:for "lesson-answer"}
     "Ответ на немецком"]
    [:textarea.lesson__input
     {:id          "lesson-answer"
      :name        "answer"
      :rows        4
      :placeholder "Введите перевод..."
      :maxlength   1000
      :lang        "de"
      :hx-on:keydown
      "if(event.key==='Enter' && (event.ctrlKey || event.metaKey)){event.preventDefault(); this.form.requestSubmit();}"}]
    [:div.lesson__action.page-footer__action
     [:button.big-button {:type "submit"} "ПРОВЕРИТЬ"]]]])


(defn- success
  [{:keys [correct-answer finished?]}]
  [:footer#lesson-footer
   {:tabindex "-1"
    :hx-on:htmx:afterSettle
    "var btn = this.querySelector('#lesson-next') || this.querySelector('#lesson-finish'); if(btn){btn.focus();}"
   }
   [:div.lesson__footer.lesson__footer--success.page-footer
    [:div.lesson__answer
     [:h3.lesson__answer-header "Правильно!"]
     [:p.lesson__answer-body {:lang "de"} correct-answer]]
    [:form
     (if finished?
       {:hx-delete "/lesson" :hx-target "#app" :hx-swap "innerHTML"}
       {:hx-post "/lesson/next" :hx-target "#lesson-footer" :hx-swap "outerHTML"})
     [:div.lesson__action.page-footer__action
      [:button.big-button
       {:id (if finished? "lesson-finish" "lesson-next") :type "submit"}
       (if finished? "ЗАКОНЧИТЬ" "ДАЛЕЕ")]]]]])


(defn- error
  [{:keys [correct-answer user-answer]}]
  [:footer#lesson-footer
   {:tabindex "-1"
    :hx-on:htmx:afterSettle "var btn = this.querySelector('#lesson-next'); if(btn){btn.focus();}"}
   [:div.lesson__footer.lesson__footer--error.page-footer
    [:div.lesson__answer
     [:h3.lesson__answer-header "Ваш ответ:"]
     [:p.lesson__answer-body {:lang "de"} (or user-answer "")]
     [:h3.lesson__answer-header "Правильный ответ:"]
     [:p.lesson__answer-body {:lang "de"} correct-answer]]
    [:form {:hx-post "/lesson/next" :hx-target "#lesson-footer" :hx-swap "outerHTML"}
     [:div.lesson__action.page-footer__action
      [:button.big-button
       {:id "lesson-next" :type "submit"}
       "ДАЛЕЕ"]]]]])


(defn footer
  "Renders footer based on props. Returns nil if no props (show input instead)."
  [state]
  (when-let [props (presenter.lesson/footer-props state)]
    (case (:variant props)
      :success (success props)
      :error   (error props)
      nil)))


(defn empty-state
  []
  [:div.lesson
   [:h1.lesson__title
    "Урок"]
   [:main.lesson__body
    [:div.lesson__empty
     [:div.lesson__empty-state
      [:p.lesson__empty-state-text "Нет слов для урока"]
      [:p.lesson__empty-state-hint "Добавьте слова, чтобы начать обучение"]
      [:button.lesson__empty-state-cta
       {:type        "button"
        :hx-get      "/words"
        :hx-push-url "true"
        :hx-target   "#app"
        :hx-swap     "innerHTML"}
       "Добавить слова"]]]]])


(defn page
  "Render the lesson page. Takes lesson state, uses presenter for props."
  [state]
  [:div.lesson
   [:h1.lesson__title
    "Урок"]
   [:header.lesson__header
    (progress state {})
    [:button.lesson__cancel
     {:id          "lesson-cancel"
      :type        "button"
      :aria-label  "Закрыть урок"
      :hx-delete   "/lesson"
      :hx-push-url "true"
      :hx-target   "#app"
      :hx-swap     "innerHTML"}
     [:svg
      {:viewBox "0 0 24 24"
       :height  "18"
       :width   "18"}
      [:path
       {:fill "currentColor"
        :d "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"}]]]
   ]
   [:main.lesson__body
    (challenge state)]
   (or
    (footer state)
    (input))])

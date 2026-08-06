(ns pages.lesson.view
  (:require
   [pages.lesson.presenter :as presenter]))


(defn- answer-body
  [{:keys [correct-answer correct-answer-segments]}]
  (if (seq correct-answer-segments)
    (into
     [:p.lesson__answer-body {:lang "de"}]
     (interpose " ")
     (for [{:keys [type text dictionary-form translation word-index]} correct-answer-segments]
       (if (= :annotated-word type)
         [:button.lesson__answer-token
          {:type "button"
           :data-word-index word-index
           :on   {:click [[:action/view-token-info
                           {:dictionary-form dictionary-form
                            :translation     translation}]]}}
          text]
         text)))
    [:p.lesson__answer-body {:lang "de"} correct-answer]))


(defn- input-footer
  []
  [:footer#lesson-footer
   {:replicant/key      :input
    :replicant/on-mount [[:action/focus-lesson-input]]}
   [:form.lesson__footer.lesson__footer--input.page-footer
    {:autocapitalize "none"
     :autocorrect "off"
     :on {:submit [[:effect/prevent-default]
                   [:action/check-answer [:event.form.field/value "answer"]]]}}
    [:label.lesson__input-label {:for "lesson-answer"} "Ответ на немецком"]
    [:textarea.lesson__input
     {:id          "lesson-answer"
      :name        "answer"
      :rows        4
      :autocapitalize "none"
      :autocomplete "off"
      :autocorrect "off"
      :enterkeyhint "done"
      :placeholder "Введите перевод..."
      :maxlength   1000
      :lang        "de"
      :spellcheck  "false"
      :on          {:keydown [[:action/submit-if-ctrl-enter
                               {:key   [:event.keyboard/key]
                                :ctrl? [:event.keyboard/ctrl?]}]]}}]
    [:div.lesson__action.page-footer__action
     [:button.big-button {:type "submit"} "ПРОВЕРИТЬ"]]]])


(defn- success-footer
  [{:keys [finished?] :as props}]
  [:footer#lesson-footer
   {:replicant/key      :success
    :replicant/on-mount [[:action/focus-continue-button
                          (if finished? "#lesson-finish" "#lesson-next")]]}
   [:div.lesson__footer.lesson__footer--success.page-footer
    [:div.lesson__answer
     [:h3.lesson__answer-header "Правильно!"]
     (answer-body props)]
    [:form
     {:on {:submit [[:effect/prevent-default]]}}
     [:div.lesson__action.page-footer__action
      [:button.big-button
       {:id   (if finished? "lesson-finish" "lesson-next")
        :type "button"
        :on   {:click   (if finished?
                          [[:action/finish-lesson]]
                          [[:action/next-trial]])
               :keydown [[:action/click-if-enter [:event.keyboard/key]]]}}
       (if finished? "ЗАКОНЧИТЬ" "ДАЛЕЕ")]]]]])


(defn- error-footer
  [{:keys [user-answer] :as props}]
  [:footer#lesson-footer
   {:replicant/key      :error
    :replicant/on-mount [[:action/focus-continue-button "#lesson-next"]]}
   [:div.lesson__footer.lesson__footer--error.page-footer
    [:div.lesson__answer
     [:h3.lesson__answer-header "Ваш ответ:"]
     [:p.lesson__answer-body {:lang "de"} (or user-answer "")]
     [:h3.lesson__answer-header "Правильно:"]
     (answer-body props)]
    [:div.lesson__action.page-footer__action
     [:button.big-button
      {:id   "lesson-next"
       :type "button"
       :on   {:click   [[:action/next-trial]]
              :keydown [[:action/click-if-enter [:event.keyboard/key]]]}}
      "ДАЛЕЕ"]]]])


(defn token-info-dialog
  [{:keys [dictionary-form translation state]}]
  [:dialog#token-info-dialog
   {:replicant/on-mount [[:action/open-dialog]]
    :on {:close [[:action/close-modal]]}}
   [:div.token-card
    (when (= state :known-with-translation) {:data-dismiss "900"})
    [:p.token-card__word {:lang "de"} dictionary-form]
    [:p.token-card__translation {:lang "ru"} translation]
    (case state
      :known-with-translation [:p.token-card__state "✓ В словаре"]
      :known-missing-translation [:button.token-card__button
                                  {:type "button"
                                   :on   {:click [[:action/save-lesson-word
                                                   {:dictionary-form dictionary-form
                                                    :translation     translation}]]}}
                                  "+ ДОБАВИТЬ ПЕРЕВОД"]
      :unknown-word [:button.token-card__button
                     {:type "button"
                      :on   {:click [[:action/save-lesson-word
                                      {:dictionary-form dictionary-form
                                       :translation     translation}]]}}
                     "+ В СЛОВАРЬ"])]])


(defn progress
  [lesson-state attrs]
  (let [value (presenter/progress-props lesson-state)]
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
  [lesson-state]
  (let [{:keys [prompt is-example?]} (presenter/challenge-props lesson-state)]
    [:div#lesson-challenge.lesson__challenge
     [:h2.lesson__prompt {:lang "ru"} prompt]
     [:p.lesson__instruction
      (if is-example?
        "Переведите предложение на немецкий"
        "Переведите слово на немецкий")]]))


(defn empty-state
  []
  [:div.lesson
   [:h1.lesson__title "Урок"]
   [:main.lesson__body
    [:div.lesson__empty
     [:div.lesson__empty-state
      [:p.lesson__empty-state-text "Нет слов для урока"]
      [:p.lesson__empty-state-hint "Добавьте слова, чтобы начать обучение"]
      [:button.lesson__empty-state-cta
       {:type "button"
        :on   {:click [[:action/go-to-words]]}}
       "Добавить слова"]]]]])


(defn page
  [state]
  (if (:lesson/empty? state)
    (empty-state)
    (let [lesson-state (:lesson/state state)]
      [:div.lesson
       [:h1.lesson__title "Урок"]
       [:header.lesson__header
        (progress lesson-state {})
        [:button.lesson__cancel
         {:id         "lesson-cancel"
          :type       "button"
          :aria-label "Закрыть урок"
          :on         {:click [[:action/cancel-lesson]]}}
         [:svg {:viewBox "0 0 24 24" :height "18" :width "18"}
          [:path
           {:fill "currentColor"
            :d
            "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"}]]]]
       [:main.lesson__body
        (challenge lesson-state)]
       (let [footer-props (presenter/footer-props lesson-state)]
         (if footer-props
           (case (:variant footer-props)
             :success (success-footer footer-props)
             :error   (error-footer footer-props))
           (input-footer)))])))

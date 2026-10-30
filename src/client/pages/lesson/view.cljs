(ns pages.lesson.view
  (:require
   [pages.lesson.presenter :as presenter]))


(defn- answer-body
  [{:keys [correct-answer correct-answer-segments]}]
  (if (seq correct-answer-segments)
    (into
     [:p.lesson__answer-body {:lang "de"}]
     (interpose " ")
     (for [{:keys [annotated? expanded? text word-index]} correct-answer-segments]
       (if annotated?
         [:button.lesson__answer-token
          {:type "button"
           :data-word-index word-index
           :aria-haspopup "dialog"
           :aria-controls "popover"
           :aria-expanded (str expanded?)
           :on {:click [[:action/open-answer-hint word-index]]}}
          text]
         text)))
    [:p.lesson__answer-body {:lang "de"} correct-answer]))


(defn- input-footer
  [{:keys [correct-answer max-length]}]
  [:footer#lesson-footer
   {:replicant/key      :input
    :replicant/on-mount [[:action/focus-lesson-input]]}
   [:form.lesson__footer.lesson__footer--input.page-footer
    {:autocapitalize "none"
     :autocorrect "off"
     :on {:submit [[:effect/prevent-default]
                   [:action/check-answer [:event.form.field/value "answer"]]]}}
    [:answer-field.lesson__input
     {:id             "lesson-answer"
      :name           "answer"
      :autocapitalize "none"
      :autocomplete   "off"
      :autocorrect    "off"
      :enterkeyhint   "done"
      :aria-label     [:i18n :lesson/answer]
      :bank-label     [:i18n :lesson/tiles]
      :correct-answer correct-answer
      :hint-label     [:i18n :lesson/tiles-hint]
      :maxlength      max-length
      :placeholder    [:i18n :lesson/answer-placeholder]
      :spellcheck     "false"
      :tray-label     [:i18n :lesson/tray]
      :lang           "de"
      :on             {:keydown [[:action/submit-if-ctrl-enter
                                  {:key   [:event.keyboard/key]
                                   :ctrl? [:event.keyboard/ctrl?]}]]}}]
    [:div.lesson__action.page-footer__action
     [:button.big-button {:type "submit"} [:i18n :lesson/check]]]]])


(defn- success-footer
  [{:keys [finished?] :as props}]
  [:footer#lesson-footer
   {:replicant/key      :success
    :replicant/on-mount [[:action/focus-continue-button
                          (if finished? "#lesson-finish" "#lesson-next")]]}
   [:div.lesson__footer.lesson__footer--success.page-footer
    [:div.lesson__answer
     [:h3.lesson__answer-header [:i18n :lesson/correct]]
     (answer-body props)]
    [:form
     {:on {:submit [[:effect/prevent-default]]}}
     [:div.lesson__action.page-footer__action
      [:button.big-button
       {:id   (if finished? "lesson-finish" "lesson-next")
        :type "button"
        ;; A native button already clicks on Enter; a keydown handler clicking it again advanced the lesson twice.
        :on   {:click (if finished?
                        [[:action/go-to-home]]
                        [[:action/next-trial]])}}
       (if finished? [:i18n :lesson/finish] [:i18n :lesson/next])]]]]])


(defn- error-footer
  [{:keys [user-answer] :as props}]
  [:footer#lesson-footer
   {:replicant/key      :error
    :replicant/on-mount [[:action/focus-continue-button "#lesson-next"]]}
   [:div.lesson__footer.lesson__footer--error.page-footer
    [:div.lesson__answer
     (when user-answer
       (list
        [:h3.lesson__answer-header [:i18n :lesson/your-answer]]
        [:p.lesson__answer-body {:lang "de"} user-answer]))
     [:h3.lesson__answer-header [:i18n :lesson/correct-answer]]
     (answer-body props)]
    [:div.lesson__action.page-footer__action
     [:button.big-button
      {:id   "lesson-next"
       :type "button"
       :on   {:click [[:action/next-trial]]}}
      [:i18n :lesson/next]]]]])


(defn- hint-card
  [{:keys [data-dismiss translation word word-index word-state]}]
  [:div.token-card
   (cond-> {}
     data-dismiss (assoc :data-dismiss data-dismiss))
   [:p.token-card__word {:lang "de"} word]
   [:p.token-card__translation translation]
   (when (= :word-state/known-with-translation word-state)
     [:p.token-card__state [:i18n :lesson/in-dictionary]])
   (when-let [action-label (case word-state
                             :word-state/known-missing-translation [:i18n :lesson/add-translation]
                             :word-state/unknown-word [:i18n :lesson/add-to-dictionary]
                             nil)]
     [:button.token-card__button
      {:type "button"
       :on   {:click [[:action/save-lesson-word
                       {:dictionary-form word
                        :translation     translation
                        :word-index      word-index}]]}}
      action-label])])


(defn answer-hint-popover
  "Popover shell anchored to the clicked answer word: the Popover API gives light-dismiss and top-layer
   rendering; `pages.lesson.popover` opens and re-positions it."
  [props]
  [:div#popover.popover
   {:popover "auto"
    :replicant/on-update [[:action/reposition-token-popover]]}
   [:div#popover-content (hint-card props)]
   [:div#popover-arrow.popover__arrow]])


(defn progress
  [lesson-state attrs]
  (let [value (presenter/progress-props lesson-state)]
    [:div#lesson-progress.lesson__progress-shell
     attrs
     [:div.lesson__progress
      {:role           "progressbar"
       :aria-label     [:i18n :lesson/progress]
       :aria-valuemin  0
       :aria-valuemax  100
       :aria-valuenow  value
       :aria-valuetext (str value "%")}
      [:div#lesson-progress-bar.lesson__progress-value
       {:style {:width (str value "%")}}]]]))


(defn challenge
  [lesson-state]
  (let [{:keys [kind prompt]} (presenter/challenge-props lesson-state)]
    [:div#lesson-challenge.lesson__challenge
     [:h2.lesson__prompt prompt]
     [:p.lesson__instruction
      (case kind
        :trial/example [:i18n :lesson/translate-sentence]
        :trial/phrase  [:i18n :lesson/translate-phrase]
        :trial/word    [:i18n :lesson/translate-word])]]))


(defn empty-state
  []
  [:div.lesson
   [:h1.lesson__title.visually-hidden [:i18n :lesson/title]]
   [:main.lesson__body
    [:div.lesson__empty
     [:div.lesson__empty-state
      [:p.lesson__empty-state-text [:i18n :lesson/empty]]
      [:p.lesson__empty-state-hint [:i18n :lesson/empty-hint]]
      [:button.lesson__empty-state-cta
       {:type "button"
        :on   {:click [[:action/go-to-words]]}}
       [:i18n :lesson/add-words]]]]]])


(defn page
  [state]
  (cond
    (:lesson/empty? state)
    (empty-state)

    ;; Opened before memory is loaded: the lesson is drawn once it is, and until then there is no trial.
    (nil? (:lesson/state state))
    [:div.lesson
     [:h1.lesson__title.visually-hidden [:i18n :lesson/title]]]

    :else
    (let [lesson-state (:lesson/state state)
          answer-hint  (presenter/answer-hint-props state)]
      [:div.lesson
       (when answer-hint (answer-hint-popover answer-hint))
       [:h1.lesson__title.visually-hidden [:i18n :lesson/title]]
       ;; The lesson is closed by the shell's corner ✕ (`:action/close-screen`).
       [:header.lesson__header
        (progress lesson-state {})]
       [:main.lesson__body
        (challenge lesson-state)]
       (let [footer-props (presenter/footer-props state)]
         (if footer-props
           (case (:variant footer-props)
             :success (success-footer footer-props)
             :error   (error-footer footer-props))
           (input-footer (presenter/input-props state))))])))

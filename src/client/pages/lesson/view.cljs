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
    [:p.lesson__answer-body
     {:lang "de"}
     correct-answer]))


(defn- input-footer
  [{:keys [correct-answer max-length]}]
  [:footer#lesson-footer
   {:replicant/key      :input
    :replicant/on-mount [[:action/focus-lesson-input]
                         [:effect/after-paint [[:effect/enter :action/start-answer]]]]}
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
     [:button.big-button
      {:type "submit"}
      [:i18n :lesson/check]]]]])


(defn- success-footer
  [{:keys [finished?] :as props}]
  [:footer#lesson-footer
   {:replicant/key      :success
    :replicant/on-mount [[:action/focus-continue-button
                          (if finished? "#lesson-again" "#lesson-next")]]}
   [:div.lesson__footer.lesson__footer--success.page-footer
    [:div.lesson__answer
     ;; The way home sits beside the verdict, so the main button stands where «ДАЛЕЕ» does.
     [:div.lesson__answer-title
      [:h3.lesson__answer-header
       [:i18n :lesson/correct]]
      (when finished?
        [:button.lesson__finish
         {:id   "lesson-finish"
          :type "button"
          :on   {:click [[:action/go-to-home]]}}
         [:i18n :lesson/finish]])]
     (answer-body props)]
    [:form
     {:on {:submit [[:effect/prevent-default]]}}
     [:div.lesson__action.page-footer__action
      ;; A native button already clicks on Enter; a keydown handler clicking it again advanced the lesson twice.
      (if finished?
        [:button.big-button
         {:id   "lesson-again"
          :type "button"
          :on   {:click [[:action/next-lesson]]}}
         [:i18n :lesson/again]]
        [:button.big-button
         {:id   "lesson-next"
          :type "button"
          :on   {:click [[:action/next-trial]]}}
         [:i18n :lesson/next]])]]]])


(defn- error-footer
  [{:keys [user-answer] :as props}]
  [:footer#lesson-footer
   {:replicant/key      :error
    :replicant/on-mount [[:action/focus-continue-button "#lesson-next"]]}
   [:div.lesson__footer.lesson__footer--error.page-footer
    [:div.lesson__answer
     (when user-answer
       (list
        [:h3.lesson__answer-header
         [:i18n :lesson/your-answer]]
        [:p.lesson__answer-body.lesson__answer-body--yours
         {:lang "de"}
         user-answer]))
     [:h3.lesson__answer-header
      [:i18n :lesson/correct-answer]]
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
   [:p.token-card__word
    {:lang "de"}
    word]
   [:p.token-card__translation translation]
   (when (= :word-state/known-with-translation word-state)
     [:p.token-card__state
      [:i18n :lesson/in-dictionary]])
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
   [:div#popover-content
    (hint-card props)]
   [:div#popover-arrow.popover__arrow]])


(defn progress
  [state attrs]
  (let [value (presenter/progress-props state)]
    [:div#lesson-progress.lesson__progress-shell
     attrs
     [:div.lesson__progress
      {:role           "progressbar"
       :aria-label     [:i18n :lesson/progress]
       :aria-valuemin  0
       :aria-valuemax  100
       :aria-valuenow  value
       :aria-valuetext (str value "%")}
      [:div#lesson-progress-bar.lesson__progress-value {:style {:transform (str "translateX(" (- value 100) "%)")}}]]]))


(defn challenge
  [{:keys [kind prompt]} attrs]
  [:div.lesson__challenge
   attrs
   [:div.lesson__task
    [:p.lesson__instruction
     (case kind
       :trial/example [:i18n :lesson/translate-sentence]
       :trial/phrase  [:i18n :lesson/translate-phrase]
       :trial/word    [:i18n :lesson/translate-word])]
    ;; The language picks the hyphenation of a word too long for its line.
    [:h2.lesson__prompt
     {:lang "ru"}
     prompt]]])


(defn- past-footer
  "The panel of a trial left behind, along the bottom over the current trial's panel."
  [{:keys [attempts current-page finished? first-try?] :as props}]
  [:div.lesson__footer.lesson__footer--success.lesson__footer--past.page-footer
   [:div.lesson__answer
    ;; Once the lesson is over, the same pair as on its final trial.
    [:div.lesson__answer-title
     [:h3.lesson__answer-header
      (if first-try?
        [:i18n :lesson/correct]
        [:i18n :lesson/attempts {:count attempts}])]
     (when finished?
       [:button.lesson__finish
        {:type "button"
         :on   {:click [[:action/go-to-home]]}}
        [:i18n :lesson/finish]])]
    (answer-body props)]
   [:div.lesson__action.page-footer__action
    (if finished?
      [:button.big-button
       {:type "button"
        :on   {:click [[:action/next-lesson]]}}
       [:i18n :lesson/again]]
      [:button.big-button
       {:type "button"
        :on   {:click [[:action/view-trial current-page current-page]]}}
       [:i18n :lesson/next]])]])


(defn empty-state
  []
  [:div.lesson
   [:h1.lesson__title.visually-hidden
    [:i18n :lesson/title]]
   [:main.lesson__body
    [:div.lesson__empty
     [:div.lesson__empty-state
      [:p.lesson__empty-state-text
       [:i18n :lesson/empty]]
      [:p.lesson__empty-state-hint
       [:i18n :lesson/empty-hint]]
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
     [:h1.lesson__title.visually-hidden
      [:i18n :lesson/title]]]

    :else
    (let [answer-hint (presenter/answer-hint-props state)
          {:keys [back? current-page current-shown? first-shown? page pages previous-page viewed-answer]}
          (presenter/strip-props state)]
      [:div.lesson
       (when answer-hint (answer-hint-popover answer-hint))
       [:h1.lesson__title.visually-hidden
        [:i18n :lesson/title]]
       ;; The lesson is closed by the shell's corner ✕ (`:action/close-screen`).
       [:header.lesson__header
        ;; A finger swipes the strip; a mouse has this arrow, so it shows on fine pointers only.
        (when back?
          [:button.lesson__back
           {:type       "button"
            :aria-label [:i18n :lesson/previous-trial]
            :disabled   first-shown?
            :on         {:click [[:action/view-trial previous-page current-page]]}}
           "‹"])
        (progress state {})]
       [:main.lesson__body
        ;; The trials answered, then the current one; `pages.lesson.page-strip` scrolls to `page`.
        (into
         [:page-strip.lesson__strip
          {:page page
           :on   {:pagechange [[:action/view-trial [:event/page] current-page]]}}]
         (for [{:keys [current? shown?] :as props} pages]
           [:section.lesson__page
            {:replicant/key [current? (:page props)]
             :aria-hidden   (not shown?)}
            (challenge props (when current? {:id "lesson-challenge"}))]))]
       ;; Only the challenge moves with the strip: a past trial's answer is a strip along the bottom; the
       ;; current panel, hidden meanwhile, keeps its place and its typed answer.
       [:div.lesson__panel
        [:div.lesson__panel-current
         {:inert (not current-shown?)}
         (let [footer-props (presenter/footer-props state)]
           (if footer-props
             (case (:variant footer-props)
               :success (success-footer footer-props)
               :error   (error-footer footer-props))
             (input-footer (presenter/input-props state))))]
        (when viewed-answer
          (past-footer viewed-answer))]])))

(ns pages.lesson.view
  (:require
   [application.pages :as pages]
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


(defn- next-button
  "The main button once the current trial is answered, and over a trial left behind: «ДАЛЕЕ», or «ЕЩЁ УРОК» once
   the lesson is over."
  [finished?]
  ;; A native button already clicks on Enter; a keydown handler clicking it again advanced the lesson twice.
  [:button.big-button
   {:id   "lesson-main-button"
    :type "button"
    :on   {:click [[:action/press-next]]}}
   (if finished?
     [:i18n :lesson/again]
     [:i18n :lesson/next])])


;; The footers are the current trial's. Over a trial left behind, a footer's text is inert, under that trial's
;; answer, and its main button is `next-button`: the same element as its own wherever it can be, so paging keeps
;; the focus on it.


(defn- input-footer
  [{:keys [correct-answer current-shown? max-length]}]
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
      :inert          (not current-shown?)
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
     ;; Keyed apart from the button over a trial left behind: back on this trial, the focus goes with that one, so
     ;; a second `Enter` does not check an empty answer.
     (if current-shown?
       [:button.big-button
        {:replicant/key :check
         :id   "lesson-main-button"
         :type "submit"}
        [:i18n :lesson/check]]
       ;; A trial waiting for its answer: the lesson is not over.
       (next-button false))]]])


(defn- success-footer
  [{:keys [current-shown? finished?] :as props}]
  [:footer#lesson-footer
   {:replicant/key      :success
    :replicant/on-mount [[:effect/focus "lesson-main-button"]]}
   [:div.lesson__footer.lesson__footer--success.page-footer
    [:div.lesson__answer
     {:inert (not current-shown?)}
     [:h3.lesson__answer-header
      [:i18n :lesson/correct]]
     (answer-body props)]
    [:form
     {:on {:submit [[:effect/prevent-default]]}}
     [:div.lesson__action.page-footer__action
      (next-button finished?)]]]])


(defn- error-footer
  [{:keys [current-shown? user-answer] :as props}]
  [:footer#lesson-footer
   {:replicant/key      :error
    :replicant/on-mount [[:effect/focus "lesson-main-button"]]}
   [:div.lesson__footer.lesson__footer--error.page-footer
    [:div.lesson__answer
     {:inert (not current-shown?)}
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
     (next-button false)]]])


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
    ;; The language picks the hyphenation of a word too long for its line.
    [:h2.lesson__prompt
     {:lang "ru"}
     prompt]
    [:p.lesson__instruction
     (case kind
       :trial/example [:i18n :lesson/translate-sentence]
       :trial/phrase  [:i18n :lesson/translate-phrase]
       :trial/word    [:i18n :lesson/translate-word])]]])


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
        :on   {:click [[:action/open-page :page/words]]}}
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
          {:keys [current-page current-shown? finished? page pages viewed-answer]}
          (presenter/strip-props state)]
      [:div.lesson
       (when answer-hint (answer-hint-popover answer-hint))
       [:h1.lesson__title.visually-hidden
        [:i18n :lesson/title]]
       ;; The lesson is closed by the shell's corner ✕ (`:action/close-page`).
       [:header.lesson__header
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
       ;; Only the challenge moves with the strip: a past trial's answer is a strip along the bottom, over the
       ;; current trial's text, which keeps its place and its typed answer; the main button below is one.
       ;; `pages.lesson.page-panel` gives the CSS the height of the panel shown, for the colour behind them; the
       ;; panel shown is its last child that is not inert, so the way home comes first.
       [:page-panel.lesson__panel
        {:page (when-not current-shown? page)}
        ;; At the end of a lesson the way out, to the window beneath, one for every panel: it travels with the
        ;; panel's edge.
        (when finished?
          [:div.lesson__panel-finish
           [:button.lesson__finish
            {:id   "lesson-finish"
             :type "button"
             :on   {:click [[:action/close-page]]}}
            [:i18n :lesson/finish]]])
        [:div.lesson__panel-current
         (let [footer-props (presenter/footer-props state)]
           (if footer-props
             (case (:variant footer-props)
               :success (success-footer footer-props)
               :error   (error-footer footer-props))
             (input-footer (presenter/input-props state))))]
        ;; A past trial's answer, one per page: the one leaving fades where it stood while the next appears; leaving
        ;; for the current trial, the strip fades too, inert at once.
        (when-let [{:keys [attempts first-try?]} viewed-answer]
          [:div.lesson__footer.lesson__footer--success.lesson__footer--past.page-footer
           {:replicant/unmounting {:class "lesson__footer--leaving"
                                   :inert true}}
           [:div.lesson__answer
            {:replicant/key        page
             :replicant/unmounting {:class "lesson__answer--leaving"}}
            [:h3.lesson__answer-header
             (if first-try?
               [:i18n :lesson/correct]
               [:i18n :lesson/attempts {:count attempts}])]
            (answer-body viewed-answer)]])]])))


(defn hotkeys
  "The lesson's keys: `Escape` ends a lesson that is over; the arrows page the trials, but not while the current
   one waits for its answer, the caret of its field theirs; on a past trial `Ctrl`+`Enter` presses the main button,
   as a plain `Enter` does on it, focused."
  [state]
  (when (:lesson/state state)
    (let [{:keys [answered? current-page current-shown? finished? first-shown? next-page previous-page]}
          (presenter/paging-props state)]
      (merge
       (when finished?
         {"Escape" [[:action/close-page]]})
       (when (and (not first-shown?) (or answered? (not current-shown?)))
         {"ArrowLeft" [[:action/view-trial previous-page current-page]]})
       (when-not current-shown?
         {"ArrowRight" [[:action/view-trial next-page current-page]]
          "Ctrl+Enter" [[:action/press-next]]})))))


;; A lesson is a mode, not a step deeper: it fades in and out. Within it, «ДАЛЕЕ» moves the trial.
(defmethod pages/page :page/lesson
  [_]
  {:hotkeys     hotkeys
   :keyboard-overlays? true
   :open        :action/open-lesson
   :path        "/lesson"
   :refresh     :action/show-lesson
   :transitions {:in     :transition/fade-in
                 :out    :transition/fade-out
                 :within (fn [before after]
                           (when (presenter/next-trial? before after)
                             :transition/next-trial))}
   :view        page})

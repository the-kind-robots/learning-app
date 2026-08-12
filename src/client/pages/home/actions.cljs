(ns pages.home.actions
  (:require
   [clojure.string :as str]
   [domain.phrase :as phrase]
   [nexus.registry :as nxr]))


(def ^:private empty-suggestions nil)


(nxr/register-action! :action/go-to-home
  (fn go-to-home [_]
    [[:effect/navigate :page/home]]))


(nxr/register-action! :action/show-home
  (fn show-home [_ {:keys [active-id active-name total]}]
    [[:effect/save
      {:page/current          :page/home
       ;; Reloaded after every replication pass (#255): lesson availability
       ;; is derived from synced data, so a pull landing while the user sits
       ;; here — a poke, a pairing adoption — must recompute it. The refresh
       ;; variant leaves the add form alone.
       :page/load             [:effect/refresh-home]
       :home/active-coll-id   active-id
       :home/active-coll-name active-name
       :home/add-error        nil
       :home/empty-vocab?     (zero? total)
       :home/mode-override    nil
       :home/suggestions      empty-suggestions
       :home/translation      ""
       :home/translation-typed? false
       :home/word             ""}]
     ;; Only Safari ever measures a height here, and only it needs the reset:
     ;; where `field-sizing` works the browser has already forgotten the
     ;; content the old height was measured for.
     [:effect/clear-autogrow "new-word-value"]
     [:effect/clear-autogrow "new-word-translation"]]))


(nxr/register-action! :action/refresh-home
  (fn refresh-home [_ {:keys [active-id active-name total]}]
    [[:effect/save
      {:home/active-coll-id   active-id
       :home/active-coll-name active-name
       :home/empty-vocab?     (zero? total)}]]))


(nxr/register-action! :action/handle-collection-rename-keydown
  (fn handle-collection-rename-keydown [state key]
    (cond
      (= key "Enter")  [[:effect/prevent-default]
                        [:effect/blur-target]]
      (= key "Escape") [[:effect/prevent-default]
                        [:effect/set-target-text (:home/active-coll-name state)]
                        [:effect/blur-target]])))


(defn- suggestions
  ([completions]
   (suggestions completions 0))
  ([completions active-idx]
   (let [completions (vec completions)]
     {:suggestions/items      completions
      :suggestions/active-idx active-idx
      :suggestions/active     (get completions active-idx)})))


(nxr/register-action! :action/update-suggestions
  (fn update-suggestions [state {:keys [completions value]}]
    ;; Guarded by the value the query was made for: a debounced answer landing
    ;; after more typing (say, the pre-space prefix of a phrase) must neither
    ;; show a stale list nor prefill the translation with a stale word.
    (when (= value (:home/word state))
      ;; While the user has not typed a translation, the field belongs to the
      ;; dictionary and follows its current answer — including an answer with
      ;; nothing to offer, or the word it was filled for would linger under a
      ;; phrase that no longer has anything to do with it (GH-178 kept the
      ;; prefill; the owner asked for it). A typed or picked translation is
      ;; theirs, and late answers leave it alone.
      (let [{:keys [translations]} (first completions)]
        [[:effect/save
          (cond-> {:home/suggestions (suggestions completions)}
            (not (:home/translation-typed? state))
            (assoc :home/translation (str/join ", " translations)))]]))))


(nxr/register-action! :action/show-word-error
  (fn show-word-error [_ error]
    [[:effect/save {:home/add-error error}]]))


(nxr/register-action! :action/dismiss-suggestions
  (fn dismiss-suggestions [_]
    [[:effect/save {:home/suggestions empty-suggestions}]]))


;; Suggestions are deliberately not cleared here: the previous list stays
;; until :action/update-suggestions delivers the next answer (GH-178), so the
;; list does not flash empty on every keystroke. An emptied input still clears
;; it — the dictionary returns [] for an empty prefix.
(nxr/register-action! :action/update-word
  (fn update-word [state value]
    ;; A prefilled translation belongs to the word that earned it: emptying the
    ;; German field drops it, but it is no longer wiped on every keystroke —
    ;; that made the field blink and resize while typing in this one.
    [[:effect/save
      (cond-> {:home/word value}
        (and (str/blank? value) (not (:home/translation-typed? state)))
        (assoc :home/translation ""))]
     [:effect/suggest-completions value]]))


(nxr/register-action! :action/toggle-add-mode
  (fn toggle-add-mode [state]
    (let [mode (phrase/add-mode (:home/word state)
                                (:suggestions/items (:home/suggestions state))
                                (:home/mode-override state))]
      [[:effect/save {:home/mode-override (if (= :phrase mode) :word :phrase)}]])))


(nxr/register-action! :action/submit-if-enter
  (fn submit-if-enter [_ {:keys [key shift?]}]
    (when (and (= "Enter" key) (not shift?))
      [[:effect/prevent-default]
       [:effect/request-submit]])))


(nxr/register-action! :action/update-translation
  (fn update-translation [_ value]
    ;; Clearing the field hands it back to the dictionary: the next answer may
    ;; prefill it again.
    [[:effect/save
      {:home/translation        value
       :home/translation-typed? (not (str/blank? value))}]]))


(nxr/register-action! :action/add-word
  (fn add-word [state {:keys [value translation focus-id]}]
    (let [mode (phrase/add-mode (:home/word state)
                                (:suggestions/items (:home/suggestions state))
                                (:home/mode-override state))]
      [[:effect/add-word
        {:focus-id    focus-id
         :mode        mode
         :translation translation
         :value       value}]])))


(nxr/register-action! :action/focus-word-input
  (fn focus-word-input [_ element-id]
    [[:effect/mobile-autofocus element-id]]))


(defn- select-item
  [{:keys [lemma translations] :as item} element-id]
  ;; Picking a suggestion decides the mode by its pos, overriding the space
  ;; heuristic: a multi-word pos=phrase lemma is a phrase, anything else a
  ;; word. Click payloads carry a precomputed :phrase?, keyboard selection
  ;; hands the raw completion with :pos — accept either.
  [[:effect/save
    {:home/mode-override (if (or (:phrase? item) (phrase/phrase-suggestion? item))
                           :phrase
                           :word)
     :home/suggestions   empty-suggestions
     :home/translation   (str/join ", " translations)
     ;; A deliberate pick owns the field: later dictionary answers must not
     ;; overwrite what the user chose.
     :home/translation-typed? true
     :home/word          lemma}]
   [:effect/focus element-id]])


(nxr/register-action! :action/handler-word-keydown
  (fn handle-word-keydown [state {:keys [key shift? focus-id scroll-selector]}]
    (let [{:suggestions/keys [items active-idx]} (:home/suggestions state)
          n (count items)]
      (cond
        (and (pos? n) (= key "ArrowDown"))
        (let [new-idx (min (dec n) (inc (or active-idx -1)))]
          [[:effect/prevent-default]
           [:effect/save {:home/suggestions (suggestions items new-idx)}]
           [:effect/scroll-nearest scroll-selector]])

        (and (pos? n) (= key "ArrowUp"))
        (let [new-idx (max 0 (dec (or active-idx 0)))]
          [[:effect/prevent-default]
           [:effect/save {:home/suggestions (suggestions items new-idx)}]
           [:effect/scroll-nearest scroll-selector]])

        (and (pos? n) (or (= key "Enter") (and (= key "Tab") (not shift?))))
        (let [item (get (vec items) (or active-idx 0))]
          (when item
            (into [[:effect/prevent-default]] (select-item item focus-id))))

        (and (pos? n) (= key "Escape"))
        [[:effect/prevent-default]
         [:effect/save {:home/suggestions empty-suggestions}]]

        ;; No suggestions on screen: Enter moves on to the translation field —
        ;; the phrase flow's typing rhythm (phrase, Enter, translation, Enter).
        (and (zero? n) (= key "Enter"))
        [[:effect/prevent-default]
         [:effect/focus focus-id]]))))


(nxr/register-action! :action/select-suggestion
  (fn select-suggestion [_ {:keys [focus-id] :as item}]
    (select-item item focus-id)))

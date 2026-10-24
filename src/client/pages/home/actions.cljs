(ns pages.home.actions
  (:require
   [clojure.string :as str]
   [domain.phrase :as phrase]
   [domain.vocabulary :as domain]
   [nexus.registry :as nxr]
   [use-cases.vocabulary :as vocabulary]))


(def ^:private empty-suggestions nil)


(defn- prefill-text
  "The text a completion puts into the translation field: several translations read as
   `пёс, собака`, each piece trimmed. Whatever the field holds is stored as one translation."
  [translations]
  (->> translations
       (map str/trim)
       (remove str/blank?)
       (str/join ", ")))


(nxr/register-action! :action/go-to-home
  (fn go-to-home [_]
    [[:effect/navigate :page/home]]))


(defn content
  "What home shows of the learner's data: the active collection's heading and whether there is anything to study."
  [_ {collection :active-collection :keys [learner]}]
  {:home/active-coll-id   (:id collection)
   :home/active-coll-name (:name collection)
   :home/empty-vocab?     (vocabulary/empty-in? learner collection)})


(def ^:private fresh-form
  {:home/add-error     nil
   :home/mode-override nil
   :home/suggestions   empty-suggestions
   :home/translation   ""
   :home/translation-typed? false
   :home/word          ""})


(nxr/register-action! :action/open-home
  ;; Home is computed from memory in the task of the tap. Entering home again while it is on display
  ;; (the `popstate` of a step back) leaves the form alone.
  (fn open-home [state context]
    (let [shown? (= :page/home (:page/current state))]
      (cond-> [[:effect/save
                (merge (when-not shown? fresh-form)
                       {:page/current :page/home}
                       (content state context))]]
        ;; Only Safari measures a height here; where `field-sizing` works the browser has forgotten the old height.
        (not shown?) (conj [:effect/clear-autogrow "new-word-value"]
                           [:effect/clear-autogrow "new-word-translation"])))))


(nxr/register-action! :action/show-home
  ;; Home computed again from memory without touching the form: after the load and the reader's own change.
  (fn show-home [state context]
    [[:effect/save (content state context)]]))


(nxr/register-action! :action/word-added
  ;; The form empties once the word is stored; home is computed again from memory, which took the word with PouchDB.
  (fn word-added [_]
    [[:effect/save fresh-form]
     [:effect/clear-autogrow "new-word-value"]
     [:effect/clear-autogrow "new-word-translation"]
     [:effect/enter :action/refresh-page]]))


(nxr/register-action! :action/handle-collection-rename-keydown
  (fn handle-collection-rename-keydown [state key]
    (cond
      (= key "Enter")  [[:effect/prevent-default]
                        [:effect/blur-target]]
      (= key "Escape") [[:effect/prevent-default]
                        [:effect/set-target-text (:home/active-coll-name state)]
                        [:effect/blur-target]])))


(defn- suggestions
  "The index is the only record of which entry is active; a second copy would let the handler
   and the view disagree."
  ([completions]
   (suggestions completions 0))
  ([completions active-idx]
   {:suggestions/items      (vec completions)
    :suggestions/active-idx active-idx}))


(nxr/register-action! :action/update-suggestions
  (fn update-suggestions [state {:keys [completions value]}]
    ;; Guarded by the value the query was made for: a debounced answer landing after more typing must
    ;; neither show a stale list nor prefill a stale word.
    (when (= value (:home/word state))
      ;; While no translation is typed the field follows the dictionary's current answer, an empty one
      ;; included, or the word it was filled for would linger. A typed or picked translation is the user's.
      (let [{:keys [translations]} (first completions)]
        [[:effect/save
          (cond-> {:home/suggestions (suggestions completions)}
            (not (:home/translation-typed? state))
            (assoc :home/translation (prefill-text translations)))]]))))


(nxr/register-action! :action/show-word-error
  (fn show-word-error [_ error]
    [[:effect/save {:home/add-error error}]]))


(nxr/register-action! :action/dismiss-suggestions
  (fn dismiss-suggestions [_]
    [[:effect/save {:home/suggestions empty-suggestions}]]))


(defn- narrowed
  "The shown suggestions that still complete `value`, in order, with the marked entry kept when it survives.
   Returns nil when none are left."
  [{:suggestions/keys [items active-idx]} value]
  (let [active (get items (or active-idx 0))
        kept   (filterv #(domain/completes? value %) items)]
    (when (seq kept)
      (suggestions kept (max 0 (.indexOf kept active))))))


;; Keeps the shown suggestions that still complete the new value; the next answer replaces them.
(nxr/register-action! :action/update-word
  (fn update-word [state value]
    [[:effect/save
      (cond-> {:home/word value}
        (:home/suggestions state)
        (assoc :home/suggestions (narrowed (:home/suggestions state) value))
        (and (str/blank? value) (not (:home/translation-typed? state)))
        (assoc :home/translation ""))]
     [:effect/suggest-completions value]]))


(nxr/register-action! :action/update-translation
  (fn update-translation [_ value]
    ;; Clearing the field hands it back to the dictionary: the next answer may prefill it again.
    [[:effect/save
      {:home/translation        value
       :home/translation-typed? (not (str/blank? value))}]]))


(nxr/register-action! :action/add-word
  (fn add-word [state {:keys [value translation focus-id]}]
    (let [mode (phrase/add-mode (:home/word state)
                                (:suggestions/items (:home/suggestions state))
                                (:home/mode-override state))
          pos  (:pos (phrase/current-pick (:home/word state) (:home/mode-override state)))]
      [[:effect/add-word
        {:focus-id    focus-id
         :mode        mode
         :pos         pos
         :translation translation
         :value       value}]])))


(nxr/register-action! :action/focus-word-input
  (fn focus-word-input [_ element-id]
    [[:effect/mobile-autofocus element-id]]))


(defn- select-item
  [{:keys [lemma pos translations] :as item} element-id]
  ;; Picking a suggestion decides the mode by its pos, over the space heuristic: a multi-word pos=phrase
  ;; lemma is a phrase, anything else a word. Click payloads carry `:phrase?`, keyboard selection the raw
  ;; completion with `:pos`. The lemma and its part of speech are stored with the decision and expire
  ;; when the value stops being it; the example request carries the part of speech to tell `rauch` from `der Rauch`.
  [[:effect/save
    {:home/mode-override {:mode  (if (or (:phrase? item) (phrase/phrase-suggestion? item))
                                   :phrase
                                   :word)
                          :pos   pos
                          :value lemma}
     :home/suggestions   empty-suggestions
     :home/translation   (prefill-text translations)
     ;; A deliberate pick owns the field: later dictionary answers must not overwrite it.
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

        ;; No suggestions on screen: Enter moves on to the translation field, the phrase flow's rhythm
        ;; (Enter belongs to the text of a multi-line field; Ctrl/Cmd+Enter submits).
        (and (zero? n) (= key "Enter"))
        [[:effect/prevent-default]
         [:effect/focus focus-id]]))))


(nxr/register-action! :action/select-suggestion
  (fn select-suggestion [_ {:keys [focus-id] :as item}]
    (select-item item focus-id)))

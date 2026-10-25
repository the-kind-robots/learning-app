(ns pages.home.actions
  (:require
   [clojure.string :as str]
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
  {:home/add-error   nil
   :home/pick        nil
   :home/suggestions empty-suggestions
   :home/translation ""
   :home/translation-typed? false
   :home/word        ""})


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
  ;; The word list takes the words added since it was last open, to highlight them.
  (fn word-added [state word-id]
    [[:effect/save (assoc fresh-form :home/added (conj (or (:home/added state) #{}) word-id))]
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


;; Keeps the shown suggestions, inert where they no longer complete the new value; the next answer replaces them.
(nxr/register-action! :action/update-word
  (fn update-word [state value]
    [[:effect/save
      (cond-> {:home/word value}
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
  (fn add-word [_ entered]
    [[:effect/add-word entered]]))


(nxr/register-action! :action/focus-word-input
  (fn focus-word-input [_ element-id]
    [[:effect/mobile-autofocus element-id]]))


(defn- select-item
  [{:keys [lemma pos translations]} element-id]
  ;; The picked lemma and its part of speech decide the mode over the space heuristic, and expire when the
  ;; value stops being the lemma; the example request carries the part of speech to tell `rauch` from `der Rauch`.
  [[:effect/save
    {:home/pick        {:lemma lemma :pos pos}
     :home/suggestions empty-suggestions
     :home/translation (prefill-text translations)
     ;; A deliberate pick owns the field: later dictionary answers must not overwrite it.
     :home/translation-typed? true
     :home/word        lemma}]
   [:effect/focus element-id]])


(nxr/register-action! :action/handler-word-keydown
  ;; The marked row and the arrows' targets come with the key, as the screen shows them.
  (fn handle-word-keydown [state {:keys [key shift? focus-id scroll-selector active-idx next-idx previous-idx]}]
    (let [items  (:suggestions/items (:home/suggestions state))
          n      (count items)
          move   (fn [new-idx]
                   (when new-idx
                     [[:effect/prevent-default]
                      [:effect/save {:home/suggestions (suggestions items new-idx)}]
                      [:effect/scroll-nearest scroll-selector]]))
          active (some->> active-idx (get items))]
      (cond
        (and (pos? n) (= key "ArrowDown"))
        (move next-idx)

        (and (pos? n) (= key "ArrowUp"))
        (move previous-idx)

        (and active (or (= key "Enter") (and (= key "Tab") (not shift?))))
        (into [[:effect/prevent-default]] (select-item active focus-id))

        (and (pos? n) (= key "Escape"))
        [[:effect/prevent-default]
         [:effect/save {:home/suggestions empty-suggestions}]]

        ;; No active suggestion (none shown, or none left that completes the field): Enter moves on to the
        ;; translation field, the phrase flow's rhythm (Enter belongs to the text of a multi-line field;
        ;; Ctrl/Cmd+Enter submits).
        (= key "Enter")
        [[:effect/prevent-default]
         [:effect/focus focus-id]]))))


(nxr/register-action! :action/select-suggestion
  (fn select-suggestion [_ {:keys [focus-id] :as item}]
    (select-item item focus-id)))

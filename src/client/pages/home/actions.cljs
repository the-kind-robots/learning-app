(ns pages.home.actions
  (:require
   [clojure.string :as str]
   [domain.phrase :as phrase]
   [domain.vocabulary :as vocabulary]
   [nexus.registry :as nxr]))


(def ^:private empty-suggestions nil)


(defn- prefill-text
  "The text a completion puts into the translation field. Several translations
   read as `пёс, собака`; one arrives and is handed over untouched. Each piece
   is trimmed before rejoining: the dictionary now carries translations as
   elements (#362), but a stored value may still have its own padding, and a
   completion that predates the rebuild may still arrive split. It matters more
   than it used to — whatever the field holds is stored as one translation, so
   what is shown here is what is saved."
  [translations]
  (->> translations
       (map str/trim)
       (remove str/blank?)
       (str/join ", ")))


(nxr/register-action! :action/go-to-home
  (fn go-to-home [_]
    [[:effect/navigate :page/home]]))


;; The screen's other job is the lesson, and its button sits in the footer
;; below the add form — a pointer away from the field the user is typing in.
;; `Alt`+`Enter` starts it without leaving the keyboard. The handler is on the
;; document, so it works wherever the focus sits, nowhere at all included, and
;; the action means nothing off the home screen. An empty vocabulary hides the
;; footer, and the keystroke offers no more than the screen does.
(nxr/register-action! :action/start-lesson-if-alt-enter
  (fn start-lesson-if-alt-enter [state {:keys [alt? key]}]
    (when (and alt?
               (= "Enter" key)
               (= :page/home (:page/current state))
               (not (:home/empty-vocab? state)))
      [[:effect/prevent-default]
       [:action/go-to-lesson]])))


(defn content
  "What home shows of the learner's data in `state`: the active collection's
   heading and whether there is anything to study."
  [state {collection :active-collection :keys [learner]}]
  (let [memory (:learner/memory state)]
    {:home/active-coll-id   (:id collection)
     :home/active-coll-name (:name collection)
     ;; The words present in scope, as the word list and the lesson count
     ;; them: a collection may still list a word deleted elsewhere.
     :home/empty-vocab?     (empty? ((:learner/collection-words learner) memory collection))}))


(def ^:private fresh-form
  {:home/add-error     nil
   :home/mode-override nil
   :home/suggestions   empty-suggestions
   :home/translation   ""
   :home/translation-typed? false
   :home/word          ""})


(nxr/register-action! :action/open-home
  ;; Home is computed from memory and saved in one write, in the task of the
  ;; tap. Entering home again while it is on display — the `popstate` of the
  ;; step back a close already rendered home for — leaves the form alone.
  (fn open-home [state context]
    (let [shown? (= :page/home (:page/current state))]
      (cond-> [[:effect/save
                (merge (when-not shown? fresh-form)
                       {:page/current :page/home}
                       (content state context))]]
        ;; Only Safari ever measures a height here, and only it needs the
        ;; reset: where `field-sizing` works the browser has already forgotten
        ;; the content the old height was measured for.
        (not shown?) (conj [:effect/clear-autogrow "new-word-value"]
                           [:effect/clear-autogrow "new-word-translation"])))))


(nxr/register-action! :action/show-home
  ;; Home computed again from memory without touching the form: after the
  ;; load and after the reader's own change on it.
  (fn show-home [state context]
    [[:effect/save (content state context)]]))


(nxr/register-action! :action/word-added
  ;; The form empties once the word is stored, and home is computed again
  ;; from memory, which took the word when PouchDB did.
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
  "The index is the only record of which entry is active. It used to be kept
   twice — the index and the completion it names — and the two readers took
   different copies: the handler moved the index, the view compared the value
   and never matched (#412)."
  ([completions]
   (suggestions completions 0))
  ([completions active-idx]
   {:suggestions/items      (vec completions)
    :suggestions/active-idx active-idx}))


(defn- completing-items
  "The shown suggestions that still complete the word field: the only ones that count."
  [{:home/keys [word suggestions]}]
  (let [items (:suggestions/items suggestions)]
    (mapv items (vocabulary/completing-indexes word items))))


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
            (assoc :home/translation (prefill-text translations)))]]))))


(nxr/register-action! :action/show-word-error
  (fn show-word-error [_ error]
    [[:effect/save {:home/add-error error}]]))


(nxr/register-action! :action/dismiss-suggestions
  (fn dismiss-suggestions [_]
    [[:effect/save {:home/suggestions empty-suggestions}]]))


(defn- narrowed
  "All shown suggestions, rows the typed text has left behind included: the list
   neither shrinks nor empties between answers (#584). The marked entry stays
   when it still completes `value`, else it moves to the first row that does,
   else to none."
  [{:suggestions/keys [items active-idx]} value]
  (let [idxs (vocabulary/completing-indexes value items)]
    (suggestions items (if (some #{active-idx} idxs) active-idx (first idxs)))))


;; Keeps the shown suggestions, inert where they no longer complete the new
;; value; the next answer replaces them.
(nxr/register-action! :action/update-word
  (fn update-word [state value]
    ;; Emptying the German field drops a prefilled translation.
    [[:effect/save
      (cond-> {:home/word value}
        (:home/suggestions state)
        (assoc :home/suggestions (narrowed (:home/suggestions state) value))
        (and (str/blank? value) (not (:home/translation-typed? state)))
        (assoc :home/translation ""))]
     [:effect/suggest-completions value]]))


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
                                (completing-items state)
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
  ;; hands the raw completion with :pos — accept either. The lemma is stored
  ;; with it: the decision was about that lemma and expires when the value
  ;; stops being it (GH-358).
  [[:effect/save
    {:home/mode-override {:mode  (if (or (:phrase? item) (phrase/phrase-suggestion? item))
                                   :phrase
                                   :word)
                          :value lemma}
     :home/suggestions   empty-suggestions
     :home/translation   (prefill-text translations)
     ;; A deliberate pick owns the field: later dictionary answers must not
     ;; overwrite what the user chose.
     :home/translation-typed? true
     :home/word          lemma}]
   [:effect/focus element-id]])


(nxr/register-action! :action/handler-word-keydown
  (fn handle-word-keydown [state {:keys [key shift? focus-id scroll-selector]}]
    (let [{:suggestions/keys [items active-idx]} (:home/suggestions state)
          n      (count items)
          idxs   (vocabulary/completing-indexes (:home/word state) items)
          ;; Arrows walk the rows that still complete the field; the rest are inert.
          move   (fn [new-idx]
                   (when new-idx
                     [[:effect/prevent-default]
                      [:effect/save {:home/suggestions (suggestions items new-idx)}]
                      [:effect/scroll-nearest scroll-selector]]))
          active (when (some #{active-idx} idxs) (get items active-idx))]
      (cond
        (and (pos? n) (= key "ArrowDown"))
        (move (or (first (filter #(> % (or active-idx -1)) idxs)) active-idx))

        (and (pos? n) (= key "ArrowUp"))
        (move (or (last (filter #(< % (or active-idx 0)) idxs)) active-idx))

        (and active (or (= key "Enter") (and (= key "Tab") (not shift?))))
        (into [[:effect/prevent-default]] (select-item active focus-id))

        (and (pos? n) (= key "Escape"))
        [[:effect/prevent-default]
         [:effect/save {:home/suggestions empty-suggestions}]]

        ;; No active suggestion: none shown, or none left that completes the
        ;; field. Enter moves on to the translation field, the phrase flow's
        ;; typing rhythm (phrase, Enter, translation, and then Ctrl/Cmd+Enter
        ;; or the button, since Enter belongs to the text in a field that takes
        ;; several lines).
        (= key "Enter")
        [[:effect/prevent-default]
         [:effect/focus focus-id]]))))


(nxr/register-action! :action/select-suggestion
  (fn select-suggestion [_ {:keys [focus-id] :as item}]
    (select-item item focus-id)))

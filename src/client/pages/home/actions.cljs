(ns pages.home.actions
  (:require
   [clojure.string :as str]
   [nexus.registry :as nxr]))


(def ^:private empty-suggestions nil)


(nxr/register-action! :action/go-to-home
  (fn go-to-home [_]
    [[:effect/navigate :page/home]]))


(nxr/register-action! :action/show-home
  (fn show-home [_ {:keys [active-id active-name total]}]
    [[:effect/save
      {:page/current        :page/home
       ;; Reloaded after every replication pass (#255): lesson availability
       ;; is derived from synced data, so a pull landing while the user sits
       ;; here — a poke, a pairing adoption — must recompute it. The refresh
       ;; variant leaves the add form alone.
       :page/load           [:effect/refresh-home]
       :home/active-coll-id active-id
       :home/active-coll-name active-name
       :home/add-error      nil
       :home/empty-vocab?   (zero? total)
       :home/suggestions    empty-suggestions
       :home/translation    ""
       :home/word           ""}]]))


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
  (fn update-suggestions [state completions]
    ;; The top suggestion's translation pre-fills an empty translation field
    ;; (GH-178: the owner said yes). Blank-guarded: a translation the user
    ;; already typed is theirs — the dictionary answers late (debounce plus
    ;; query), and late answers must not clobber the user's input.
    (let [{:keys [translations]} (first completions)]
      [[:effect/save
        (cond-> {:home/suggestions (suggestions completions)}
          (and (seq translations) (str/blank? (:home/translation state)))
          (assoc :home/translation (str/join ", " translations)))]])))


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
  (fn update-word [_ value]
    [[:effect/save {:home/word value :home/translation ""}]
     [:effect/suggest-completions value]]))


(nxr/register-action! :action/update-translation
  (fn update-translation [_ value]
    [[:effect/save {:home/translation value}]]))


(nxr/register-action! :action/add-word
  (fn add-word [_ {:keys [value translation focus-id]}]
    [[:effect/add-word {:value value :translation translation :focus-id focus-id}]]))


(nxr/register-action! :action/focus-word-input
  (fn focus-word-input [_ element-id]
    [[:effect/mobile-autofocus element-id]]))


(defn- select-item
  [{:keys [lemma translations]} element-id]
  [[:effect/save
    {:home/word        lemma
     :home/translation (str/join ", " translations)
     :home/suggestions empty-suggestions}]
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
         [:effect/save {:home/suggestions empty-suggestions}]]))))


(nxr/register-action! :action/select-suggestion
  (fn select-suggestion [_ {:keys [focus-id] :as item}]
    (select-item item focus-id)))

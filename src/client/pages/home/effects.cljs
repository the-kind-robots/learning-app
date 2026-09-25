(ns pages.home.effects
  (:require
   [goog.functions :as gfn]
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [use-cases.collections :as collections]
   [use-cases.vocabulary :as vocabulary]))


;; Measured end to end (#195): past the debounce, worker + SQLite + render
;; cost 9-44 ms (one-letter prefix worst). And since the list now keeps its
;; previous answer until the next one arrives (#178), a mid-burst answer
;; replaces smoothly instead of flashing. 100 ms does not coalesce typing at
;; the measured cadence — 120-180 ms per key is longer than the wait, so a
;; trailing debounce expires between keys and every keystroke gets a query of
;; its own. What it does buy is the burst faster than that, and suggestions
;; ~110-145 ms after the typing stops.
(def ^:private suggest!
  (gfn/debounce
   (fn ^:async suggest!
     [dispatch dictionary value]
     (try
       ;; No readiness check, and none is coming back: the worker answers
       ;; with what this tab has — the database if it holds it, no rows if it
       ;; does not (#351) — so a gate here would only re-decide what has
       ;; already been decided one hop away. `:dictionary/ready?` stays on the
       ;; port as the reading #312 needs to say why a list is empty. A worker
       ;; that could not open the database rejects, which the catch reports.
       (let [completions (await ((:dictionary/completions dictionary) value))]
         ;; The queried value rides along so the action can drop answers that
         ;; arrive after further typing (stale list, stale translation prefill).
         (dispatch [[:action/update-suggestions {:completions completions :value value}]]))
       (catch js/Error err
         (log/error :effect/suggest-completions {:error (str err)}))))
   100))


(nxr/register-effect! :effect/forget-active-collection
  (fn forget-active-collection [{:keys [capabilities]} _]
    ((get-in capabilities [:collections :collections/set-active!]) nil)))


(nxr/register-effect! :effect/suggest-completions
  (fn suggest-dictionary
    [{:keys [capabilities dispatch]} _ value]
    (suggest! dispatch (:dictionary capabilities) value)))


(nxr/register-effect! :effect/rename-active-collection
  (fn ^:async rename-active-collection
    [{:keys [capabilities dispatch]} _ new-name]
    (try
      ;; A blank or a taken name keeps the current one, and a store that did
      ;; not change renders nothing (#213) — the heading would keep what was
      ;; typed. So it is written by hand, through the same effect Escape
      ;; already uses.
      ;;
      ;; The write lands after the blur that started it, and the tap that
      ;; blurred the heading may have opened another screen by then (#460).
      ;; Memory takes the renamed collection when PouchDB does, and whichever
      ;; screen is on display follows it.
      (when-let [{:keys [name]} (await (collections/rename-active! capabilities new-name))]
        (dispatch [[:effect/set-target-text name]]))
      (catch js/Error err
        (log/error :effect/rename-active-collection {:error (str err)})))))


(nxr/register-effect! :effect/add-word
  (fn ^:async add-word
    [{:keys [dispatch capabilities]} _ {:keys [value translation focus-id mode]}]
    (try
      (let [result (await (vocabulary/add! capabilities value translation (or mode :word)))]
        (if (:error result)
          (dispatch [[:action/show-word-error (:error result)]])
          (dispatch (cond-> [[:action/word-added]]
                      focus-id (conj [:effect/focus focus-id])))))
      ;; A write that threw is said on the form, which keeps what was typed
      ;; (#313). Nothing the user does fixes it, so no cause is told apart.
      (catch js/Error err
        (log/error :effect/add-word {:error (str err)})
        (dispatch [[:action/show-word-error :save-failed]])))))

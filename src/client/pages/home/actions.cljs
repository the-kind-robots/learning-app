(ns pages.home.actions
  (:require
   [nexus.registry :as nxr]))


(nxr/register-action! :action/show-home
  (fn show-home [_ total]
    [[:effect/save
      {:app/page          :page/home
       :home/empty-vocab? (zero? total)
       :home/suggestions  []
       :home/prefill      nil
       :home/add-error    nil}]]))


(nxr/register-action! :action/update-suggestions
  (fn update-suggestions [_ {:keys [suggestions prefill]}]
    [[:effect/save
      {:home/suggestions suggestions
       :home/prefill     prefill}]]))


(nxr/register-action! :action/show-word-error
  (fn show-word-error [_ error]
    [[:effect/save {:home/add-error error}]]))


(nxr/register-action! :action/look-up-word
  (fn look-up-word [_ value]
    [[:effect/suggest-dictionary value]]))


(nxr/register-action! :action/add-word
  (fn add-word [_ {:keys [value translation]}]
    [[:effect/add-word {:value value :translation translation}]]))


(nxr/register-action! :action/focus-word-input
  (fn focus-word-input [_]
    [[:effect/mobile-autofocus "new-word-value"]]))

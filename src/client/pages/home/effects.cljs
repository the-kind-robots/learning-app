(ns pages.home.effects
  (:require
   [goog.functions :as gfn]
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [use-cases.vocabulary :as vocabulary]))


(def ^:private suggest!
  (gfn/debounce
   (fn ^:async suggest!
     [dispatch dictionary value]
     (try
       (when-let [completions (when ((:dictionary/ready? dictionary))
                                (await ((:dictionary/completions dictionary) value)))]
         (dispatch [[:action/update-suggestions completions]]))
       (catch js/Error err
         (log/error :effect/suggest-completions {:error (str err)}))))
   300))


(nxr/register-effect! :effect/load-home
  (fn ^:async load-home
    [{:keys [capabilities dispatch]} _]
    (try
      (let [total (await (vocabulary/count capabilities))]
        (dispatch [[:action/show-home total]]))
      (catch js/Error err
        (log/error :effect/load-home {:error (str err)})))))


(nxr/register-effect! :effect/suggest-completions
  (fn suggest-dictionary
    [{:keys [capabilities dispatch]} _ value]
    (suggest! dispatch (:dictionary capabilities) value)))


(nxr/register-effect! :effect/add-word
  (fn ^:async add-word
    [{:keys [dispatch capabilities]} _ {:keys [value translation focus-id]}]
    (try
      (let [result (await (vocabulary/add! capabilities value translation))]
        (if (:error result)
          (dispatch [[:action/show-word-error (:error result)]])
          (let [total (await (vocabulary/count capabilities))]
            (dispatch (cond-> [[:action/show-home total]]
                        focus-id (conj [:effect/focus focus-id]))))))
      (catch js/Error err
        (log/error :effect/add-word {:error (str err)})))))

(ns pages.home.effects
  (:require
   [dbs :as dbs]
   [goog.functions :as gfn]
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [repo.dictionary :as dictionary]
   [use-cases.vocabulary :as vocabulary]))


(def ^:private suggest!
  (gfn/debounce
   (fn ^:async suggest!
     [dispatch value]
     (try
       (when-let [completions (when (dictionary/ready?)
                                (await (dictionary/completions (dbs/dbs) value)))]
         (dispatch [[:action/update-suggestions completions]]))
       (catch js/Error err
         (log/error :effect/suggest-completions {:error (str err)}))))
   300))


(nxr/register-effect! :effect/load-home
  (fn ^:async load-home
    [{:keys [dispatch]} _]
    (try
      (let [total (await (vocabulary/count (dbs/dbs)))]
        (dispatch [[:action/show-home total]]))
      (catch js/Error err
        (log/error :effect/load-home {:error (str err)})))))


(nxr/register-effect! :effect/suggest-completions
  (fn suggest-dictionary [{:keys [dispatch]} _ value]
    (suggest! dispatch value)))


(nxr/register-effect! :effect/add-word
  (fn ^:async add-word
    [{:keys [dispatch]} _ {:keys [value translation focus-id]}]
    (try
      (let [result (await (vocabulary/add! (dbs/dbs) value translation))]
        (if (:error result)
          (dispatch [[:action/show-word-error (:error result)]])
          (let [total (await (vocabulary/count (dbs/dbs)))]
            (dispatch (cond-> [[:action/show-home total]]
                        focus-id (conj [:effect/focus focus-id]))))))
      (catch js/Error err
        (log/error :effect/add-word {:error (str err)})))))

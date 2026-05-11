(ns pages.home.effects
  (:require
   [dbs :as dbs]
   [goog.functions :as gfn]
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [reitit.frontend.easy :as rfe]
   [repo.dictionary-legacy :as dictionary-legacy]
   [use-cases.vocabulary :as vocabulary]))


(def ^:private suggest!
  (gfn/debounce
   (fn ^:async suggest!
     [dispatch value]
     (try
       (let [dbs    (dbs/dbs)
             result (await (dictionary-legacy/suggest dbs value))]
         (dispatch [[:action/update-suggestions result]]))
       (catch js/Error err
         (log/error :effect/suggest-dictionary {:error (str err)}))))
   300))


(nxr/register-action! :action/go-to-home
  (fn go-to-home [_]
    [[:effect/navigate :page/home]]))


(nxr/register-effect! :effect/load-home
  (fn ^:async load-home [{:keys [dispatch]} _]
    (try
      (let [total (await (vocabulary/count (dbs/dbs)))]
        (js/console.log [[:effect/load-home]])
        (dispatch [[:action/show-home total]]))
      (catch js/Error err
        (log/error :effect/load-home {:error (str err)})))))


(nxr/register-effect! :effect/suggest-dictionary
  (fn suggest-dictionary [{:keys [dispatch]} _ value]
    (suggest! dispatch value)))


(nxr/register-effect! :effect/add-word
  (fn ^:async add-word [{:keys [dispatch]} _ {:keys [value translation]}]
    (try
      (let [result (await (vocabulary/add! (dbs/dbs) value translation))]
        (if (:error result)
          (dispatch [[:action/show-word-error (:error result)]])
          (rfe/replace-state :page/home)))
      (catch js/Error err
        (log/error :effect/add-word {:error (str err)})))))

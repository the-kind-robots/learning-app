(ns pages.words.effects
  (:require
   [dbs :as dbs]
   [goog.functions :as gfn]
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [use-cases.vocabulary :as vocabulary]))


(def ^:private search!
  (gfn/debounce
   (fn ^:async search!
     [dispatch search]
     (try
       (let [{:keys [words total]} (await (vocabulary/list (dbs/dbs)
                                                           {:order  :asc
                                                            :search search}))]
         (dispatch [[:action/show-words {:words words :total total :search search}]]))
       (catch js/Error err
         (log/error :effect/set-words-search {:error (str err)}))))
   400))


(nxr/register-action! :action/go-to-words
  (fn go-to-words [_]
    [[:effect/navigate :page/words]]))


(nxr/register-effect! :effect/load-words
  (fn ^:async load-words [{:keys [dispatch]} _ search]
    (try
      (let [{:keys [words total]} (await (vocabulary/list (dbs/dbs)
                                                          {:order  :asc
                                                           :search search}))]
        (dispatch [[:action/show-words {:words words :total total :search search}]]))
      (catch js/Error err
        (log/error :effect/load-words {:error (str err)})))))


(nxr/register-effect! :effect/set-words-search
  (fn set-words-search
    [{:keys [dispatch]} _ search]
    (search! dispatch search)))


(nxr/register-effect! :effect/update-word
  (fn ^:async update-word [{:keys [dispatch]} _ {:keys [id translation search]}]
    (try
      (await (vocabulary/update! (dbs/dbs) id translation))
      (let [{:keys [words total]} (await (vocabulary/list (dbs/dbs)
                                                          {:order  :asc
                                                           :search search}))]
        (dispatch [[:action/show-words {:words words :total total :search search}]]))
      (catch js/Error err
        (log/error :effect/update-word {:error (str err)})))))


(nxr/register-effect! :effect/delete-word
  (fn ^:async delete-word [{:keys [dispatch]} _ {:keys [id value search]}]
    (when (js/confirm (str "Удалить «" value "»?"))
      (try
        (await (vocabulary/delete! (dbs/dbs) id))
        (let [{:keys [words total]} (await (vocabulary/list (dbs/dbs)
                                                            {:order  :asc
                                                             :search search}))]
          (dispatch [[:action/show-words {:words words :total total :search search}]]))
        (catch js/Error err
          (log/error :effect/delete-word {:error (str err)}))))))

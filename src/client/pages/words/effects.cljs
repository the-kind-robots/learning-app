(ns pages.words.effects
  (:require
   [goog.functions :as gfn]
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [use-cases.vocabulary :as vocabulary]))


(def ^:private search!
  (gfn/debounce
   (fn ^:async search!
     [dispatch capabilities search]
     (try
       (let [{:keys [words total]} (await (vocabulary/list-active capabilities {:order :asc :search search}))]
         (dispatch [[:action/show-words {:words words :total total :search search}]]))
       (catch js/Error err
         (log/error :effect/set-words-search {:error (str err)}))))
   400))


(nxr/register-action! :action/go-to-words
  (fn go-to-words [_]
    [[:effect/navigate :page/words]]))


(nxr/register-effect! :effect/load-words
  (fn ^:async load-words
    [{:keys [capabilities dispatch]} _ search]
    (try
      (let [{:keys [words total]} (await (vocabulary/list-active capabilities {:order :asc :search search}))]
        (dispatch [[:action/show-words {:words words :total total :search search}]]))
      (catch js/Error err
        (log/error :effect/load-words {:error (str err)})))))


(nxr/register-effect! :effect/set-words-search
  (fn set-words-search
    [{:keys [capabilities dispatch]} _ search]
    (search! dispatch capabilities search)))


(nxr/register-effect! :effect/update-word
  (fn ^:async update-word
    [{:keys [capabilities dispatch]} _ {:keys [id translation search]}]
    (try
      (await (vocabulary/update! capabilities id translation))
      (let [{:keys [words total]} (await (vocabulary/list-active capabilities {:order :asc :search search}))]
        (dispatch [[:action/show-words {:words words :total total :search search}]]))
      (catch js/Error err
        (log/error :effect/update-word {:error (str err)})))))


(nxr/register-effect! :effect/delete-word
  (fn ^:async delete-word
    [{:keys [capabilities dispatch]} _ {:keys [id value search]}]
    (let [collection-id ((:collections/active-id (:collections capabilities)))
          prompt        (if collection-id
                          (str "Убрать «" value "» из набора?")
                          (str "Удалить «" value "» окончательно?"))]
      (when (js/confirm prompt)
        (try
          (await (vocabulary/remove-from-active! capabilities id))
          (let [{:keys [words total]} (await (vocabulary/list-active capabilities {:order :asc :search search}))]
            (dispatch [[:action/show-words {:words words :total total :search search}]]))
          (catch js/Error err
            (log/error :effect/delete-word {:error (str err)})))))))

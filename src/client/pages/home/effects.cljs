(ns pages.home.effects
  (:require
   [goog.functions :as gfn]
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [use-cases.collections :as collections]
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


(defn- ^:async resolve-active
  "Returns {:active-id … :active-name … :word-count …}. If the stored id no
  longer matches a collection, clears localStorage and falls back to the
  implicit main view. :word-count is the size of the active collection (nil
  for main — caller falls back to the global vocab count)."
  [colls]
  (let [stored-id (when-let [id ((:collections/active-id colls))]
                    (not-empty id))]
    (if stored-id
      (if-let [coll (await ((:collections/get colls) stored-id))]
        {:active-id   stored-id
         :active-name (:name coll)
         :word-count  (count (:word-ids coll))}
        (do ((:collections/set-active! colls) nil)
            {:active-id nil :active-name nil :word-count nil}))
      {:active-id nil :active-name nil :word-count nil})))


(nxr/register-effect! :effect/load-home
  (fn ^:async load-home
    [{:keys [capabilities dispatch]} _]
    (try
      (let [colls (:collections capabilities)
            total (await (vocabulary/count capabilities))
            {:keys [active-id active-name word-count]} (await (resolve-active colls))]
        (dispatch [[:action/show-home
                    {:active-id   active-id
                     :active-name active-name
                     :total       (or word-count total)}]]))
      (catch js/Error err
        (log/error :effect/load-home {:error (str err)})))))


(nxr/register-effect! :effect/suggest-completions
  (fn suggest-dictionary
    [{:keys [capabilities dispatch]} _ value]
    (suggest! dispatch (:dictionary capabilities) value)))


(nxr/register-effect! :effect/rename-active-collection
  (fn ^:async rename-active-collection
    [{:keys [capabilities dispatch]} _ new-name]
    (try
      ;; Always dispatch the final name (even when unchanged) so Replicant
      ;; rewrites the h2 textContent to match state — that's how a blank
      ;; edit reverts visually to the existing name.
      (when-let [{:keys [name]} (await (collections/rename-active! capabilities new-name))]
        (dispatch [[:effect/save {:home/active-coll-name name}]]))
      (catch js/Error err
        (log/error :effect/rename-active-collection {:error (str err)})))))


(nxr/register-effect! :effect/add-word
  (fn ^:async add-word
    [{:keys [dispatch capabilities]} _ {:keys [value translation focus-id]}]
    (try
      (let [result (await (vocabulary/add! capabilities value translation))]
        (if (:error result)
          (dispatch [[:action/show-word-error (:error result)]])
          (let [colls (:collections capabilities)
                total (await (vocabulary/count capabilities))
                {:keys [active-id active-name word-count]} (await (resolve-active colls))]
            (dispatch (cond-> [[:action/show-home
                                {:active-id   active-id
                                 :active-name active-name
                                 :total       (or word-count total)}]]
                        focus-id (conj [:effect/focus focus-id]))))))
      (catch js/Error err
        (log/error :effect/add-word {:error (str err)})))))

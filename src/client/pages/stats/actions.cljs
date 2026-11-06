(ns pages.stats.actions
  (:require
   [i18n :as i18n]
   [nexus.registry :as nxr]
   [pages.stats.picture :as picture]
   [use-cases.lesson :as lesson]
   [use-cases.vocabulary :as vocabulary]))


(nxr/register-action! :action/show-stats
  ;; On entry, and again from memory after the load or when a lesson synced from another device lands: the
  ;; screen holds nothing of its own to keep or to reset. The learner and the clock come from `:effect/enter`:
  ;; all the words in ten bins by the percent the word list shows, and the lessons by day from the first.
  (fn show-stats [_ {:keys [learner now-ms]}]
    [[:effect/save
      {:page/current      :page/stats
       :stats/lesson-days (lesson/lesson-days learner now-ms)
       :stats/word-bins   (vocabulary/word-bins learner now-ms)}]]))


(nxr/register-action! :action/share-stats
  (fn share-stats [state {:keys [language]}]
    [[:effect/share-picture (i18n/interpolate language (picture/picture state))]]))

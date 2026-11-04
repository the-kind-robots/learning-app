(ns use-cases.sync
  "Turning the sync on for a device without an account."
  (:require
   [ports.account :as account]
   [ports.learner :as learner]))


(defn request!
  "Asks us to turn the sync on, with the learner's `note`. The word count and the days of practice go with it,
   so whoever grants it sees how far the learner has come (#650). A promise as `ports.account/request-sync!`
   gives."
  [account learner note]
  (account/request-sync! account
                         {:days  (learner/practice-days learner)
                          :note  note
                          :words (learner/word-count learner)}))

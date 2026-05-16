(ns client.support.db-seed
  (:require
   [client.support.time :as time]
   [db :as db]))


(defn ^:async seed-vocabulary!
  "Adds vocabulary words and reviews to test db for testing."
  [db words]
  (await (js/Promise.all
          (into-array (map (fn [{id :_id value :value translation :translation}]
                             (db/insert db
                                        {:_id         id
                                         :type        "vocab"
                                         :value       value
                                         :translation [{:lang "ru" :value translation}]
                                         :created-at  time/test-now-iso
                                         :modified-at time/test-now-iso}))
                           words))))
  (await (js/Promise.all
          (into-array (map (fn [{id :_id}]
                             (db/insert db
                                        {:_id        (str "review-" id)
                                         :type       "review"
                                         :word-id    id
                                         :retained   true
                                         :created-at time/test-now-iso}))
                           words)))))


(defn ^:async seed-examples!
  "Adds example documents to test db for testing."
  [db examples]
  (await (js/Promise.all
          (into-array (map (fn [{id :_id word-id :word-id word :word value :value translation :translation}]
                             (db/insert db
                                        {:_id         id
                                         :type        "example"
                                         :word-id     word-id
                                         :word        word
                                         :value       value
                                         :translation translation
                                         :structure   []
                                         :created-at  time/test-now-iso}))
                           examples)))))

(ns client.support.db-seed
  (:require
   [client.support.time :as time]
   [db :as db]
   [promesa.core :as p]))


(defn seed-vocabulary!
  "Adds vocabulary words and reviews to test db for testing."
  [db words]
  (p/do
    (p/all (map (fn [{:keys [_id value translation]}]
                  (db/insert db
                             {:_id         _id
                              :type        "vocab"
                              :value       value
                              :translation [{:lang "ru" :value translation}]
                              :created-at  time/test-now-iso
                              :modified-at time/test-now-iso}))
                words))
    (p/all (map (fn [{:keys [_id]}]
                  (db/insert db
                             {:_id        (str "review-" _id)
                              :type       "review"
                              :word-id    _id
                              :retained   true
                              :created-at time/test-now-iso}))
                words))))


(defn seed-examples!
  "Adds example documents to test db for testing."
  [db examples]
  (p/all
   (map (fn [{:keys [_id word-id word value translation source structure]}]
          (let [source    (or source "ai")
                structure (or structure
                              [{:usedForm       (or word value)
                                :dictionaryForm (or word value)
                                :translation    translation
                                :wordIndex      0}])]
            (db/insert db
                       (cond-> {:_id         _id
                                :type        "example"
                                :word-id     word-id
                                :word        word
                                :value       value
                                :translation translation
                                :source      source
                                :created-at  time/test-now-iso
                                :modified-at time/test-now-iso}
                         (= "ai" source)
                         (assoc :structure structure)))))
        examples)))

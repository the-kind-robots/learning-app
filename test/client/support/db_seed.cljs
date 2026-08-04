(ns client.support.db-seed
  (:require
   [client.support.time :as time]
   [db :as db]
   [domain.vocabulary :as vocabulary]))


(defn- ^:async insert-all!
  [db docs]
  (await (js/Promise.all (into-array (map #(db/insert db %) docs)))))


(defn- vocab-doc
  "Vocab doc, defaulting :_id to its content-addressed id when not given."
  [{id :_id value :value translation :translation}]
  {:_id         (or id (vocabulary/vocab-id value))
   :type        "vocab"
   :value       value
   :translation [{:lang "ru" :value translation}]
   :created-at  time/test-now-iso
   :modified-at time/test-now-iso})


(defn- review-doc
  [{id :_id}]
  {:_id        (str "review-" id)
   :type       "review"
   :word-id    id
   :retained   true
   :created-at time/test-now-iso})


(defn- example-doc
  [{id :_id word-id :word-id word :word value :value translation :translation}]
  {:_id         id
   :type        "example"
   :word-id     word-id
   :word        word
   :value       value
   :translation translation
   :structure   []
   :created-at  time/test-now-iso})


(defn ^:async seed-vocabulary!
  "Adds vocabulary words and a passing review for each to a test db."
  [db words]
  (let [docs (mapv vocab-doc words)]
    (await (insert-all! db docs))
    (await (insert-all! db (map review-doc docs)))))


(defn ^:async seed-examples!
  "Adds example documents to a test db."
  [db examples]
  (await (insert-all! db (map example-doc examples))))

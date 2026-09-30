(ns adapters.reviews
  "Repository of reviews: one document per graded trial of a word."
  (:require
   [adapters.repository :as repository]
   [db.pouch :as dbs]
   [domain.vocabulary :as vocabulary]))


(def schema
  {:type    "review"
   :db      :user/db
   :indexes [{:name "by-type-word-id" :fields [:type :word-id]}]})


(defn save-review!
  [dbs clock word-id retained translation]
  (dbs/insert dbs schema (repository/stamp-created clock (vocabulary/new-review word-id retained translation))))


(defn ^:async tombstones-of
  "The reviews of `word-id` as deletions, for the bulk write that removes
   the word — so word and reviews go in one atomic write."
  [dbs word-id]
  (let [{reviews :docs} (await (dbs/find-all dbs schema {:selector {:word-id word-id}}))]
    (mapv repository/tombstone reviews)))

(ns adapters.reviews
  "Repository of reviews: one document per graded trial of a word."
  (:require
   [adapters.repository :as repository]
   [db.pouch :as dbs]
   [domain.vocabulary :as vocabulary]))


(def schema
  {:type "review"
   :db   :user/db})


(defn ^:async reviews-by-word
  "Reviews as `{:word-id :created-at :retained}`, grouped by word id. With
   `word-ids` only those words' reviews are read; nil reads every review."
  [dbs word-ids]
  (let [{reviews :docs} (await (dbs/find-all dbs
                                             schema
                                             (cond-> {}
                                               word-ids (assoc :selector {:word-id {:$in (vec word-ids)}}))))]
    (->> reviews
         (map (fn [{:keys [word-id created-at retained]}]
                {:created-at created-at
                 :retained   retained
                 :word-id    word-id}))
         (group-by :word-id))))


(defn save-review!
  [dbs clock word-id retained translation]
  (dbs/insert dbs schema (repository/stamp-created clock (vocabulary/new-review word-id retained translation))))


(defn ^:async tombstones-of
  "The reviews of `word-id` as deletions, for the bulk write that removes
   the word — so word and reviews go in one atomic write."
  [dbs word-id]
  (let [{reviews :docs} (await (dbs/find-all dbs schema {:selector {:word-id word-id}}))]
    (mapv repository/tombstone reviews)))

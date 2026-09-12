(ns adapters.reviews
  "Repository of reviews: one document per graded trial of a word."
  (:require
   [db.pouch :as dbs]
   [domain.vocabulary :as vocabulary]))


(def schema
  {:type    "review"
   :db      :user/db
   :indexes [{:name "by-type-word-id" :fields [:type :word-id]}]
   :views   {"reviews-by-word"
             {:map
              "function (doc) { if (doc.type === 'review') emit(doc.word_id, [doc.created_at, doc.retained]); }"}}})


(def ^:private by-word-view
  "One row per review, keyed by word id, valued `[created_at retained]` —
   what retention needs, without fetching the review documents."
  (dbs/view schema "reviews-by-word"))


(defn- stamp
  [clock review]
  (cond-> review
    (nil? (:created-at review)) (assoc :created-at ((:clock/now-iso clock)))))


(defn ^:async reviews-by-word
  "Reviews as `{:word-id :created-at :retained}`, grouped by word id. With
   `word-ids` only those words are read, by key; nil reads the whole view,
   which is the cheaper of the two when every word is wanted anyway
   (#404: 800 keys 0.8 s, all rows 1.1 s, 1500 keys 1.5 s)."
  [dbs word-ids]
  (let [{rows :rows} (await (dbs/query dbs
                                       by-word-view
                                       (cond-> {} word-ids (assoc :keys (vec word-ids)))))]
    (->> rows
         (map (fn [{word-id :key [created-at retained] :value}]
                {:created-at created-at
                 :retained   retained
                 :word-id    word-id}))
         (group-by :word-id))))


(defn save-review!
  [dbs clock word-id retained translation]
  (dbs/insert dbs schema (stamp clock (vocabulary/new-review word-id retained translation))))


(defn ^:async tombstones-of
  "The reviews of `word-id` as deletions, for the bulk write that removes
   the word — so word and reviews go in one atomic write."
  [dbs word-id]
  (let [{reviews :docs} (await (dbs/find-all dbs schema {:selector {:word-id word-id}}))]
    (mapv #(assoc % :_deleted true) reviews)))

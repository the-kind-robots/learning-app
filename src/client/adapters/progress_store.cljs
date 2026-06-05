(ns adapters.progress-store
  (:require
   [adapters.data-export :as data-export]
   [clojure.core :as clojure]
   [db.pouch :as dbs]
   [domain.lesson :as lesson]
   [domain.retention :as retention]
   [domain.vocabulary :as vocabulary]
   [utils :as utils]))


(defn- now-iso
  [clock]
  ((:clock/now-iso clock)))


(defn- now-ms
  [clock]
  ((:clock/now-ms clock)))


(defn- find-all
  ([dbs kind]
   (find-all dbs nil kind))
  ([dbs word-id kind]
   (dbs/find-all dbs
                 (cond-> {:selector {:type kind}}
                   (some? word-id) (assoc-in [:selector :word-id] word-id)))))


(defn- ^:async word-retention-levels
  [dbs word-ids now-ms-val]
  (if (seq word-ids)
    (let [{reviews :docs}  (await (dbs/find-all dbs
                                                {:selector {:type    "review"
                                                            :word-id {:$in (vec word-ids)}}}))
          word-id->reviews (group-by :word-id reviews)]
      (mapv (fn [word-id]
              {:word-id word-id
               :retention-level (retention/retention-level
                                 (word-id->reviews word-id [])
                                 now-ms-val)})
            word-ids))
    []))


(defn- ^:async word-retention-level
  [dbs word-id now-ms-val]
  (let [{reviews :docs} (await (dbs/find-all dbs
                                             {:selector {:type    "review"
                                                         :word-id word-id}}))]
    (retention/retention-level reviews now-ms-val)))


(defn- with-retention
  [dbs clock word]
  ((fn ^:async f
     []
     (let [level (await (word-retention-level dbs (:_id word) (now-ms clock)))]
       (assoc word :retention-level level)))))


(defn- stamp-word
  [clock word]
  (let [now (now-iso clock)]
    (cond-> (assoc word :modified-at now)
      (nil? (:created-at word)) (assoc :created-at now))))


(defn- stamp-review
  [clock review]
  (cond-> review
    (nil? (:created-at review)) (assoc :created-at (now-iso clock))))


(defn- stamp-lesson
  [clock lesson-state]
  (cond-> lesson-state
    (nil? (:started-at lesson-state)) (assoc :started-at (now-iso clock))))


(defn ^:async find-word-by-value
  [dbs value]
  (await (dbs/get dbs "vocab" (vocabulary/vocab-id value))))


(defn ^:async get-word
  [dbs clock word-id]
  (when-let [word (await (dbs/get dbs "vocab" word-id))]
    (await (with-retention dbs clock word))))


(defn ^:async list-words
  [dbs clock
   {:keys [order limit offset search word-ids]
    :or   {order :desc}}]
  (let [{all-docs :docs} (await (find-all dbs "vocab"))
        docs        (cond->> all-docs
                      (some? word-ids) (filter #((set word-ids) (:_id %))))
        total-count (clojure/count docs)
        retention-levels (await (word-retention-levels dbs (mapv :_id docs) (now-ms clock)))
        word-id->retention (->> retention-levels
                                (map (juxt :word-id :retention-level))
                                (into {}))
        words       (cond->> docs
                      (utils/non-blank search)
                      (filter (fn [{:keys [value translation]}]
                                (or (utils/includes? value search)
                                    (some #(utils/includes? (:value %) search) translation)))))
        words       (->> words
                         (map (fn [word]
                                (assoc word :retention-level (word-id->retention (:_id word) 0))))
                         (sort-by :retention-level (if (= order :asc) < >)))
        words       (cond->> words
                      offset (drop offset)
                      limit  (take limit))]
    {:total total-count
     :words (vec words)}))


(defn ^:async count-words
  [dbs]
  (let [{docs :docs} (await (find-all dbs "vocab"))]
    (clojure/count docs)))


(defn save-word!
  [dbs clock word]
  (dbs/insert dbs (stamp-word clock word)))


(defn save-review!
  [dbs clock word-id retained translation]
  (dbs/insert dbs (stamp-review clock (vocabulary/new-review word-id retained translation))))


(defn ^:async delete-word!
  "Atomically removes the word and its reviews from user-db, scrubbing the
   word-id from every collection's :word-ids in the same bulk write. Examples
   live in device-db and are deleted as a follow-up best-effort bulk write."
  [dbs word-id]
  (when-let [word (await (dbs/get dbs "vocab" word-id))]
    (let [{reviews :docs}  (await (find-all dbs word-id "review"))
          {examples :docs} (await (find-all dbs word-id "example"))
          {collections :docs} (await (dbs/find dbs {:selector {:type "collection"}}))
          tombstone        #(assoc % :_deleted true)
          updated-collections (->> collections
                                   (filter #(some #{word-id} (:word-ids %)))
                                   (mapv #(update %
                                                  :word-ids
                                                  (partial filterv (complement #{word-id})))))
          user-bulk        (-> [(tombstone word)]
                               (into (map tombstone) reviews)
                               (into updated-collections))]
      (await (dbs/bulk-docs dbs "vocab" user-bulk))
      (when (seq examples)
        (await (dbs/bulk-docs dbs "example" (mapv tombstone examples)))))))


(defn get-lesson
  [dbs]
  (dbs/get dbs "lesson" lesson/lesson-id))


(defn save-lesson!
  [dbs clock lesson-state]
  (dbs/insert dbs (stamp-lesson clock lesson-state)))


(defn ^:async remove-lesson!
  [dbs]
  (when-let [lesson-state (await (get-lesson dbs))]
    (await (dbs/remove dbs lesson-state))))


(defn export-data!
  [dbs]
  (data-export/export-data! dbs))


(defn import-data!
  [dbs payload]
  (data-export/import-data! dbs payload))

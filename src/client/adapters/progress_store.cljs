(ns adapters.progress-store
  (:require
   [adapters.collections :as collections]
   [adapters.data-export :as data-export]
   [clojure.core :as clojure]
   [db.pouch :as dbs]
   [domain.lesson :as lesson]
   [domain.retention :as retention]
   [domain.vocabulary :as vocabulary]
   [utils :as utils]))


(def word-schema
  "Words and phrases are one document type that differ by `:kind`. `by-type`
   serves every type-selected query on user-db, not only this one."
  {:type    "vocab"
   :db      :user/db
   :indexes [{:name "by-type" :fields [:type]}]
   :views   {"vocab-preview"
             {:map
              "function (doc) { if (doc.type === 'vocab') emit(doc._id, [doc.kind, doc.value, doc.translation]); }"}}})


(def review-schema
  {:type    "review"
   :db      :user/db
   :indexes [{:name "by-type-word-id" :fields [:type :word-id]}]
   :views   {"reviews-by-word"
             {:map
              "function (doc) { if (doc.type === 'review') emit(doc.word_id, [doc.created_at, doc.retained]); }"}}})


(def lesson-schema
  {:type "lesson"
   :db   :device/db})


(def ^:private vocab-preview-view
  "One row per word, keyed by id, valued `[kind value translation]` — what a
   list of words shows, without fetching the documents."
  (dbs/view word-schema "vocab-preview"))


(def ^:private reviews-by-word-view
  "One row per review, keyed by word id, valued `[created_at retained]` —
   what retention needs, without fetching the review documents."
  (dbs/view review-schema "reviews-by-word"))


(defn- now-iso
  [clock]
  ((:clock/now-iso clock)))


(defn- now-ms
  [clock]
  ((:clock/now-ms clock)))


(defn ^:async reviews-by-word
  "Reviews as `{:word-id :created-at :retained}`, grouped by word id. With
   `word-ids` only those words are read, by key; nil reads the whole view,
   which is the cheaper of the two when every word is wanted anyway
   (#404: 800 keys 0.8 s, all rows 1.1 s, 1500 keys 1.5 s)."
  [dbs word-ids]
  (let [{rows :rows} (await (dbs/query dbs
                                       reviews-by-word-view
                                       (cond-> {} word-ids (assoc :keys (vec word-ids)))))]
    (->> rows
         (map (fn [{word-id :key [created-at retained] :value}]
                {:created-at created-at
                 :retained   retained
                 :word-id    word-id}))
         (group-by :word-id))))


(defn ^:async vocab-previews
  "Every word and phrase as `{:_id :kind :value :translation}` — what a list
   shows — or only `word-ids` when given. Read from the vocab view, so no
   document is fetched."
  [dbs word-ids]
  (let [{rows :rows} (await (dbs/query dbs vocab-preview-view {}))
        wanted-ids   (some-> word-ids set)]
    (->> rows
         (filter (fn [{id :id}]
                   (or (nil? wanted-ids) (contains? wanted-ids id))))
         (mapv (fn [{id :id [kind value translation] :value}]
                 {:_id         id
                  :kind        kind
                  :translation translation
                  :value       value})))))


(defn- ^:async word-retention-level
  [dbs word-id now-ms-val]
  (let [{reviews :docs} (await (dbs/find-all dbs review-schema {:selector {:word-id word-id}}))]
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
  (await (dbs/get dbs word-schema (vocabulary/vocab-id value))))


(defn ^:async get-word
  [dbs clock word-id]
  (when-let [word (await (dbs/get dbs word-schema word-id))]
    (await (with-retention dbs clock word))))


(defn ^:async list-words
  [dbs clock
   {:keys [order limit offset search word-ids]
    :or   {order :desc}}]
  (let [words        (await (vocab-previews dbs word-ids))
        total-count  (clojure/count words)
        ;; The page is sorted by retention, so every candidate needs its
        ;; level; only words the filters exclude are spared the lookup.
        candidates   (cond->> words
                       (utils/non-blank search)
                       (filter (fn [{:keys [value translation]}]
                                 (or (utils/includes? value search)
                                     (some #(utils/includes? (:value %) search) translation)))))
        ;; A narrowed list reads its reviews by key; the whole vocabulary reads
        ;; the whole review view, which is cheaper than that many keys.
        narrowed-ids (when (or (some? word-ids) (utils/non-blank search))
                       (mapv :_id candidates))
        word-id->reviews (await (reviews-by-word dbs narrowed-ids))
        now          (now-ms clock)
        words        (->> candidates
                          (map (fn [word]
                                 (assoc word
                                        :retention-level
                                        (retention/retention-level (word-id->reviews (:_id word) []) now))))
                          (sort-by :retention-level (if (= order :asc) < >)))
        words        (cond->> words
                       offset (drop offset)
                       limit  (take limit))]
    {:total total-count
     :words (vec words)}))


(defn ^:async count-words
  [dbs]
  (clojure/count (await (vocab-previews dbs nil))))


(defn save-word!
  [dbs clock word]
  (dbs/insert dbs word-schema (stamp-word clock word)))


(defn save-review!
  [dbs clock word-id retained translation]
  (dbs/insert dbs review-schema (stamp-review clock (vocabulary/new-review word-id retained translation))))


(defn ^:async delete-word!
  "Atomically removes the word and its reviews from user-db, scrubbing the
   word-id from every collection's :word-ids in the same bulk write. The
   collections adapter hands over its updated documents so one write covers
   the three types. Examples live in device-db; the use-case purges them
   afterwards, best effort. Returns true when a word was deleted."
  [dbs word-id]
  (when-let [word (await (dbs/get dbs word-schema word-id))]
    (let [{reviews :docs} (await (dbs/find-all dbs review-schema {:selector {:word-id word-id}}))
          tombstone       #(assoc % :_deleted true)
          user-bulk       (-> [(tombstone word)]
                              (into (map tombstone) reviews)
                              (into (await (collections/without-word dbs word-id))))]
      (await (dbs/bulk-docs dbs word-schema user-bulk))
      true)))


(defn get-lesson
  [dbs]
  (dbs/get dbs lesson-schema lesson/lesson-id))


(defn save-lesson!
  [dbs clock lesson-state]
  (dbs/insert dbs lesson-schema (stamp-lesson clock lesson-state)))


(defn ^:async remove-lesson!
  [dbs]
  (when-let [lesson-state (await (get-lesson dbs))]
    (await (dbs/remove dbs lesson-schema lesson-state))))


(defn export-data!
  [dbs]
  (data-export/export-data! dbs))


(defn import-data!
  [dbs payload]
  (data-export/import-data! dbs payload))

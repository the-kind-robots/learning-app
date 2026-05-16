(ns use-cases.vocabulary
  (:refer-clojure :exclude [list count get])
  (:require
   [clojure.core :as clojure]
   [dbs :as dbs]
   [domain.vocabulary :as domain]
   [repo.retention :as retention]
   [utils :as utils]))


(defn- find-all
  ([dbs kind]
   (find-all dbs nil kind))
  ([dbs word-id kind]
   (dbs/find-all dbs
                 (cond-> {:selector {:type kind}}
                   (some? word-id) (assoc-in [:selector :word-id] word-id)))))


(defn ^:async find-duplicate
  "Find an existing vocab doc whose normalized value matches `normalized`."
  [dbs normalized]
  (let [{docs :docs} (await (find-all dbs "vocab"))]
    (first (filter #(= normalized (domain/normalize-value (:value %))) docs))))


(defn ^:async add!
  "Adds a new vocabulary word with an initial review.
   If a duplicate exists (case-insensitive, article-stripped), merges translations.
   Returns {:word-id id :created? true/false}."
  [dbs value translation]
  (let [now-iso    (utils/now-iso)
        parsed     (domain/parse-translations translation)
        normalized (domain/normalize-value value)]
    (if (empty? parsed)
      {:error :empty-translations}
      (let [existing (await (find-duplicate dbs normalized))]
        (if existing
          (let [merged  (domain/merge-translations (:translation existing) parsed)
                updated (assoc existing :translation merged :modified-at now-iso)]
            (await (dbs/insert dbs updated))
            {:word-id (:_id existing) :created? false})
          (let [word         (domain/new-word value parsed now-iso)
                {:keys [id]} (await (dbs/insert dbs word))
                review       (domain/new-review id true translation now-iso)]
            (await (dbs/insert dbs review))
            {:word-id id :created? true}))))))


(defn find-duplicate-by-value
  "Return existing vocab doc whose normalized value matches value."
  [dbs value]
  (find-duplicate dbs (domain/normalize-value value)))


(defn ^:async get
  "Returns a word row by id, or nil if not found."
  [dbs word-id]
  (let [word (await (dbs/get dbs "vocab" word-id))]
    (when word
      (let [retention-level (await (retention/level dbs word-id))]
        (assoc word :retention-level retention-level)))))


(defn ^:async list
  "Returns vocabulary rows with retention levels."
  ([dbs] (list dbs {}))
  ([dbs
    {:keys [order limit offset search]
     :or   {order :desc}}]
   (let [{docs :docs} (await (find-all dbs "vocab"))
         retention-levels (await (retention/levels dbs (mapv :_id docs)))
         total-count  (clojure/count docs)
         word-id->retention (->> retention-levels
                                 (map (juxt :word-id :retention-level))
                                 (into {}))
         words        (cond->> docs
                        (utils/non-blank search)
                        (filter (fn [{:keys [value translation]}]
                                  (or (utils/includes? value search)
                                      (some #(utils/includes? (:value %) search) translation)))))
         words        (->> words
                           (map (fn [word]
                                  (assoc word :retention-level (word-id->retention (:_id word) 0))))
                           (sort-by :retention-level (if (= order :asc) < >)))
         words        (cond->> words
                        offset (drop offset)
                        limit  (take limit))]
     {:total total-count
      :words (vec words)})))


(defn ^:async count
  "Returns the total number of vocabulary words."
  [dbs]
  (let [{docs :docs} (await (find-all dbs "vocab"))]
    (clojure/count docs)))


(defn ^:async update!
  "Updates a word's translation. Returns updated row, or nil if not found."
  [dbs word-id translation]
  (let [word (await (dbs/get dbs "vocab" word-id))]
    (when word
      (let [word (domain/update-word word translation (utils/now-iso))]
        (await (dbs/insert dbs word))
        (let [retention-level (await (retention/level dbs word-id))]
          (assoc word :_id word-id :retention-level retention-level))))))


(defn ^:async delete!
  "Deletes a word and all its associated reviews and examples.
   No-op if word doesn't exist."
  [dbs word-id]
  (let [word (await (dbs/get dbs "vocab" word-id))]
    (when word
      (let [{reviews :docs}  (await (find-all dbs word-id "review"))
            {examples :docs} (await (find-all dbs word-id "example"))]
        (await (js/Promise.all (into-array (map #(dbs/remove dbs %) reviews))))
        (await (js/Promise.all (into-array (map #(dbs/remove dbs %) examples))))
        (await (dbs/remove dbs word))))))


(defn add-review
  "Creates a review document for a word and updates its retention model."
  [dbs word-id retained translation]
  (let [review (domain/new-review word-id retained translation (utils/now-iso))]
    (dbs/insert dbs review)))

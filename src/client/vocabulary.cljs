(ns vocabulary
  (:refer-clojure :exclude [list count get])
  (:require
   [clojure.core :as clojure]
   [dbs :as dbs]
   [domain.vocabulary :as domain]
   [promesa.core :as p]
   [retention :as retention]
   [utils :as utils]))


(defn- find-all
  ([dbs kind]
   (find-all dbs nil kind))
  ([dbs word-id kind]
   (p/let [{docs :docs} (dbs/find-all dbs
                                      (cond-> {:selector {:type kind}}
                                        (some? word-id) (assoc-in [:selector :word-id] word-id)))]
     docs)))


(defn- find-duplicate
  "Find an existing vocab doc whose normalized value matches `normalized`."
  [dbs normalized]
  (p/let [docs (find-all dbs "vocab")]
    (first (filter #(= normalized (domain/normalize-value (:value %))) docs))))


(defn add!
  "Adds a new vocabulary word with an initial review.
   If a duplicate exists (case-insensitive, article-stripped), merges translations.
   Returns {:word-id id :created? true/false}."
  [dbs value translation]
  (let [now-iso       (utils/now-iso)
        parsed        (domain/parse-translations translation)
        normalized    (domain/normalize-value value)]
    (if (empty? parsed)
      (p/resolved {:error :empty-translations})
    (p/let [existing (find-duplicate dbs normalized)]
      (if existing
        (let [merged  (domain/merge-translations (:translation existing) parsed)
              updated (assoc existing :translation merged, :modified-at now-iso)]
          (p/let [_ (dbs/insert dbs updated)]
            {:word-id (:_id existing) :created? false}))
        (let [word (domain/new-word value parsed now-iso)]
          (p/let [{:keys [id]} (dbs/insert dbs word)
                  review       (domain/new-review id true translation now-iso)]
            (dbs/insert dbs review)
            {:word-id id :created? true})))))))


(defn find-duplicate-by-value
  "Return existing vocab doc whose normalized value matches value."
  [dbs value]
  (find-duplicate dbs (domain/normalize-value value)))


(defn get
  "Returns a word row by id, or nil if not found."
  [dbs word-id]
  (p/let [word (dbs/get dbs "vocab" word-id)]
    (when word
      (p/let [retention-level (retention/level dbs word-id)]
        (assoc word :retention-level retention-level)))))


(defn list
  "Returns vocabulary rows with retention levels."
  ([dbs] (list dbs {}))
  ([dbs {:keys [order limit offset search]
         :or   {order  :desc}}]
   (p/let [words            (find-all dbs "vocab")
           retention-levels (retention/levels dbs (mapv :_id words))]

     (let [total-count        (clojure/count words)
           word-id->retention (->> retention-levels
                                   (map (juxt :word-id :retention-level))
                                   (into {}))
           words (cond->> words
                   (utils/non-blank search)
                   (filter (fn [{:keys [value translation]}]
                             (or (utils/includes? value search)
                                 (some #(utils/includes? (:value %) search) translation)))))

           words (->> words
                      (map (fn [word]
                             (assoc word :retention-level (word-id->retention (:_id word) 0))))
                      (sort-by :retention-level (if (= order :asc) < >)))
           words (cond->> words
                   offset   (drop offset)
                   limit    (take limit))]
       {:total total-count
        :words (vec words)}))))


(defn count
  "Returns the total number of vocabulary words."
  [dbs]
  (p/let [words (find-all dbs "vocab")]
    (clojure/count words)))


(defn update!
  "Updates a word's translation. Returns updated row, or nil if not found."
  [dbs word-id translation]
  (p/let [word (dbs/get dbs "vocab" word-id)]
    (when word
      (p/let [word (domain/update-word word translation (utils/now-iso))
              _    (dbs/insert dbs word)
              retention-level (retention/level dbs word-id)]
        (assoc word :_id word-id :retention-level retention-level)))))


(defn delete!
  "Deletes a word and all its associated reviews and examples.
   No-op if word doesn't exist."
  [dbs word-id]
  (p/let [word (dbs/get dbs "vocab" word-id)]
    (when word
      (p/let [reviews  (find-all dbs word-id "review")
              examples (find-all dbs word-id "example")]
        (p/all (map #(dbs/remove dbs %) reviews))
        (p/all (map #(dbs/remove dbs %) examples))
        (dbs/remove dbs word)))))


(defn add-review
  "Creates a review document for a word and updates its retention model."
  [dbs word-id retained translation]
  (let [review (domain/new-review word-id retained translation (utils/now-iso))]
    (p/let [insert-result (dbs/insert dbs review)]
      insert-result)))

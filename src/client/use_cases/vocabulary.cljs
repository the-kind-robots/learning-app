(ns use-cases.vocabulary
  (:refer-clojure :exclude [list count get])
  (:require
   [domain.phrase :as phrase]
   [domain.retention :as retention]
   [domain.vocabulary :as domain]
   [use-cases.collections :as collections]
   [utils :as utils]))


(defn ^:async find-duplicate
  "Find an existing vocab doc with the same value, or nil."
  [{:keys [words]} value]
  (await ((:words/find-by-value words) value)))


(defn ^:async add!
  "Adds a new vocabulary word with an initial review and queues a
   collection-scoped example fetch. If a duplicate exists (case-insensitive,
   article-stripped), merges translations and does not re-fetch examples.
   Returns {:word-id id :created? true/false}."
  [{:keys [collections examples reviews words] :as capabilities} value translation]
  (let [parsed (domain/parse-translations translation)]
    (if (empty? parsed)
      {:error :empty-translations}
      (let [existing (await (find-duplicate capabilities value))]
        (if existing
          (let [merged        (domain/merge-translations (:translation existing) parsed)
                updated       (assoc existing :translation merged)
                collection-id ((:collections/active-id collections))]
            (await ((:words/save! words) updated))
            (when collection-id
              (await ((:collections/add-word! collections) (:id existing) collection-id))
              (when-not (await ((:examples/find examples) (:id existing) collection-id))
                (let [collection-name (:name (await ((:collections/get collections) collection-id)))]
                  ((:examples/request! examples) updated collection-id collection-name))))
            {:word-id (:id existing) :created? false})
          (let [word (domain/new-word value parsed)
                {:keys [id]} (await ((:words/save! words) word))
                collection-id ((:collections/active-id collections))
                collection-name (when collection-id
                                  (:name (await ((:collections/get collections) collection-id))))]
            (await ((:reviews/save! reviews) id true translation))
            (when collection-id
              (await ((:collections/add-word! collections) id collection-id)))
            ((:examples/request! examples) word collection-id collection-name)
            {:word-id id :created? true}))))))


(defn- now-ms
  [{:keys [clock]}]
  ((:clock/now-ms clock)))


(defn ^:async get
  "Returns a word with its retention level, or nil if not found."
  [{:keys [reviews words] :as capabilities} word-id]
  (when-let [word (await ((:words/get words) word-id))]
    (let [reviews (await ((:reviews/by-word reviews) [word-id]))]
      (assoc word :retention-level (retention/retention-level (reviews word-id []) (now-ms capabilities))))))


(defn ^:async list
  "Vocabulary rows with retention levels, sorted by retention (`:order`
   :asc or :desc, default :desc) and paged by `:offset`/`:limit`. `:word-ids`
   restricts to those words, `:search` to values or translations containing
   the text. `:total` counts the words before the search filter, so an empty
   vocabulary and a search with no match tell apart; `:matches` counts them
   after the filter and before the paging, so a caller holding a page can
   tell whether another one follows."
  [{:keys [reviews words] :as capabilities}
   {:keys [order limit offset search word-ids]
    :or   {order :desc}}]
  (let [words        (await ((:words/previews words) word-ids))
        total        (clojure.core/count words)
        ;; The page is sorted by retention, so every candidate needs its
        ;; level; only words the filters exclude are spared the lookup.
        candidates   (cond->> words
                       (utils/non-blank search)
                       (filter (fn [{:keys [value translation]}]
                                 (or (utils/includes? value search)
                                     (some #(utils/includes? (:value %) search) translation)))))
        ;; A narrowed list reads its reviews by key; the whole vocabulary
        ;; reads every review, which is the cheaper of the two when every
        ;; word is wanted anyway (#404, 9000 reviews: 800 keys 0.8 s, all
        ;; rows 1.1 s, 1500 keys 1.5 s).
        narrowed-ids (when (or (some? word-ids) (utils/non-blank search))
                       (mapv :id candidates))
        reviews      (await ((:reviews/by-word reviews) narrowed-ids))
        now          (now-ms capabilities)
        rows         (->> candidates
                          (map (fn [word]
                                 (assoc word
                                        :retention-level
                                        (retention/retention-level (reviews (:id word) []) now))))
                          (sort-by :retention-level (if (= order :asc) < >)))
        matches      (clojure.core/count rows)
        rows         (cond->> rows
                       offset (drop offset)
                       limit  (take limit))]
    {:matches matches
     :total   total
     :words   (vec rows)}))


(defn ^:async list-active
  "Returns vocabulary rows scoped to the active collection — its own words
   and its children's by name (ADR-0013). When no collection is active
   (implicit main card), returns all words like `list`."
  [capabilities opts]
  (let [word-ids (await (collections/active-word-ids capabilities))]
    (await (list capabilities
                 (cond-> opts
                   word-ids (assoc :word-ids word-ids))))))


(defn ^:async count
  "Returns the total number of vocabulary words."
  [{:keys [words]}]
  (await ((:words/count words))))


(defn ^:async update!
  "Updates a word's translation. Returns updated row, or nil if not found."
  [{:keys [words] :as capabilities} word-id translation]
  (when-let [word (await ((:words/get words) word-id))]
    (let [updated (if (phrase/phrase-doc? word)
                    (phrase/update-phrase word translation)
                    (domain/update-word word translation))]
      (await ((:words/save! words) updated))
      (await (get capabilities word-id)))))


(defn ^:async delete!
  "Atomically deletes a word, its reviews and its collection memberships in
   one bulk write — the reviews and collections repositories hand over their
   tombstoned and updated documents — then purges the word's examples, which
   live in another database. No-op if word doesn't exist."
  [{:keys [collections examples reviews words]} word-id]
  (let [companions (into (await ((:reviews/tombstones-of reviews) word-id))
                         (await ((:collections/docs-without-word collections) word-id)))]
    (when (await ((:words/delete! words) word-id companions))
      (await ((:examples/purge-by-word! examples) word-id)))))


(defn ^:async remove-from-active!
  "User-initiated 'remove word' that respects the active scope:
   from a named collection, only the membership is dropped — the word
   itself stays in vocabulary; from the implicit main, the word and all
   its associated data are permanently deleted."
  [{:keys [collections] :as capabilities} word-id]
  (if-let [collection-id ((:collections/active-id collections))]
    (await ((:collections/exclude-word! collections) word-id collection-id))
    (await (delete! capabilities word-id))))


(defn add-review
  "Creates a review document for a word and updates its retention model."
  [{:keys [reviews]} word-id retained translation]
  ((:reviews/save! reviews) word-id retained translation))

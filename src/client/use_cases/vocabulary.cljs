(ns use-cases.vocabulary
  (:require
   [domain.collections :as collections]
   [domain.phrase :as phrase]
   [domain.retention :as retention]
   [domain.vocabulary :as domain]
   [ports.learner :as learner-port]
   [utils :as utils]))


(defn find-duplicate
  "The word or phrase memory has under the same value, or nil."
  [{:keys [learner]} value]
  (learner-port/word ((:learner/memory learner)) (domain/vocab-id value)))


(defn add-review
  "Writes a review of a word. Resolves once memory has it."
  [{:keys [learner world]} word-id retained translation]
  ((:learner/insert! learner)
   :entity/review
   (assoc (domain/new-review word-id retained translation)
          :created-at
          ((:clock/now-iso (:world/clock world))))))


(defn ^:async add!
  "Adds an entry of `kind` (`:word` or `:phrase`) to the vocabulary and the active collection; a new word
   gets an initial review unless memory holds its history, and a duplicate value merges translations.
   Resolves with `{:word-id :created?}` or `{:error :empty-translations}`."
  ;; `kind` has no default: optional, `:static-fns` compiles call sites to an arity property a plain test stub lacks.
  [{:keys [learner] :as capabilities} value translation kind pos]
  (let [translation (domain/entered-translation kind translation)
        entry       (domain/entered-entry kind value translation pos)]
    (if-not entry
      {:error :empty-translations}
      (let [collection ((:learner/active-collection learner))
            created?   (volatile! false)
            _          (await ((:learner/change! learner)
                               [[:entity/word
                                 (:id entry)
                                 (fn [stored]
                                   (vreset! created? (nil? stored))
                                   (if stored
                                     (domain/merged-entry stored entry)
                                     entry))]]))
            created?   @created?
            history    (learner-port/review-history ((:learner/memory learner)) (:id entry))]
        (when (and created? (zero? (count (:ids history))))
          (await (add-review capabilities (:id entry) true translation)))
        (when collection
          (await ((:learner/change! learner)
                  [[:entity/collection
                    (:id collection)
                    #(some-> % (collections/with-word (:id entry)))]])))
        {:created? created? :word-id (:id entry)}))))


(defn rows
  "The word list's rows out of memory, paged by `:offset`/`:limit`, narrowed by `:collection` and `:search`,
   each with its retention level. `:total` counts the words before the search, `:matches` after it."
  [memory {:keys [collection limit offset search]} now-ms]
  (let [scope   (learner-port/collection-words memory collection)
        query   (when (utils/non-blank search) (domain/query search))
        matched (learner-port/matching-words memory collection query)
        page    (cond->> matched
                  offset (drop offset)
                  limit  (take limit))]
    {:matches (count matched)
     :total   (count scope)
     :words   (mapv #(assoc (select-keys % [:id :kind :translation :value])
                            :retention-level
                            (retention/level (retention/urgency (learner-port/review-history memory (:id %))
                                                                now-ms)))
                    page)}))


(defn ^:async update!
  "Updates a word's translation. Resolves with the updated word, once
   memory has it, or nil when there is no such word."
  [{:keys [learner]} word-id translation]
  (when (await ((:learner/change! learner)
                [[:entity/word
                  word-id
                  (fn [word]
                    (cond
                      (nil? word)           nil
                      (phrase/phrase? word) (phrase/update-phrase word translation)
                      :else                 (domain/update-word word translation)))]]))
    (learner-port/word ((:learner/memory learner)) word-id)))


(defn ^:async delete!
  "Deletes a word and its place in every collection in one write; its reviews and examples stay. Resolves
   true when it wrote, nil when there was nothing to write, false when the write was taken back."
  [{:keys [learner]} word-id]
  (let [memory  (await ((:learner/fresh-memory learner)))
        listing (learner-port/collections-of memory word-id)]
    (await ((:learner/change! learner)
            (into [[:entity/word word-id (constantly :entity/deleted)]]
                  (map (fn [{:keys [id]}]
                         [:entity/collection id #(some-> % (collections/without-word word-id))]))
                  listing)))))


(defn ^:async remove-from-active!
  "Removes a word within the active scope: from a named collection and its children only the membership
   goes; from the implicit main the word and its place in every collection."
  [{:keys [learner] :as capabilities} word-id]
  (if-let [collection ((:learner/active-collection learner))]
    (let [scope (collections/family (learner-port/collections ((:learner/memory learner)))
                                    (:id collection))]
      (await ((:learner/change! learner)
              (mapv (fn [id]
                      [:entity/collection id #(some-> % (collections/without-word word-id))])
                    (or (seq (map :id scope)) [(:id collection)])))))
    (await (delete! capabilities word-id))))

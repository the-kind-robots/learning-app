(ns use-cases.vocabulary
  (:require
   [clojure.string :as str]
   [domain.collections :as collections]
   [domain.phrase :as phrase]
   [domain.retention :as retention]
   [domain.vocabulary :as domain]
   [ports.learner :as learner]
   [ports.world :as world]
   [utils :as utils]))


(defn find-duplicate
  "The word or phrase memory has under the same value, or nil."
  [learner value]
  (learner/word (learner/memory learner) (domain/vocab-id value)))


(defn add-review
  "Writes a review of a word. Resolves once memory has it."
  [learner world word-id retained translation]
  (learner/insert! learner
                   :entity/review
                   (assoc (domain/new-review word-id retained translation)
                          :created-at
                          (world/clock world :now-iso))))


(defn ^:async add!
  "Adds an entry of `kind` (`:word` or `:phrase`) to the vocabulary and the active collection; a new word
   gets an initial review unless memory holds its history, and a duplicate value merges translations.
   Resolves with `{:word-id :created?}` or `{:error :failure/empty-translations}`."
  ;; `kind` has no default: optional, `:static-fns` compiles call sites to an arity property a plain test stub lacks.
  [learner world value translation kind pos]
  (let [translation (domain/entered-translation kind translation)
        entry       (domain/entered-entry kind value translation pos)]
    (if-not entry
      {:error :failure/empty-translations}
      (let [collection (learner/active-collection learner)
            created?   (volatile! false)
            _          (await (learner/change! learner
                                               [[:entity/word
                                                 (:id entry)
                                                 (fn [stored]
                                                   (vreset! created? (nil? stored))
                                                   (if stored
                                                     (domain/merged-entry stored entry)
                                                     entry))]]))
            created?   @created?
            history    (learner/review-history (learner/memory learner) (:id entry))]
        (when (and created? (zero? (count (:ids history))))
          (await (add-review learner world (:id entry) true translation)))
        (when collection
          (await (learner/change! learner
                                  [[:entity/collection
                                    (:id collection)
                                    #(some-> % (collections/with-word (:id entry)))]])))
        {:created? created? :word-id (:id entry)}))))


(defn rows
  "The word list's rows out of memory, paged by `:offset`/`:limit`, narrowed by `:collection` and `:search`,
   each with its retention level. `:total` counts the words before the search, `:matches` after it."
  [memory {:keys [collection limit offset search]} now-ms]
  (let [scope   (learner/collection-words memory collection)
        query   (when (utils/non-blank search) (domain/query search))
        matched (learner/matching-words memory collection query)
        page    (cond->> matched
                  offset (drop offset)
                  limit  (take limit))]
    {:matches (count matched)
     :total   (count scope)
     :words   (mapv #(assoc (select-keys % [:id :kind :translation :value])
                            :retention-level
                            (retention/level (retention/urgency (learner/review-history memory (:id %))
                                                                now-ms)))
                    page)}))


(defn spelling-taken?
  "True when `value` is the spelling of a word other than `word-id`, as `memory` has it."
  [memory word-id value]
  (let [id (domain/vocab-id value)]
    (and (not= id word-id)
         (some? (learner/word memory id)))))


(defn ^:async update!
  "Edits the word `word-id` in place: its `value` (the same vocab id, spelled differently) and `translation`.
   Resolves with whether memory took the change."
  [learner word-id value translation]
  (await (learner/change! learner
                          [[:entity/word word-id #(some-> % (phrase/edited-word value translation))]])))


(defn ^:async rename!
  "Makes a new word of `value` and `translation` in the place of the word `word-id`: the same collections, the
   old word gone. A new word gets an initial review unless memory holds its history. Resolves with whether
   memory took the change."
  [learner world word-id value translation]
  (let [kind        (if (phrase/phrase-value? value []) :phrase :word)
        translation (domain/entered-translation kind translation)
        entry       (domain/entered-entry kind (str/trim value) translation nil)
        memory      (await (learner/fresh-memory learner))]
    (if (or (nil? entry) (nil? (learner/word memory word-id)))
      false
      (let [written (await (learner/change! learner
                                            (-> [[:entity/word (:id entry) #(or % entry)]
                                                 [:entity/word word-id (constantly :entity/deleted)]]
                                                (into (map (fn [{:keys [id]}]
                                                             [:entity/collection
                                                              id
                                                              #(some-> % (collections/replace-word word-id (:id entry)))]))
                                                      (learner/collections-of memory word-id)))))]
        (when (and written (zero? (count (:ids (learner/review-history memory (:id entry))))))
          (await (add-review learner world (:id entry) true translation)))
        written))))


(defn ^:async delete!
  "Deletes a word and its place in every collection in one write; its reviews and examples stay. Resolves
   true when it wrote, nil when there was nothing to write, false when the write was taken back."
  [learner word-id]
  (let [memory  (await (learner/fresh-memory learner))
        listing (learner/collections-of memory word-id)]
    (await (learner/change! learner
                            (into [[:entity/word word-id (constantly :entity/deleted)]]
                                  (map (fn [{:keys [id]}]
                                         [:entity/collection id #(some-> % (collections/without-word word-id))]))
                                  listing)))))


(defn ^:async remove-from-active!
  "Removes a word within the active scope: from a named collection and its children only the membership
   goes; from the implicit main the word and its place in every collection."
  [learner word-id]
  (if-let [collection (learner/active-collection learner)]
    (let [scope (collections/family (learner/collections (learner/memory learner))
                                    (:id collection))]
      (await (learner/change! learner
                              (mapv (fn [id]
                                      [:entity/collection id #(some-> % (collections/without-word word-id))])
                                    (or (seq (map :id scope)) [(:id collection)])))))
    (await (delete! learner word-id))))

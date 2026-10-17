(ns use-cases.vocabulary
  (:require
   [domain.collections :as collections]
   [domain.phrase :as phrase]
   [domain.retention :as retention]
   [domain.vocabulary :as domain]
   [utils :as utils]))


(defn find-duplicate
  "The word or phrase memory has under the same value, or nil."
  [{:keys [learner]} value]
  ((:learner/word learner) ((:learner/memory learner)) (domain/vocab-id value)))


(defn add-review
  "Writes a review of a word. Resolves once memory has it."
  [{:keys [learner world]} word-id retained translation]
  ((:learner/insert! learner)
   :entity/review
   (assoc (domain/new-review word-id retained translation)
          :created-at
          ((:clock/now-iso (:world/clock world))))))


(defn ^:async add!
  "Adds a vocabulary entry of `kind` — `:word` or `:phrase` — to the
   vocabulary and to the active collection. The kind decides only how the
   translation is read and which document is built. The example backfill
   then asks for the new pair; a phrase gets one like a word (#371).

   A new word gets an initial review, unless memory holds reviews of it
   already: a word deleted and added again comes back with its history,
   and its retention is computed from that history alone. A duplicate value
   is one entry whatever its kind: translations merge, and the kind is left
   alone. `pos` is the part of speech of the suggestion the word was picked
   from, or nil; a word keeps it for the example request, and a phrase does
   not. Returns a promise of {:word-id id :created? bool} or {:error
   :empty-translations}.

   `kind` has no default on purpose. Made optional, this becomes a two-arity
   function, and `:static-fns` then compiles every call site to
   `add_BANG_.cljs$core$IFn$_invoke$arity$4` — a property a plain test stub
   does not carry, so `with-redefs` stops intercepting and the effect throws
   where it used to run."
  [{:keys [learner] :as capabilities} value translation kind pos]
  (let [translation (domain/entered-translation kind translation)
        entry       (domain/entered-entry kind value translation pos)]
    (if-not entry
      {:error :empty-translations}
      (let [collection ((:learner/active-collection learner))
            created?   (volatile! false)
            word       (await ((:learner/change! learner)
                               :entity/word
                               (:id entry)
                               (fn [stored]
                                 (vreset! created? (nil? stored))
                                 (if stored
                                   (domain/merged-entry stored entry)
                                   entry))))
            created?   @created?
            history    ((:learner/review-history learner) ((:learner/memory learner)) (:id word))]
        (when (and created? (zero? (count (:ids history))))
          (await (add-review capabilities (:id word) true translation)))
        (when collection
          (await ((:learner/change! learner)
                  :entity/collection
                  (:id collection)
                  #(some-> % (collections/with-word (:id word))))))
        {:created? created? :word-id (:id word)}))))


(defn rows
  "The word list's rows out of memory, paged by `:offset`/`:limit`. Each row
   carries its retention level. `:collection` narrows the rows to a
   collection, and `:search` to the words holding the text. `:total` counts
   the words before the search, `:matches` after it."
  [learner memory {:keys [collection limit offset search]} now-ms]
  (let [scope   ((:learner/collection-words learner) memory collection)
        query   (when (utils/non-blank search) (domain/query search))
        matched ((:learner/matching-words learner) memory collection query)
        page    (cond->> matched
                  offset (drop offset)
                  limit  (take limit))]
    {:matches (count matched)
     :total   (count scope)
     :words   (mapv #(assoc (select-keys % [:id :kind :translation :value])
                            :retention-level
                            (retention/level (retention/urgency ((:learner/review-history learner) memory (:id %))
                                                                now-ms)))
                    page)}))


(defn update!
  "Updates a word's translation. Resolves with the updated word, once
   memory has it, or nil when there is no such word."
  [{:keys [learner]} word-id translation]
  ((:learner/change! learner)
   :entity/word
   word-id
   (fn [word]
     (cond
       (nil? word)               nil
       (phrase/phrase? word) (phrase/update-phrase word translation)
       :else                     (domain/update-word word translation)))))


(defn ^:async delete!
  "Deletes a word and its place in every collection, in one write. Its
   reviews and its examples stay, and come back if the word is added again.
   Resolves true when it wrote, nil when there was nothing to write, and
   false when the write failed and was taken back."
  [{:keys [learner]} word-id]
  (let [memory  (await ((:learner/fresh-memory learner)))
        listing ((:learner/collections-of learner) memory word-id)]
    (await ((:learner/change-all! learner)
            (into [[:entity/word word-id (constantly :entity/deleted)]]
                  (map (fn [{:keys [id]}]
                         [:entity/collection id #(some-> % (collections/without-word word-id))]))
                  listing)))))


(defn ^:async remove-from-active!
  "User-initiated 'remove word' that respects the active scope:
   from a named collection and its children, only the membership is dropped
   — the word itself stays in vocabulary; from the implicit main, the word
   and its place in every collection are deleted."
  [{:keys [learner] :as capabilities} word-id]
  (if-let [collection ((:learner/active-collection learner))]
    (let [scope (collections/family ((:learner/collections learner) ((:learner/memory learner)))
                                    (:id collection))]
      (await ((:learner/change-all! learner)
              (mapv (fn [id]
                      [:entity/collection id #(some-> % (collections/without-word word-id))])
                    (or (seq (map :id scope)) [(:id collection)])))))
    (await (delete! capabilities word-id))))

(ns use-cases.vocabulary
  (:require
   [clojure.string :as str]
   [domain.collections :as collections]
   [domain.phrase :as phrase]
   [domain.retention :as retention]
   [domain.statistics :as statistics]
   [domain.vocabulary :as vocabulary]
   [ports.learner :as learner]
   [ports.world :as world]
   [utils :as utils]))


(defn empty-in?
  "True when `collection` (nil for all) holds no word the learner has. The words in scope are counted as the
   word list and the lesson count them: a collection may still list a word deleted elsewhere."
  [learner collection]
  (empty? (learner/collection-words learner collection)))


(defn dictionary-translation
  "The translation field's text the dictionary offers for `typed` among its `completions`: the translations of
   the first row whose lemma `typed` begins, \"\" when none does. A row found through a word form is another
   word (#711)."
  [typed completions]
  (or (some->> (vocabulary/leading-index typed completions)
               (nth completions)
               :translations
               vocabulary/translations-text)
      ""))


(defn ^:async add!
  "Adds `value` with `translation` to the vocabulary and the active collection: a phrase or a word by the
   dictionary's part of speech `pos` (none: by the value alone), a word keeping `pos`; a duplicate value merges
   translations. Resolves with `{:word-id :created?}` or `{:error :failure/empty-translations}`."
  [learner world {:keys [value translation pos]}]
  (let [now   (world/clock world :now-iso)
        kind  (if (phrase/phrase-value? value pos) :phrase :word)
        entry (vocabulary/entered-entry kind value translation pos now)]
    (if-not entry
      {:error :failure/empty-translations}
      (let [collection (learner/active-collection learner)
            created?   (volatile! false)
            _ (await (learner/change! learner
                                      [[:entity/word
                                        (:id entry)
                                        (fn [stored]
                                          (vreset! created? (nil? stored))
                                          (if stored
                                            (vocabulary/merged-entry stored entry now)
                                            entry))]]))
            created?   @created?]
        (when collection
          (await (learner/change! learner
                                  [[:entity/collection
                                    (:id collection)
                                    #(some-> % (collections/with-word (:id entry)))]])))
        {:created? created? :word-id (:id entry)}))))


(defn rows
  "The word list's rows out of memory, paged by `:offset`/`:limit`, narrowed by `:collection` and `:search`,
   each with its `:recall`, the probability the learner recalls it now (nil for a word never answered). `:total` counts the words before the search, `:matches` after it."
  [learner {:keys [collection limit offset search]} now-ms]
  (let [pace    (retention/pace (learner/collection-cards learner nil))
        scope   (learner/collection-words learner collection)
        query   (when (utils/non-blank search) (vocabulary/query search))
        matched (learner/matching-words learner collection query)
        page    (cond->> matched
                  offset (drop offset)
                  limit  (take limit))]
    {:matches (count matched)
     :total   (count scope)
     :words   (mapv (fn [word]
                      (let [memory (retention/memory
                                    {:history (learner/review-history learner (:id word))
                                     :pace    pace
                                     :weights retention/default-weights
                                     :word    word})
                            recall (when memory
                                     (retention/recall
                                      {:memory memory :now-ms now-ms :weights retention/default-weights}))]
                        (assoc (select-keys word [:id :kind :translation :value]) :recall recall)))
                    page)}))


(defn word-bins
  "All the learner's words in ten bins by the percent the word list shows for each at `now-ms`
   (`domain.statistics/word-bins`)."
  [learner now-ms]
  (->> (rows learner {} now-ms)
       :words
       (map :recall)
       statistics/word-bins))


(defn spelling-taken?
  "True when `value` is the spelling of a word other than `word-id`, as the learner has it."
  [learner word-id value]
  (let [id (vocabulary/vocab-id value)]
    (and (not= id word-id)
         (some? (learner/word learner id)))))


(defn editable-word
  "What the edit dialog starts from for the word `word-id`: `{:phrase? :word :translations}`, the translations
   as the lines of their field. nil when the learner has no such word."
  [learner word-id]
  (when-let [word (learner/word learner word-id)]
    {:phrase?      (phrase/phrase? word)
     :translations (vocabulary/russian-translations word)
     :word         (:value word)}))


(defn ^:async update!
  "Edits the word `word-id` in place: its `value` (the same vocab id, spelled differently) and `translation`.
   Resolves with whether memory took the change."
  [learner world word-id value translation]
  (await (learner/change! learner
                          [[:entity/word
                            word-id
                            #(some-> % (vocabulary/edited-word value translation (world/clock world :now-iso)))]])))


(defn ^:async rename!
  "Makes a new word of `value` and `translation` in the place of the word `word-id`: the same collections, the
   old word gone. Resolves with whether memory took the change."
  [learner world word-id value translation]
  (let [kind  (if (phrase/phrase-value? value nil) :phrase :word)
        entry (vocabulary/entered-entry kind (str/trim value) translation nil (world/clock world :now-iso))
        _ (await (learner/fresh-memory learner))]
    (if (or (nil? entry) (nil? (learner/word learner word-id)))
      false
      ;; The new word in, the old one out, and every collection that listed the old one listing the new.
      (let [changes (->> (learner/collections-of learner word-id)
                         (map (fn [{:keys [id]}]
                                [:entity/collection id #(some-> % (collections/replace-word word-id (:id entry)))]))
                         (into [[:entity/word (:id entry) #(or % entry)]
                                [:entity/word word-id (constantly :entity/deleted)]]))]
        (await (learner/change! learner changes))))))


(defn ^:async delete!
  "Deletes a word and its place in every collection in one write; its reviews and examples stay. Resolves
   true when it wrote, nil when there was nothing to write, false when the write was taken back."
  [learner word-id]
  (await (learner/fresh-memory learner))
  (let [listing (learner/collections-of learner word-id)]
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
    (let [scope (collections/family (learner/collections learner)
                                    (:id collection))]
      (await (learner/change! learner
                              (mapv (fn [id]
                                      [:entity/collection id #(some-> % (collections/without-word word-id))])
                                    (or (seq (map :id scope)) [(:id collection)])))))
    (await (delete! learner word-id))))

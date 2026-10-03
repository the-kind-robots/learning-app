(ns use-cases.vocabulary
  (:require
   [clojure.string :as str]
   [domain.phrase :as phrase]
   [domain.retention :as retention]
   [domain.vocabulary :as domain]
   [use-cases.examples :as examples]
   [utils :as utils]))


(defn find-duplicate
  "The word or phrase memory has under the same value, or nil."
  [{:keys [learner]} value]
  ((:learner/word learner) ((:learner/memory learner)) (domain/vocab-id value)))


(defn- entered-translation
  "A phrase's text is collapsed to single spaces on submit — its field is
   multi-line and the line breaks are visual only."
  [kind translation]
  (cond-> translation
    (= :phrase kind) phrase/collapsed))


(defn- translation-entries
  "One entered translation is one entry, for either kind. Blank gives none,
   which is what `add!` answers `:empty-translations` to."
  [kind translation]
  (if (= :phrase kind)
    (if (str/blank? translation) [] [(phrase/translation-entry translation)])
    (domain/parse-translations translation)))


(defn- new-entry
  [kind value translation entries]
  (if (= :phrase kind)
    (phrase/new-phrase value translation)
    (domain/new-word value entries)))


(defn ^:async add!
  "Adds a vocabulary entry of `kind` — `:word` or `:phrase` — and queues a
   collection-scoped example fetch. A phrase asks for an example like a
   word does (#371); the kind decides only how the translation is read and
   which document is built.

   A new word gets an initial review, unless memory holds reviews of it
   already: a word deleted and added again comes back with its history,
   and its retention is computed from that history alone. A duplicate value
   is one entry whatever its kind: translations merge, and the kind is left
   alone. Either way an example is fetched only when the active collection
   has none for the word yet. Returns a promise of {:word-id id :created?
   bool} or {:error :empty-translations}.

   `kind` has no default on purpose. Made optional, this becomes a two-arity
   function, and `:static-fns` then compiles every call site to
   `add_BANG_.cljs$core$IFn$_invoke$arity$4` — a property a plain test stub
   does not carry, so `with-redefs` stops intercepting and the effect throws
   where it used to run."
  [{:keys [learner] :as capabilities} value translation kind]
  (let [translation (entered-translation kind translation)
        entries     (translation-entries kind translation)]
    (if (empty? entries)
      {:error :empty-translations}
      (let [collection ((:learner/active-collection learner))
            {:keys [created? word]} (await ((:learner/add-word! learner)
                                            (new-entry kind value translation entries)))
            history    ((:learner/review-history learner) ((:learner/memory learner)) (:id word))]
        (when (and created? (zero? (count (:ids history))))
          (await ((:learner/add-review! learner) (:id word) true translation)))
        (when collection
          (await ((:learner/add-to-collection! learner) (:id word) (:id collection))))
        (await (examples/request-example-if-missing! capabilities word collection))
        {:created? created? :word-id (:id word)}))))


(defn urgency-of
  "How due the word on `card` is, computed from its review history."
  [card now]
  (retention/urgency (:reviews card) now))


(defonce ^:private matching-cache
  (volatile! nil))


(defn- matching
  "The words of `scope` whose search text holds `query`, or all of them when
   `query` is nil. The result is kept for the last scope and query, because
   the next page cuts the same matches."
  [scope query]
  (if-not query
    scope
    (let [[cached-scope cached-query cached] @matching-cache]
      (if (and (identical? cached-scope scope) (= cached-query query))
        cached
        (let [matched (into [] (filter #(domain/matches? (:search %) query)) scope)]
          (vreset! matching-cache [scope query matched])
          matched)))))


(defn rows
  "The word list's rows out of memory, paged by `:offset`/`:limit`. Each row
   carries its retention level. `:collection` narrows the rows to a
   collection, and `:search` to the words holding the text. `:total` counts
   the words before the search, `:matches` after it."
  [learner memory {:keys [collection limit offset search]} now-ms]
  (let [scope   ((:learner/collection-words learner) memory collection)
        query   (when (utils/non-blank search) (domain/query search))
        matched (matching scope query)
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
  ((:learner/update-word! learner)
   word-id
   (fn [word]
     (if (phrase/phrase-doc? word)
       (phrase/update-phrase word translation)
       (domain/update-word word translation)))))


(defn delete!
  "Deletes a word and its place in every collection. Its reviews and its
   examples stay, and come back if the word is added again. No-op if memory
   has no such word."
  [{:keys [learner]} word-id]
  ((:learner/delete-word! learner) word-id))


(defn ^:async remove-from-active!
  "User-initiated 'remove word' that respects the active scope:
   from a named collection, only the membership is dropped — the word
   itself stays in vocabulary; from the implicit main, the word and its
   place in every collection are deleted."
  [{:keys [learner] :as capabilities} word-id]
  (if-let [collection ((:learner/active-collection learner))]
    (await ((:learner/remove-from-collection! learner) word-id (:id collection)))
    (await (delete! capabilities word-id))))


(defn add-review
  "Writes a review of a word. Resolves once memory has it."
  [{:keys [learner]} word-id retained translation]
  ((:learner/add-review! learner) word-id retained translation))

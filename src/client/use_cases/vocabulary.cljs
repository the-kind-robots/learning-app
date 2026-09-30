(ns use-cases.vocabulary
  (:require
   [clojure.string :as str]
   [domain.phrase :as phrase]
   [domain.retention :as retention]
   [domain.vocabulary :as domain]
   [use-cases.collections :as collections]
   [use-cases.examples :as examples]
   [utils :as utils]))


(defn ^:async find-duplicate
  "Find an existing vocab doc with the same value, or nil."
  [{:keys [words]} value]
  (await ((:words/find-by-value words) value)))


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
  "Adds a vocabulary entry of `kind` — `:word` or `:phrase` — with an initial
   review, and queues a collection-scoped example fetch. A phrase asks for an
   example like a word does (#371); the kind decides only how the translation
   is read and which document is built.

   A duplicate value is one entry whatever its kind: translations merge, the
   kind is left alone, and an example is fetched only when the active
   collection has none for it yet. Returns a promise of
   {:word-id id :created? bool} or {:error :empty-translations}.

   `kind` has no default on purpose. Made optional, this becomes a two-arity
   function, and `:static-fns` then compiles every call site to
   `add_BANG_.cljs$core$IFn$_invoke$arity$4` — a property a plain test stub
   does not carry, so `with-redefs` stops intercepting and the effect throws
   where it used to run."
  [{:keys [collections examples reviews words] :as capabilities} value translation kind]
  (let [translation (entered-translation kind translation)
        entries     (translation-entries kind translation)]
    (if (empty? entries)
      {:error :empty-translations}
      (let [existing      (await (find-duplicate capabilities value))
            collection-id ((:collections/active-id collections))]
        (if existing
          (let [merged  (domain/merge-translations (:translation existing) entries)
                updated (assoc existing :translation merged)]
            (await ((:words/save! words) updated))
            (when collection-id
              (await ((:collections/add-word! collections) (:id existing) collection-id))
              (when (await (examples/needs-example? capabilities (:id existing) collection-id))
                (let [collection-name (:name (await ((:collections/get collections) collection-id)))]
                  ((:examples/request! examples)
                   [{:collection-id collection-id
                     :collection-name collection-name
                     :word updated}]))))
            {:word-id (:id existing) :created? false})
          (let [entry        (new-entry kind value translation entries)
                {:keys [id]} (await ((:words/save! words) entry))
                collection-name (when collection-id
                                  (:name (await ((:collections/get collections) collection-id))))]
            (await ((:reviews/save! reviews) id true translation))
            (when collection-id
              (await ((:collections/add-word! collections) id collection-id)))
            ((:examples/request! examples)
             [{:collection-id collection-id
               :collection-name collection-name
               :word entry}])
            {:word-id id :created? true}))))))


(defn urgency-of
  "How due the word on `card` is, computed from its review history."
  [card now]
  (retention/urgency (:reviews card) now))


(defn- card-of
  [memory word-id]
  (nth (:cards memory) (get-in memory [:slot-of word-id])))


(defonce ^:private slots-cache
  (volatile! nil))


(defn- collection-slots
  "The slots of the words of `collection` and of its children. The result
   is kept for the last collections, slots and collection asked about; an
   answer changes none of them."
  [memory collection]
  (let [all                                (:collections memory)
        slots                              (:slot-of memory)
        id                                 (:id collection)
        [held-all held-slots held-id held] @slots-cache]
    (if (and (identical? held-all all) (identical? held-slots slots) (= held-id id))
      held
      (let [found (into [] (keep slots) (collections/scope-word-ids (vals all) id))]
        (vreset! slots-cache [all slots id found])
        found))))


(defn collection-cards
  "The cards a lesson draws from: those of the words of `collection`, or of
   every word when `collection` is nil. Cards whose word is removed are left
   out."
  [memory collection]
  (let [cards (:cards memory)]
    (if collection
      (into [] (comp (map #(nth cards %)) (filter :word)) (collection-slots memory collection))
      (into [] (filter :word) cards))))


(defonce ^:private collection-cache
  (volatile! nil))


(defn collection-words
  "The words of `collection` and of its children, in the word list's order,
   or every word when `collection` is nil. The result is kept for the last
   words, collections and collection asked about, because every keystroke
   asks again about the same collection."
  [memory collection]
  (let [words                            (:words memory)
        all                              (:collections memory)
        id                               (:id collection)
        [held-words held-all held-id held] @collection-cache]
    (if (and (identical? held-words words) (identical? held-all all) (= held-id id))
      held
      (let [ids   (some->> id (collections/scope-word-ids (vals all)) set)
            found (into [] (if ids (filter #(ids (:id %))) identity) (vals words))]
        (vreset! collection-cache [words all id found])
        found))))


(defonce ^:private matching-cache
  (volatile! nil))


(defn- matching
  "The words of `scope` whose search text holds `query`, or all of them when
   `query` is nil. The result is kept for the last scope and query, because
   the next page cuts the same matches."
  [scope query]
  (if-not query
    scope
    (let [[held-scope held-query matched] @matching-cache]
      (if (and (identical? held-scope scope) (= held-query query))
        matched
        (let [matched (into [] (filter #(domain/matches? (:search %) query)) scope)]
          (vreset! matching-cache [scope query matched])
          matched)))))


(defn rows
  "The word list's rows out of memory, paged by `:offset`/`:limit`. Each row
   carries its retention level; the level is nil while `:retention?` is
   false, that is, before the reviews are loaded. `:collection` narrows the
   rows to a collection, and `:search` to the words holding the text.
   `:total` counts the words before the search, `:matches` after it."
  [memory {:keys [collection limit offset retention? search] :or {retention? true}} now-ms]
  (let [scope   (collection-words memory collection)
        query   (when (utils/non-blank search) (domain/query search))
        matched (matching scope query)
        page    (cond->> matched offset (drop offset) limit (take limit))]
    {:matches (count matched)
     :total   (count scope)
     :words   (mapv #(assoc (select-keys % [:id :kind :translation :value])
                            :retention-level (when retention?
                                               (retention/level (urgency-of (card-of memory (:id %)) now-ms))))
                    page)}))


(defn ^:async update!
  "Updates a word's translation. Returns the updated word, or nil if not
   found. What screens show of it follows memory, which takes the word when
   PouchDB does."
  [{:keys [words]} word-id translation]
  (when-let [word (await ((:words/get words) word-id))]
    (let [updated (if (phrase/phrase-doc? word)
                    (phrase/update-phrase word translation)
                    (domain/update-word word translation))]
      (await ((:words/save! words) updated))
      updated)))


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

(ns use-cases.vocabulary
  (:refer-clojure :exclude [list count get])
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


(defn- now-ms
  [{:keys [clock]}]
  ((:clock/now-ms clock)))


(defn ^:async get
  "Returns a word with its retention level, or nil if not found."
  [{:keys [reviews words] :as capabilities} word-id]
  (when-let [word (await ((:words/get words) word-id))]
    (let [reviews (await ((:reviews/by-word reviews) [word-id]))]
      (assoc word :retention-level (retention/retention-level (reviews word-id []) (now-ms capabilities))))))


(defn- page-of
  [rows offset limit]
  (cond->> rows
    offset (drop offset)
    limit  (take limit)))


(defn urgency-of
  "Urgency out of the retention state memory keeps on the word; a word with no
   review has none and is as due as a word gets."
  [now word]
  (retention/state-urgency (:retention word) now))


(defn- preview
  "What a row carries of a word: what a list shows and a lesson asks, not the
   search text or the sort key memory keeps beside it."
  [word]
  (select-keys word [:id :kind :translation :value]))


(defn- with-retention-level
  [memory now word]
  (assoc (preview word)
         :retention-level
         (retention/urgency->retention-level (urgency-of now word))))


(defn- with-urgency
  "`words`, each with the urgency it is ranked by and the retention level that
   is its image, so a word's retention state is read once (#404). Urgency
   rather than retention: retention underflows to a flat 0.0 after 3.8
   unreviewed days, and the ties would then fall back to the alphabet (#431)."
  [memory now words]
  (map (fn [word]
         (let [urgency (urgency-of now word)]
           (assoc (preview word)
                  :retention-level (retention/urgency->retention-level urgency)
                  :urgency urgency)))
       words))


(defonce ^:private scope-cache
  (volatile! nil))


(defn- in-scope
  "The words of `in-order` that `word-ids` names, in order; all of them for
   nil. Kept for the last list and ids asked of: every keystroke in a
   collection's filter asks again of the same scope."
  [in-order word-ids]
  (if-not word-ids
    in-order
    (let [[held-order held-ids scope] @scope-cache]
      (if (and (identical? held-order in-order) (= held-ids word-ids))
        scope
        (let [ids   (set word-ids)
              scope (into [] (filter #(ids (:id %))) in-order)]
          (vreset! scope-cache [in-order word-ids scope])
          scope)))))


(defn scope
  "Every word in scope — all of memory, or those `word-ids` names — in the
   word list's order."
  [memory word-ids]
  (in-scope (:words-in-order memory) word-ids))


(defn rows
  "Vocabulary rows out of the learner's data in memory, with retention levels,
   paged by `:offset`/`:limit`. Pure: memory is the value it is given.

   `:order` says what the caller wants the page to hold — `:alphabetical`
   (default), the word list's order, or `:most-due`, the lesson's, whose rows
   also carry the `:urgency` they were ranked by so a caller can break its
   ties. Alphabetical order computes retention for the page's rows only;
   most-due ranks every word in scope.

   `:word-ids` restricts to those words, `:search` to values or translations
   containing the text. `:total` counts the words in scope before the search
   filter, so an empty vocabulary and a search with no match tell apart;
   `:matches` counts them after it and before the paging, so a caller holding
   a page can tell whether another one follows."
  [memory {:keys [limit offset order search word-ids] :or {order :alphabetical}} now-ms]
  (let [scope   (in-scope (:words-in-order memory) word-ids)
        query   (when (utils/non-blank search) (domain/query search))
        matched (if query
                  (into [] (filter #(domain/matches? (:search %) query)) scope)
                  scope)]
    (if (= order :most-due)
      ;; Most due first. Urgency still ties — every word with no review,
      ;; every word added within one second of another — and those ties keep
      ;; the alphabet; a caller taking a subset off the head breaks them for
      ;; itself out of the `:urgency` each row carries.
      (let [rows (sort-by :urgency > (with-urgency memory now-ms matched))]
        {:matches (clojure.core/count rows)
         :total   (clojure.core/count scope)
         :words   (vec (page-of rows offset limit))})
      {:matches (clojure.core/count matched)
       :total   (clojure.core/count scope)
       :words   (mapv #(with-retention-level memory now-ms %) (page-of matched offset limit))})))


(defn word-count
  "How many words and phrases the learner's data in memory holds."
  [memory]
  (clojure.core/count (:words memory)))


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

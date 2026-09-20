(ns use-cases.vocabulary
  (:refer-clojure :exclude [list count get])
  (:require
   [clojure.string :as str]
   [domain.phrase :as phrase]
   [domain.retention :as retention]
   [domain.vocabulary :as domain]
   [use-cases.collections :as collections]
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
              (when-not (await ((:examples/find examples) (:id existing) collection-id))
                (let [collection-name (:name (await ((:collections/get collections) collection-id)))]
                  ((:examples/request! examples) updated collection-id collection-name))))
            {:word-id (:id existing) :created? false})
          (let [entry        (new-entry kind value translation entries)
                {:keys [id]} (await ((:words/save! words) entry))
                collection-name (when collection-id
                                  (:name (await ((:collections/get collections) collection-id))))]
            (await ((:reviews/save! reviews) id true translation))
            (when collection-id
              (await ((:collections/add-word! collections) id collection-id)))
            ((:examples/request! examples) entry collection-id collection-name)
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
   :asc for the most due first, :desc for the best remembered first,
   default :desc) and paged by `:offset`/`:limit`. Each row carries the
   `:urgency` the sort ran on, for a caller that has to break its ties.
   `:word-ids`
   restricts to those words, `:search` to values or translations containing
   the text. `:total` counts the words before the search filter, so an empty
   vocabulary and a search with no match tell apart."
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
        ;; Sorted by urgency rather than by the retention level the row
        ;; carries: retention underflows to a flat 0.0 after 3.8 unreviewed
        ;; days, and the ties then fall back to the repository's read order,
        ;; which is the alphabet (#431). Urgency orders the same words the
        ;; same way without collapsing, and the level is its image, so a
        ;; word's reviews are still walked once (#404).
        ;;
        ;; Urgency still ties — every word with no review at all, and every
        ;; word added within one second of another, since elapsed time is
        ;; truncated to seconds — and those ties are still broken by the read
        ;; order. On this page that is the alphabet among words showing the
        ;; same percentage, which is predictable and wanted. Only a caller
        ;; taking a subset off the head needs more, and `domain.lesson` does
        ;; that for itself out of the `:urgency` each row carries.
        rows         (->> candidates
                          (map (fn [word]
                                 (let [urgency (retention/urgency (reviews (:id word) []) now)]
                                   (assoc word
                                          :retention-level (retention/urgency->retention-level urgency)
                                          :urgency urgency))))
                          (sort-by :urgency (if (= order :asc) > <)))
        rows         (cond->> rows
                       offset (drop offset)
                       limit  (take limit))]
    {:total total
     :words (vec rows)}))


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

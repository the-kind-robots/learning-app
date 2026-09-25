(ns adapters.memory
  "The learner's data held in memory, as a projection of the local databases
   (ADR-0016). PouchDB is the only source: memory is loaded from it, follows
   its change feed, and takes this app's own writes only once PouchDB has
   accepted them. It never holds what PouchDB lacks, so losing it loses
   nothing.

   Outward it is domain shapes only:

     {:collections       {id {:id :name :word-ids :created-at}}
      :examples          {id {:id :word-id :collection-id :value ...}}
      :examples-by-word  {word-id #{example-id}}
      :reviews           {id {:word-id :created-at :retained}}
      :reviews-by-word   {word-id {review-id review}}
      :words             {id {:id :kind :value :translation :search :filed-under :retention}}
      :words-in-order    [word ...]}

   `:words-in-order` is every word in the word list's order
   (`domain.vocabulary/filed-under`), `:search` a word's text normalised once
   for the words filter, `:retention` `domain.retention/retention-state` of the
   word's reviews.

   Every index is kept by one path, `ingest`: the document as memory held it
   against the document as it now stands, either of them absent. What each
   document was taken as is kept under a key of this namespace, so a
   revision seen twice changes nothing and a deletion — which carries no more
   than an id — knows what to take out."
  (:require
   [adapters.collections :as collections]
   [adapters.examples :as examples]
   [adapters.repository :as repository]
   [adapters.reviews :as reviews]
   [adapters.words :as words]
   [db.pouch :as dbs]
   [domain.retention :as retention]
   [domain.vocabulary :as vocabulary]
   [instrumentation :as instrumentation]
   [lambdaisland.glogi :as log]))


(def empty-memory
  {:collections     {}
   :examples        {}
   :examples-by-word {}
   :reviews         {}
   :reviews-by-word {}
   :words           {}
   :words-in-order  []
   ::held           {}})


(defrecord ^:private Word [id kind value translation filed-under search retention])


(defn- word
  "A word as memory holds it. A record rather than a map: the list, the
   filter and the lesson read `:id`, `:search` and `:retention` off every word
   in scope, and a record answers those as fields rather than through a hash
   lookup — measured four times faster over 20 000 words."
  [doc]
  (let [entity (repository/entity doc)]
    (map->Word (assoc entity
                      :filed-under (vocabulary/filed-under (:id entity))
                      :search      (vocabulary/search-text entity)))))


(defn- review
  [doc]
  {:created-at (:created-at doc)
   :retained   (:retained doc)
   :word-id    (:word-id doc)})


(def ^:private kinds
  "Which kind of the learner's data a document type is, and in what shape.
   The types are the ones the owning repositories declare."
  {(:type collections/schema) [:collections collections/doc->collection]
   (:type examples/schema)    [:examples repository/entity]
   (:type reviews/schema)     [:reviews review]
   (:type words/schema)       [:words word]})


;;
;; Derived indexes: the words in order and each word's retention state. An
;; ingest records what it touched; `derived` brings both up to date once per
;; batch, so a word's ten reviews arriving together compute its retention
;; once, and a vocabulary arriving at start is sorted once.
;;


(defn- position
  "Where a word filed under `key` stands among the `n` words `word-at` reads
   by index: its index when it is there, else the index it would be inserted
   at. Binary search."
  [word-at n key]
  (loop [lo 0
         hi n]
    (if (< lo hi)
      (let [mid (quot (+ lo hi) 2)]
        (if (neg? (compare (:filed-under (word-at mid)) key))
          (recur (inc mid) hi)
          (recur lo mid)))
      lo)))


(defn- moved
  "`in-order` after one word went from `old` to `new`, either absent. A word
   whose place did not change — an edited translation, a new retention
   state — is replaced where it stands; one that came or went is spliced,
   which copies the list."
  [in-order [old new]]
  (if (and old new (= (:filed-under old) (:filed-under new)))
    (assoc in-order (position #(nth in-order %) (count in-order) (:filed-under old)) new)
    (let [^js arr (to-array in-order)
          at      #(position (fn [i] (aget arr i)) (.-length arr) %)]
      (when old
        (.splice arr (at (:filed-under old)) 1))
      (when new
        (.splice arr (at (:filed-under new)) 0 new))
      (vec arr))))


(def ^:private resort-above
  "A batch moving more words than this sorts the list once rather than
   splicing each word into place: a splice copies the list, so a whole
   vocabulary arriving at start would cost its square."
  64)


(defn- with-retention
  "`word` carrying the retention state of its reviews in `memory` — what
   ranking and retention levels read, so that a read walks no review and
   looks nothing up — or none when it has no review."
  [memory word]
  (if-let [reviews (vals (get-in memory [:reviews-by-word (:id word)]))]
    (assoc word :retention (retention/retention-state reviews))
    (assoc word :retention nil)))


(defn- derived
  "Memory with the derived state brought up to what the batch touched: the
   retention of every word that was written or whose reviews changed, and
   the words in order."
  [{moves ::moved-words reviewed ::reviewed-words words :words :as memory}]
  (let [touched (into (set reviewed) (keep :id) (map first moves))
        touched (into touched (keep (comp :id second)) moves)
        ;; What the list held for each word before the batch: its first move's.
        before (reduce (fn [held [old new]]
                         (let [id (:id (or old new))]
                           (cond-> held (not (contains? held id)) (assoc id old))))
                       {}
                       moves)
        [words changes]
        (reduce (fn [[words changes] id]
                  (if-let [word (get words id)]
                    (let [word (with-retention memory word)]
                      [(assoc words id word) (conj changes [(get before id (get (:words memory) id)) word])])
                    (if (get before id)
                      [words (conj changes [(get before id) nil])]
                      [words changes])))
                [words []]
                touched)]
    (-> memory
        (assoc :words words)
        (assoc :words-in-order
               (if (> (count changes) resort-above)
                 (vec (sort-by :filed-under (vals words)))
                 (reduce moved (:words-in-order memory) changes)))
        (dissoc ::moved-words ::reviewed-words))))


;;
;; Ingest
;;


(defn- dissoc-in-or-drop
  "`m` without `k` under `outer`, and without `outer` once it holds nothing."
  [m index outer k]
  (let [left (disj (get-in m [index outer]) k)]
    (if (empty? left)
      (update m index dissoc outer)
      (assoc-in m [index outer] left))))


(defn- unindexed
  "Memory without what `entity`, held as `kind` under `id`, put in any
   primary index."
  [memory kind id entity]
  (case kind
    :reviews  (-> memory
                  (update :reviews dissoc id)
                  (update-in [:reviews-by-word (:word-id entity)] dissoc id)
                  (as-> m (cond-> m
                            (empty? (get-in m [:reviews-by-word (:word-id entity)]))
                            (update :reviews-by-word dissoc (:word-id entity))))
                  (update ::reviewed-words (fnil conj #{}) (:word-id entity)))
    :examples (-> memory
                  (update :examples dissoc id)
                  (dissoc-in-or-drop :examples-by-word (:word-id entity) id))
    (update memory kind dissoc id)))


(defn- indexed
  "Memory with `entity`, of `kind`, in every primary index its kind has."
  [memory kind id entity]
  (case kind
    :reviews  (-> memory
                  (assoc-in [:reviews id] entity)
                  (assoc-in [:reviews-by-word (:word-id entity) id] entity)
                  (update ::reviewed-words (fnil conj #{}) (:word-id entity)))
    :examples (-> memory
                  (assoc-in [:examples id] entity)
                  (update-in [:examples-by-word (:word-id entity)] (fnil conj #{}) id))
    (assoc-in memory [kind id] entity)))


(defn- ingest
  "Memory after the document `id` went from `old` to `new` — each
   `{:kind :entity :rev}` or nil for absent. The one path every index is kept
   by: the primary ones here, the derived ones recorded for `derived`."
  [memory id old new]
  (let [word-of #(when (= :words (:kind %)) (:entity %))
        memory  (cond-> memory
                  old (unindexed (:kind old) id (:entity old))
                  new (indexed (:kind new) id (:entity new))
                  (or (word-of old) (word-of new))
                  (update ::moved-words (fnil conj []) [(word-of old) (word-of new)]))]
    (if new
      (assoc-in memory [::held id] new)
      (update memory ::held dissoc id))))


(defn- with-one
  [memory {id :_id rev :_rev :as doc}]
  (let [old (get-in memory [::held id])]
    (if (= rev (:rev old))
      memory
      (let [[kind shape] (when-not (:_deleted doc) (kinds (:type doc)))]
        (ingest memory id old (when kind {:entity (shape doc) :kind kind :rev rev}))))))


(defn with-docs
  "Memory after `docs`, as the database holds them: a revision memory already
   holds changes nothing, a deletion removes the document, and a type memory
   does not hold is left out. Memory that ends up equal is the same value."
  [memory docs]
  (let [after (reduce with-one memory docs)]
    (if (identical? after memory)
      memory
      (derived after))))


(defn with-doc
  [memory doc]
  (with-docs memory [doc]))


(defn start!
  "Loads memory from both databases and keeps it following them. Returns at
   once, with a function that stops following; the load runs on.

   `change!` is called with a function of memory to apply — once per batch,
   so a batch renders once — and, when both databases have been read, with
   `identity` and `:ready`. Writes this app makes through `db.pouch` are
   applied as soon as PouchDB accepted them; everything else arrives through
   the feeds."
  [dbs change!]
  (let [stops  (atom [])
        apply! (fn [docs]
                 (when (seq docs)
                   (change! #(with-docs % docs))))]
    (dbs/on-written! dbs (fn [_ docs] (apply! docs)))
    ((fn ^:async load!
       []
       (try
         (when ^boolean goog/DEBUG
           (instrumentation/memory-start!))
         (swap! stops conj (await (dbs/follow! dbs :user/db apply!)))
         (swap! stops conj (await (dbs/follow! dbs :device/db apply!)))
         (change! identity :ready)
         (when ^boolean goog/DEBUG
           (instrumentation/memory-ready!))
         (catch :default err
           (log/error :memory/load-failed {:error (str err)})))))
    (fn []
      (dbs/on-written! dbs nil)
      (doseq [stop @stops]
        (stop)))))

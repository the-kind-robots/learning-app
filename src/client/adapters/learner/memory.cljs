(ns adapters.learner.memory
  "The learner's data in memory (ADR-0016): cards (word and reviews, in a slot never reused), collections,
   examples, and `:words` in the word list's order. `::position` is the feed position of the last change
   taken, arriving with its batch (`with-changes`); a snapshot stores memory without `:words` and it."
  (:require
   [adapters.learner.documents :as documents]
   [domain.collections :as domain-collections]
   [domain.retention :as retention]
   [domain.vocabulary :as vocabulary]
   [lambdaisland.glogi :as log]))


(def empty-memory
  {:cards            []
   :collections      {}
   :collections-by-word {}
   :examples         {}
   :examples-by-word {}
   :slot-of          {}
   :words            (sorted-map)
   ::position        nil})


(defn- sort-key
  "Where a word sits in `:words`: first its value without the article, then
   its id. The id tells apart `der Zug` and a bare `Zug`."
  [id]
  (let [[value id] (vocabulary/filed-under id)]
    ;; \0 sorts before any letter: by value, then by id.
    (str value "\u0000" id)))


(defn- card-slot
  "Returns memory with a card for `word-id`, and the card's slot. If the id
   has no card yet, a new card is added at the end of `:cards`."
  [memory word-id]
  (if-let [slot (get-in memory [:slot-of word-id])]
    [memory slot]
    (let [slot (count (:cards memory))]
      [(-> memory
           (update :cards conj {:reviews retention/empty-reviews})
           (assoc-in [:slot-of word-id] slot))
       slot])))


(defn- add-word
  [memory word]
  (let [[memory slot] (card-slot memory (:id word))
        word (assoc word :search (vocabulary/search-text word))]
    (-> memory
        (assoc-in [:cards slot :word] word)
        (assoc-in [:words (sort-key (:id word))] word))))


(defn- remove-word
  [memory {:keys [id]}]
  (-> memory
      (update-in [:cards (get-in memory [:slot-of id])] dissoc :word)
      (update :words dissoc (sort-key id))))


(defn- add-review
  "Memory with `review` in its word's history. A review already there
   changes nothing: reviews are never edited."
  [memory {:keys [id word-id] :as review}]
  (let [[memory slot] (card-slot memory word-id)]
    (if (<= 0 (.indexOf (:ids (get-in memory [:cards slot :reviews])) id))
      memory
      (update-in memory [:cards slot :reviews] retention/with-review review))))


;; `:collections-by-word` changes with `:collections` alone, so a review or an example leaves
;; `:words` and `:collections` as they were: the example backfill is not woken by them.
(defn- index-add
  "The key -> set `index` with `v` in the set of `k`."
  [index k v]
  (update index k (fnil conj #{}) v))


(defn- index-remove
  "The key -> set `index` without `v` in the set of `k`; a key whose set empties is dropped."
  [index k v]
  (let [left (disj (get index k) v)]
    (if (empty? left) (dissoc index k) (assoc index k left))))


(defn- differing
  "The middle of the vectors `before` and `after`, each without the words
   they share at the start and at the end. An edit of a collection changes a
   few words, so the middles are small and a set of each is cheap."
  [before after]
  (let [before (vec before)
        after  (vec after)
        limit  (min (count before) (count after))
        start  (or (first (filter #(not= (before %) (after %)) (range limit))) limit)
        room   (- limit start)
        tail   (or (first (filter #(not= (before (- (count before) 1 %)) (after (- (count after) 1 %)))
                                  (range room)))
                   room)]
    [(subvec before start (- (count before) tail))
     (subvec after start (- (count after) tail))]))


(defn- add-collection
  "Memory with `collection`. A held collection is replaced by touching only the words that differ,
   because a collection can hold thousands."
  [memory {:keys [id word-ids] :as collection}]
  (if-let [previous (get-in memory [:collections id])]
    (let [[before after] (differing (:word-ids previous) word-ids)
          held (set before)
          now  (set after)]
      (-> memory
          (assoc-in [:collections id] collection)
          (update :collections-by-word
                  #(reduce (fn [index word-id] (index-remove index word-id id))
                           %
                           (remove now held)))
          (update :collections-by-word
                  #(reduce (fn [index word-id] (index-add index word-id id))
                           %
                           (remove held now)))))
    (-> memory
        (assoc-in [:collections id] collection)
        (update :collections-by-word
                #(reduce (fn [index word-id] (index-add index word-id id))
                         %
                         word-ids)))))


(defn- remove-collection
  [memory {:keys [id word-ids]}]
  (-> memory
      (update :collections dissoc id)
      (update :collections-by-word
              #(reduce (fn [index word-id] (index-remove index word-id id))
                       %
                       word-ids))))


(defn- add-example
  [memory {:keys [id word-id] :as example}]
  (-> memory
      (assoc-in [:examples id] example)
      (update :examples-by-word index-add word-id id)))


(defn- remove-example
  [memory {:keys [id word-id]}]
  (-> memory
      (update :examples dissoc id)
      (update :examples-by-word index-remove word-id id)))


(def ^:private doc-types
  "For each document type that memory keeps: its kind, and the function
   that turns a document into an entity."
  {(:type documents/collection-schema) {:entity documents/doc->collection
                                        :kind   :collection}
   (:type documents/example-schema)    {:entity documents/doc->example
                                        :kind   :example}
   (:type documents/review-schema)     {:entity documents/doc->review
                                        :kind   :review}
   (:type documents/settings-schema)   {:entity documents/entity
                                        :kind   :settings}
   (:type documents/vocab-schema)      {:entity documents/doc->word
                                        :kind   :word}})


(defn- entry
  "What memory takes from `doc`: its kind and its entity. Returns nil when
   `doc` is a deletion, or when memory does not keep its type."
  [doc]
  (when-let [{:keys [kind entity]} (and (not (:_deleted doc)) (doc-types (:type doc)))]
    {:kind kind :entity (entity doc)}))


(defn- card
  "The card of `word-id`, or nil when it has none."
  [memory word-id]
  (some->> (get-in memory [:slot-of word-id]) (nth (:cards memory))))


(defn- old-version
  "The version of `doc` memory holds now, `{:kind :entity}`, or nil. It is a
   collection, an example, the settings or a word, found by the id; a review
   is never removed."
  [memory {id :_id}]
  (or (some->> (get-in memory [:collections id]) (hash-map :kind :collection :entity))
      (some->> (get-in memory [:examples id]) (hash-map :kind :example :entity))
      (when (= id (get-in memory [:settings :id])) {:kind :settings :entity (:settings memory)})
      (some->> (card memory id) :word (hash-map :kind :word :entity))))


(defn- added
  "Memory with `entry` in it. A nil entry adds nothing."
  [memory {:keys [kind entity] :as entry}]
  (if-not entry
    memory
    (case kind
      :collection (add-collection memory entity)
      :example    (add-example memory entity)
      :review     (add-review memory entity)
      :settings   (assoc memory :settings entity)
      :word       (add-word memory entity))))


(defn- removed
  "Memory without the `old` version. A nil `old` removes nothing."
  [memory {:keys [kind entity] :as old}]
  (if-not old
    memory
    (case kind
      :collection (remove-collection memory entity)
      :example    (remove-example memory entity)
      :settings   (dissoc memory :settings)
      :word       (remove-word memory entity))))


(defn- with-doc
  "Memory after `doc` arrives: the version memory holds is removed, and
   `doc` is added. A collection is not removed first: adding the new version
   replaces it."
  [memory doc]
  (let [old (old-version memory doc)
        new (entry doc)]
    (if (= :collection (:kind old) (:kind new))
      (added memory new)
      (-> memory (removed old) (added new)))))


(defn- with-taken-doc
  "Memory after `doc` arrives. A document of an unexpected shape is logged and its held version
   removed, so memory holds what a full read would; it does not stop the rest. A build that starts
   to take such a document must change `adapters.learner.snapshot/format-version`."
  [memory doc]
  (try
    (with-doc memory doc)
    (catch :default err
      (log/error :memory/document-skipped {:error (str err) :id (:_id doc)})
      (removed memory (old-version memory doc)))))


(defn with-docs
  "Memory after `docs` arrive in order; a batch that changes nothing returns the same memory. Every document
   reaches memory here, in the order the database stored it, so the last taken for an id is the winner."
  [memory docs]
  (reduce with-taken-doc memory docs))


(defn with-changes
  "Memory after the batch `docs` arrives (`with-docs`), with `position` of the batch's last change.
   A batch that changes nothing memory keeps, such as a pairing receipt, returns the same memory
   and keeps its position."
  [memory docs position]
  (let [taken (with-docs memory docs)]
    (if (identical? taken memory)
      memory
      (assoc taken ::position position))))


(defn position
  "The feed position of user-db that memory has taken its changes up to,
   `{:id :rev :seq}`, or nil before memory has taken any."
  [memory]
  (::position memory))


(defn stored
  "Memory as a snapshot stores it: all of it but the word list and the
   feed position, which come from the words and the header."
  [memory]
  (dissoc memory :words ::position))


(defn restored
  "Memory from `stored`, as `stored` returned it, with no feed position."
  [stored]
  (assoc stored
         ::position nil
         :words     (into (sorted-map)
                          (keep (fn [{word :word}] (when word [(sort-key (:id word)) word])))
                          (:cards stored))))


(defn with-position
  "Memory with `position`, `{:id :rev :seq}`, as the feed position of
   user-db it has taken changes up to."
  [memory position]
  (assoc memory ::position position))


;;
;; Reads. Outside the learner's adapter memory is read through these functions only,
;; which `ports.learner` passes on; the keys above belong to this namespace.
;;


(defn language
  "The language the learner picked, or nil when none was picked."
  [memory]
  (get-in memory [:settings :language]))


(defn word
  "The word or phrase `word-id`, or nil when memory has none. The word
   carries `:search`, its text normalised for the filter."
  [memory word-id]
  (:word (card memory word-id)))


(defn words
  "Every word and phrase, in the word list's order."
  [memory]
  (vals (:words memory)))


(defn word-count
  "How many words and phrases memory has."
  [memory]
  (count (:words memory)))


(defn review-history
  "The review history of `word-id` (`domain.retention/Reviews`); an empty
   one when memory has no review of it."
  [memory word-id]
  (or (:reviews (card memory word-id)) retention/empty-reviews))


(defn collection
  "The collection `collection-id`, or nil when memory has none."
  [memory collection-id]
  (get-in memory [:collections collection-id]))


(defn collections
  "Every collection, in no particular order."
  [memory]
  (vals (:collections memory)))


(defn collections-of
  "The collections that name the word `word-id`, in no particular order."
  [memory word-id]
  (into [] (map (:collections memory)) (get-in memory [:collections-by-word word-id])))


(defn examples-of
  "The examples memory has for any of `word-ids`."
  [memory word-ids]
  (into [] (comp (mapcat #(get-in memory [:examples-by-word %])) (map (:examples memory))) word-ids))


(defonce ^:private slots-cache
  ;; The last answer of `collection-slots`, with what it was computed from. A lesson asks for a
  ;; collection's cards after every answer; an answer changes only reviews, so the same
  ;; objects give the same answer.
  (volatile! nil))


(defn- collection-slots
  "The slots of the words of `collection` and of its children. The result
   is kept for the last collections, slots and collection asked about; an
   answer changes none of them."
  [memory collection]
  (let [all   (:collections memory)
        slots (:slot-of memory)
        id    (:id collection)
        [cached-all cached-slots cached-id cached] @slots-cache]
    (if (and (identical? cached-all all) (identical? cached-slots slots) (= cached-id id))
      cached
      (let [found (into [] (keep slots) (domain-collections/family-word-ids (vals all) id))]
        (vreset! slots-cache [all slots id found])
        found))))


(defn collection-cards
  "The cards a lesson draws from: those of the words of `collection`, or of
   every word when `collection` is nil. A card is `{:word :reviews}`. Cards
   whose word is removed are left out."
  [memory collection]
  (let [cards (:cards memory)]
    (if collection
      (into [] (comp (map #(nth cards %)) (filter :word)) (collection-slots memory collection))
      (into [] (filter :word) cards))))


(defonce ^:private collection-cache
  ;; The last answer of `collection-words`, with what it was computed from: every keystroke of
  ;; the words filter asks for the same collection.
  (volatile! nil))


(defn collection-words
  "The words of `collection` and of its children, in the word list's order; every word when it
   is nil. Kept for the last words, collections and collection asked about."
  [memory collection]
  (let [words (:words memory)
        all   (:collections memory)
        id    (:id collection)
        [cached-words cached-all cached-id cached] @collection-cache]
    (if (and (identical? cached-words words) (identical? cached-all all) (= cached-id id))
      cached
      (let [ids   (some->> id (domain-collections/family-word-ids (vals all)) set)
            found (into [] (if ids (filter #(ids (:id %))) identity) (vals words))]
        (vreset! collection-cache [words all id found])
        found))))


(defonce ^:private matches-cache
  ;; The last answer of `matching-words`, with what it was computed from.
  (volatile! nil))


(defn matching-words
  "The words of `collection` (`collection-words`) whose search text holds
   `query`, or all of them when `query` is nil. The result is kept for the
   last scope and query, because the next page cuts the same matches."
  [memory collection query]
  (let [scope (collection-words memory collection)]
    (if-not query
      scope
      (let [[cached-scope cached-query cached] @matches-cache]
        (if (and (identical? cached-scope scope) (= cached-query query))
          cached
          (let [matched (into [] (filter #(vocabulary/matches? (:search %) query)) scope)]
            (vreset! matches-cache [scope query matched])
            matched))))))

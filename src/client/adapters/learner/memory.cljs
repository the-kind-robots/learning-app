(ns adapters.learner.memory
  "The learner's data in memory: a value built from PouchDB documents
   (ADR-0016), which `adapters.learner.loader` hands over.

     {:cards            [{:word word :reviews domain.retention/Reviews}]
      :collections      {id collection}
      :examples         {id example}
      :examples-by-word {word-id #{example-id}}
      :slot-of          {word-id slot}
      :words            (sorted-map sort-key word)}

   Each word id has a card: the word, until it is removed, and its review
   history. A card's slot is its place in `:cards`. Memory gives a word id
   its slot when the word or one of its reviews first arrives, because a
   review may arrive before its word, and never reuses a slot. `:words`
   keeps the same words in the word list's order; each word carries
   `:search`, its text normalised for the filter.

   Memory adds and removes each document type with a pair of functions.
   When a new revision of a document arrives, memory removes the old
   version and adds the new one. Under `::entries` memory keeps what it took
   from each document. So when the same revision arrives twice, the second
   one changes nothing, and when a deletion arrives with only an id, memory
   still knows what to remove. The same entries rebuild memory from a
   snapshot (`adapters.learner.snapshot`).

   Under `::position` memory keeps the feed position of user-db's last
   change that changed it (`db.pouch/change-position`). A batch and its
   position arrive together (`with-changes`), so a memory value and its
   position never part."
  (:require
   [adapters.learner.documents :as documents]
   [domain.collections :as domain-collections]
   [domain.retention :as retention]
   [domain.vocabulary :as vocabulary]
   [lambdaisland.glogi :as log]))


(def empty-memory
  {:cards            []
   :collections      {}
   :examples         {}
   :examples-by-word {}
   :slot-of          {}
   :words            (sorted-map)
   ::entries         {}
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
  [memory review]
  (let [[memory slot] (card-slot memory (:word-id review))]
    (update-in memory [:cards slot :reviews] retention/with-review review)))


(defn- remove-review
  [memory {:keys [id word-id]}]
  (update-in memory [:cards (get-in memory [:slot-of word-id]) :reviews] retention/without-review id))


(defn- add-example
  [memory {:keys [id word-id] :as example}]
  (-> memory
      (assoc-in [:examples id] example)
      (update-in [:examples-by-word word-id] (fnil conj #{}) id)))


(defn- remove-example
  [memory {:keys [id word-id]}]
  (let [left (disj (get-in memory [:examples-by-word word-id]) id)]
    (-> memory
        (update :examples dissoc id)
        (update :examples-by-word #(if (empty? left) (dissoc % word-id) (assoc % word-id left))))))


(def ^:private doc-types
  "For each document type that memory keeps: its kind, and the function
   that turns a document into an entity."
  {(:type documents/collection-schema) {:entity documents/doc->collection
                                        :kind   :collection}
   (:type documents/example-schema)    {:entity documents/doc->example
                                        :kind   :example}
   (:type documents/review-schema)     {:entity documents/doc->review
                                        :kind   :review}
   (:type documents/vocab-schema)      {:entity documents/doc->word
                                        :kind   :word}})


(defn- entry
  "What memory keeps of `doc`: its kind, its entity and its rev. Returns nil
   when `doc` is a deletion, or when memory does not keep its type."
  [{rev :_rev :as doc}]
  (when-let [{:keys [kind entity]} (and (not (:_deleted doc)) (doc-types (:type doc)))]
    {:kind kind :entity (entity doc) :rev rev}))


(defn- added
  "Memory with `entry` in it. A nil entry adds nothing."
  [memory {:keys [kind entity] :as entry}]
  (if-not entry
    memory
    (-> (case kind
          :collection (assoc-in memory [:collections (:id entity)] entity)
          :example    (add-example memory entity)
          :review     (add-review memory entity)
          :word       (add-word memory entity))
        (assoc-in [::entries (:id entity)] entry))))


(defn- removed
  "Memory without `entry`. A nil entry removes nothing."
  [memory {:keys [kind entity] :as entry}]
  (if-not entry
    memory
    (-> (case kind
          :collection (update memory :collections dissoc (:id entity))
          :example    (remove-example memory entity)
          :review     (remove-review memory entity)
          :word       (remove-word memory entity))
        (update ::entries dissoc (:id entity)))))


(defn- with-doc
  "Memory after `doc` arrives: the version memory has is removed, and `doc`
   is added. If memory has this revision already, nothing changes."
  [memory {id :_id rev :_rev :as doc}]
  (let [old (get-in memory [::entries id])]
    (if (= rev (:rev old))
      memory
      (-> memory (removed old) (added (entry doc))))))


(defn- with-taken-doc
  "Memory after `doc` arrives. When memory cannot take `doc` — a document
   whose shape it does not expect — it logs it and removes the version of
   it that it held, so that memory holds what a full read of user-db
   gives. One odd document does not stop the rest. A build that starts to
   take such a document must change `adapters.learner.snapshot/format-version`:
   an older snapshot lacks it."
  [memory doc]
  (try
    (with-doc memory doc)
    (catch :default err
      (log/error :memory/document-skipped {:error (str err) :id (:_id doc)})
      (removed memory (get-in memory [::entries (:_id doc)])))))


(defn with-docs
  "Memory after `docs` arrive, one after another. If memory already has a
   document's revision, that document changes nothing. A deletion removes
   the document. Memory ignores documents of a type it does not keep, and
   leaves out, logged, one it cannot take. When no document changes
   memory, this returns the same memory.

   Every document reaches memory through this function: the load, and the
   change feed's batches and catch-ups, which also bring this app's own
   writes. The feed brings changes in the order the database stored them,
   so the last document memory takes for an id is the one the database
   holds as the winner."
  [memory docs]
  (reduce with-taken-doc memory docs))


(defn with-changes
  "Memory after the batch `docs` of user-db arrives (`with-docs`), with
   `position`, the position of the batch's last change, as its position.
   When the batch changes nothing memory keeps, such as a pairing receipt,
   this returns the same memory: its position stays, and still marks a
   change memory took with nothing it keeps stored after it."
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


(defn entries
  "What memory took from each document, one entry per document:
   `{:kind :entity :rev}`, in no particular order."
  [memory]
  (vals (::entries memory)))


(defn with-entries
  "Memory with `entries` added, as `entries` returns them. Each entry goes
   through the same path as the entry of a document that arrives. An entry
   memory cannot take throws: entries come from a snapshot, and a snapshot
   with one bad entry is not used at all."
  [memory entries]
  (reduce added memory entries))


(defn with-position
  "Memory with `position`, `{:id :rev :seq}`, as the feed position of
   user-db it has taken changes up to."
  [memory position]
  (assoc memory ::position position))


;;
;; Reads. Everything outside the learner's adapter reads memory through these
;; functions only, handed out by the learner port (`ports.learner/reads`);
;; the keys above belong to this namespace.
;;


(defn word
  "The word or phrase `word-id`, or nil when memory has none. The word
   carries `:search`, its text normalised for the filter."
  [memory word-id]
  (some->> (get-in memory [:slot-of word-id]) (nth (:cards memory)) :word))


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
  (or (some->> (get-in memory [:slot-of word-id]) (nth (:cards memory)) :reviews)
      retention/empty-reviews))


(defn collection
  "The collection `collection-id`, or nil when memory has none."
  [memory collection-id]
  (get-in memory [:collections collection-id]))


(defn collections
  "Every collection, in no particular order."
  [memory]
  (vals (:collections memory)))


(defn examples-of
  "The examples memory has for any of `word-ids`."
  [memory word-ids]
  (into [] (comp (mapcat #(get-in memory [:examples-by-word %])) (map (:examples memory))) word-ids))


(defonce ^:private slots-cache
  ;; The last answer of `collection-slots`, with what it was computed from.
  ;; A lesson in a collection asks for the collection's cards after every
  ;; answer. Finding which words a collection and its children hold walks
  ;; every collection; an answer changes only reviews, so the collections
  ;; and slots stay the same objects and the answer is reused. A changed
  ;; collection or a new word gives new objects and a new answer.
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
      (let [found (into [] (keep slots) (domain-collections/scope-word-ids (vals all) id))]
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
  ;; The last answer of `collection-words`, with what it was computed from.
  ;; Every keystroke in the words filter asks for the same collection's
  ;; words; while the words and collections stay the same objects, the
  ;; answer is reused.
  (volatile! nil))


(defn collection-words
  "The words of `collection` and of its children, in the word list's order,
   or every word when `collection` is nil. The result is kept for the last
   words, collections and collection asked about, because every keystroke
   asks again about the same collection."
  [memory collection]
  (let [words (:words memory)
        all   (:collections memory)
        id    (:id collection)
        [cached-words cached-all cached-id cached] @collection-cache]
    (if (and (identical? cached-words words) (identical? cached-all all) (= cached-id id))
      cached
      (let [ids   (some->> id (domain-collections/scope-word-ids (vals all)) set)
            found (into [] (if ids (filter #(ids (:id %))) identity) (vals words))]
        (vreset! collection-cache [words all id found])
        found))))

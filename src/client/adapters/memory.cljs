(ns adapters.memory
  "The learner's data in memory: a value built from PouchDB documents
   (ADR-0016), which `adapters.memory-loader` hands over.

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
   still knows what to remove."
  (:require
   [adapters.collections :as collections]
   [adapters.examples :as examples]
   [adapters.repository :as repository]
   [adapters.reviews :as reviews]
   [adapters.words :as words]
   [domain.retention :as retention]
   [domain.vocabulary :as vocabulary]))


(def empty-memory
  {:cards            []
   :collections      {}
   :examples         {}
   :examples-by-word {}
   :slot-of          {}
   :words            (sorted-map)
   ::entries         {}})


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
        word          (assoc word :search (vocabulary/search-text word))]
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
  {(:type collections/schema) {:entity collections/doc->collection
                               :kind   :collection}
   (:type examples/schema)    {:entity examples/doc->example
                               :kind   :example}
   (:type reviews/schema)     {:entity #(assoc (select-keys % [:created-at :retained :word-id]) :id (:_id %))
                               :kind   :review}
   (:type words/schema)       {:entity repository/entity
                               :kind   :word}})


(defn- entry
  "What memory keeps of `doc`: its kind, its entity and its rev. Returns nil
   when `doc` is a deletion, or when memory does not keep its type."
  [{rev :_rev :as doc}]
  (when-let [{:keys [kind entity]} (and (not (:_deleted doc)) (doc-types (:type doc)))]
    {:kind kind :entity (entity doc) :rev rev}))


(defn- added
  [memory {:keys [kind entity] :as entry}]
  (-> (case kind
        :collection (assoc-in memory [:collections (:id entity)] entity)
        :example    (add-example memory entity)
        :review     (add-review memory entity)
        :word       (add-word memory entity))
      (assoc-in [::entries (:id entity)] entry)))


(defn- removed
  [memory {:keys [kind entity]}]
  (-> (case kind
        :collection (update memory :collections dissoc (:id entity))
        :example    (remove-example memory entity)
        :review     (remove-review memory entity)
        :word       (remove-word memory entity))
      (update ::entries dissoc (:id entity))))


(defn- with-doc
  "Memory after doc arrives: the old version is removed, the new one added.
   If memory already holds this revision, nothing changes."
  [memory {id :_id rev :_rev :as doc}]
  (let [old (get-in memory [::entries id])]
    (if (= rev (:rev old))
      memory
      (let [new (entry doc)]
        (cond-> memory
          old (removed old)
          new (added new))))))


(defn with-docs
  "Memory after `docs` arrive, one after another. If memory already holds a
   document's revision, that document changes nothing. A deletion removes
   the document. Memory ignores documents of a type it does not keep."
  [memory docs]
  (reduce with-doc memory docs))

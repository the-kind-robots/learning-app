(ns use-cases.examples
  "Filling in the examples a device did not generate itself.

   An example is generated on the device that added the word and is never
   replicated, so a word that arrived by replication has none until this device
   asks for one. Adding a word is the only other trigger, and a replicated word
   was never added here."
  (:require
   [lambdaisland.glogi :as log]
   [utils :as utils]))


(defn- memory
  [{:keys [learner]}]
  ((:learner/memory learner)))


(defn- examples-request!
  [{:keys [learner]} requests]
  ((:learner/request-examples! learner) requests))


(defn visible-in
  "Which of `examples` a read scoped to `collection-id` sees. An example
   generated in a theme is visible only there, so a named collection sees the
   examples carrying its own id; a read outside every theme — a nil collection
   — sees all of them.

   About data, not about a screen: the scope is what was asked for, and what a
   view does with the answer is its own business. The one statement of the
   rule, read by a lesson to find the example it has for an entry and by the
   backfill to find the entries it has none for; stated twice, the two would
   disagree about what is already there.

   Pure, so the rule is checkable without a database."
  [examples collection-id]
  (filterv #(or (nil? collection-id) (= collection-id (:collection-id %))) examples))


(defn ^:async request-example-if-missing!
  "Queues a fetch of an example for `word` in `collection` when memory has
   none that a read in that collection sees.

   A fetch already queued is not read for: the queue holds one task per pair
   by construction, so asking twice writes the same id twice and the second
   write is refused."
  [{:keys [learner] :as capabilities} word {collection-id :id collection-name :name}]
  (try
    (when (empty? (visible-in ((:learner/examples-of learner) (memory capabilities) [(:id word)]) collection-id))
      (await (examples-request! capabilities
                                [{:collection-id collection-id
                                  :collection-name collection-name
                                  :word word}])))
    ;; `:default` rather than `js/Error`: a rejected PouchDB call answers with
    ;; a plain object, which `js/Error` does not catch.
    (catch :default err
      (log/warn :examples/request-failed {:error (ex-message err)}))))


(defn missing-examples
  "Every pair this device has no example for, as
   `{:collection-id :collection-name :word}`, out of what it was handed: the
   `:collections` it has, the `:entries` under consideration and the
   `:examples` it has for them.

   Every entry in a named collection wants an example carrying that
   collection; an entry in no collection wants one only when it has none at
   all. Both follow from `visible-in`.

   All of them, uncapped: this writes task documents and generates nothing —
   the pace belongs to the task queue, which runs three at a time and backs off
   on the provider's own terms. A pair already queued is named again and the
   write for it is refused, which is cheaper than reading the queue to find
   out.

   Pure, so the rule is checkable without a database."
  [{collections :collections examples :examples entries :entries}]
  (let [by-word   (group-by :word-id examples)
        by-id     (utils/index-by :id entries)
        collected (into #{} (mapcat :word-ids) collections)
        missing?  (fn [word-id collection-id]
                    (empty? (visible-in (by-word word-id) collection-id)))
        themed    (for [{:keys [id name word-ids]} collections
                        word-id word-ids
                        :when   (and (by-id word-id) (missing? word-id id))]
                    {:collection-id   id
                     :collection-name name
                     :word            (by-id word-id)})
        loose     (for [{:keys [id] :as entry} entries
                        :when (and (not (collected id)) (missing? id nil))]
                    {:word entry})]
    (vec (concat themed loose))))


(defn entries-of-pass
  "The entries one replication pass put in question: the ids it wrote, plus
   every entry named by a collection among them. A collection is recognised by
   its id matching one this device has — no document type is named here.

   A theme document is rewritten whole whenever an entry joins it anywhere, so
   a pass that brings one has to ask about the entries it names: that is how a
   word themed on another device gets that theme's example without waiting for
   the next start. The cost is the size of the theme, not of the vocabulary.

   Pure, so what a pass brought is read off what the pass carried."
  [pulled-ids collections]
  (let [arrived (set pulled-ids)]
    (into arrived
          (comp (filter (comp arrived :id))
                (mapcat :word-ids))
          collections)))


(def ^:private entries-per-task
  "How many entries the backfill weighs in one task, so that a vocabulary's
   worth after a large pass does not hold up a keystroke."
  500)


(defn- ^:async request-missing!
  "Queues a fetch for every pair without an example among `entries`, given
   `collections` and the examples memory has, and returns how many it
   queued. It weighs the entries a chunk per task. One write, however many pairs: a first synchronisation is a
   vocabulary's worth of task documents and has no business being that
   many inserts."
  [{:keys [learner] :as capabilities} collections entries]
  (if (empty? entries)
    0
    (let [requests (loop [requests []
                          chunks   (partition-all entries-per-task entries)]
                     (if-let [[chunk & more] (seq chunks)]
                       (let [requests (into requests
                                            (missing-examples
                                             {:collections collections
                                              :entries     chunk
                                              :examples    ((:learner/examples-of learner)
                                                            (memory capabilities)
                                                            (map :id chunk))}))]
                         (await (js/Promise. (fn [resolve] (js/setTimeout resolve 0))))
                         (recur requests more))
                       requests))]
      (await (examples-request! capabilities requests))
      (when (seq requests)
        (log/info :examples/missing-requested {:count (count requests)}))
      (count requests))))


(defn- ^:async contained
  "Resolves to what `request` resolves to, or to 0 when it fails.

   Contained on purpose: this runs off the back of a replication pass, and a
   pass reports what it replicated whether or not this got anywhere. Queueing
   writes task documents locally and makes no request of its own, so an
   unreachable backend leaves the tasks waiting rather than failing here."
  [request]
  (try
    (await (request))
    ;; `:default` rather than `js/Error`: a rejected PouchDB call answers with
    ;; a plain object, which `js/Error` does not catch (`db.pouch` catches the
    ;; same way for the same reason).
    (catch :default err
      (log/warn :examples/missing-request-failed {:error (ex-message err)})
      0)))


(defn ^:async request-all-missing!
  "Asks for every example this device is missing, over every entry memory
   has, once memory has everything the databases held at start. What a
   start does once, and what leaves nothing over."
  [{:keys [learner] :as capabilities}]
  (await ((:learner/loaded learner)))
  (await (contained
          (fn []
            (let [memory (memory capabilities)]
              (request-missing! capabilities
                                ((:learner/collections learner) memory)
                                (vec ((:learner/words learner) memory))))))))


(defn ^:async request-missing-for!
  "Asks for the examples missing among the entries one replication pass
   brought — `pulled-ids` are the ids that pass wrote here. The words and
   collections among them are read from PouchDB by id: memory takes them
   from the change feed, which may not have brought them yet. Ids that name
   neither a word, a phrase nor a collection fall out there.

   A collection among them is unfolded into the entries it names
   (`entries-of-pass`), so an entry themed on another device gets that
   theme's example now rather than at the next start. An entry it names
   that the pass did not bring is read from memory. A word or a collection
   the pass deleted is left out, whatever memory still has of it.

   A pass is not a reason to read the whole vocabulary again: with a throttle
   of half a minute that is regular work proportional to how much the device
   has."
  [{:keys [learner] :as capabilities} pulled-ids]
  (await ((:learner/loaded learner)))
  (await (contained
          (fn ^:async request []
            (let [memory  (memory capabilities)
                  pulled  (await ((:learner/read-stored learner) pulled-ids))
                  gone    (set (:deleted pulled))
                  arrived (utils/index-by :id (:words pulled))
                  colls   (vals (apply dissoc
                                       (merge (utils/index-by :id ((:learner/collections learner) memory))
                                              (utils/index-by :id (:collections pulled)))
                                       gone))
                  entries (into []
                                (comp (remove gone)
                                      (keep #(or (arrived %) ((:learner/word learner) memory %))))
                                (entries-of-pass pulled-ids colls))]
              (await (request-missing! capabilities colls entries)))))))


(defn start!
  "Asks for everything this device is missing and returns the listener a
   completed replication pass calls with what it brought home. A pass that
   wrote nothing here reads nothing.

   The listener answers at once and leaves the work running: a pass is what the
   screen and the throttle wait for, and it is over when the replication is,
   not when this device has finished writing task documents.

   Two of them may run at once. A pair is one task id, so the second write for
   it is refused by the database rather than by anything this has to remember."
  [capabilities]
  (request-all-missing! capabilities)
  (fn [{:keys [pulled-ids]}]
    (when (seq pulled-ids)
      (request-missing-for! capabilities pulled-ids))
    nil))

(ns use-cases.examples
  "Filling in the examples the account has none for.

   An example is generated on the device that added the word, and it
   replicates with the word. So a word that arrived by replication usually
   brings its example, and this device asks only for the pairs memory still
   has no example for once it has taken what arrived. Such a pair is one whose
   example was never fetched anywhere, or a word themed on a device that has
   not fetched for the theme yet. Adding a word is the only other trigger.

   No backfill counts, and no fetch runs, before the examples an earlier
   build kept in device-db have moved to user-db, so that a moved example
   answers its pair (`backfill-ready`)."
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

   It does not wait for the examples an earlier build kept in device-db to
   move: adding a word must not wait on device-db. The fetch it queues
   waits for the move, and finds its pair answered when the move brought
   an example for it (`start!`).

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
   queued. With `delay-ms`, the fetches become due that long from now. It
   weighs the entries a chunk per task. One write, however many pairs: a
   first synchronisation is a vocabulary's worth of task documents and has
   no business being that many inserts."
  [{:keys [learner] :as capabilities} collections entries & [delay-ms]]
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
      (await (examples-request! capabilities
                                (cond->> requests
                                  delay-ms (mapv #(assoc % :delay-ms delay-ms)))))
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
   has. What a start does once, and what leaves nothing over."
  [{:keys [learner] :as capabilities}]
  (await (contained
          (fn []
            (let [memory (memory capabilities)]
              (request-missing! capabilities
                                ((:learner/collections learner) memory)
                                (vec ((:learner/words learner) memory))))))))


(defn- pass-fetch-delay-ms
  "How long a fetch that a pass queued waits before it runs: two of the
   sync engine's push windows. A word often arrives a pass ahead of its
   example: the device that added it pushes the word, fetches the example
   and pushes that a push window later, and the poke brings it here within
   another. The pass that brings the example deletes the waiting fetch
   (`start!`). Without a push window, as for a device with no account, the
   fetch does not wait."
  [{:capabilities/keys [sync]}]
  (some-> (:sync/push-interval-ms sync) (* 2)))


(defn ^:async request-missing-for!
  "Asks for the examples missing among the entries one replication pass
   brought — `pulled-ids` are the ids that pass wrote here. It first catches
   memory up with user-db, which then holds every document the pass stored:
   the words, the collections, and the examples that came with them. So an
   example the pass brought answers its pair, and a word or a collection the
   pass deleted is gone from memory and asks for nothing. Everything after
   that is read from memory. Ids that name neither a word, a phrase nor a
   collection in memory fall out. The fetches it queues wait
   `pass-fetch-delay-ms` before they run.

   A collection among them is unfolded into the entries it names
   (`entries-of-pass`), so an entry themed on another device gets that
   theme's example now rather than at the next start.

   A pass is not a reason to read the whole vocabulary again: with a throttle
   of half a minute that is regular work proportional to how much the device
   has."
  [{:keys [learner] :as capabilities} pulled-ids]
  (await (contained
          (fn ^:async request []
            (await ((:learner/catch-up! learner)))
            (let [memory (memory capabilities)
                  colls  ((:learner/collections learner) memory)]
              (await (request-missing! capabilities
                                       colls
                                       (into []
                                             (keep #((:learner/word learner) memory %))
                                             (entries-of-pass pulled-ids colls))
                                       (pass-fetch-delay-ms capabilities))))))))


(defn- ^:async backfill-ready
  "Resolves once the backfill may count, and the fetches may run: the
   examples an earlier build kept in device-db have moved to user-db, which
   waits for memory to load, and `first-pass`, a promise of this session's
   first completed replication pass, has resolved."
  [{:keys [learner]} first-pass]
  (await ((:learner/examples-moved learner)))
  (await first-pass))


(defn start!
  "Asks for everything this device is missing once the backfill is ready
   (`backfill-ready`), holds the example fetches back until then, and
   returns the listener a completed replication pass calls with what it
   brought home: `:pulled-ids`, the entries and collections it wrote here,
   and `:pulled-examples`, the examples it wrote here.

   The first pass is the first call of that listener. A device without an
   account runs no pass, and is ready once the move is done.

   A pass deletes the queued fetches of the pairs its examples answer, at
   once, and counts what its entries are missing once the backfill is
   ready. A pass that wrote no entry counts nothing.

   The listener answers at once and leaves the work running: a pass is what the
   screen and the throttle wait for, and it is over when the replication is,
   not when this device has finished writing task documents.

   Two of them may run at once. A pair is one task id, so the second write for
   it is refused by the database rather than by anything this has to remember."
  [{:keys [learner] :capabilities/keys [sync] :as capabilities}]
  (let [passed     (atom nil)
        first-pass (if (:sync/account-id sync)
                     (js/Promise. (fn [resolve] (reset! passed resolve)))
                     (js/Promise.resolve nil))
        ready      (backfill-ready capabilities first-pass)]
    ((:learner/hold-fetches-until! learner) ready)
    (.then ready #(request-all-missing! capabilities))
    (fn [{:keys [pulled-examples pulled-ids]}]
      (when-let [resolve @passed]
        (resolve nil))
      (when (seq pulled-examples)
        (contained #((:learner/cancel-answered-fetches! learner) pulled-examples)))
      (when (seq pulled-ids)
        (.then ready #(request-missing-for! capabilities pulled-ids)))
      nil)))

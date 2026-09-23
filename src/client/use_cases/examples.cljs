(ns use-cases.examples
  "Filling in the examples a device did not generate itself.

   An example is generated on the device that added the word and is never
   replicated, so a word that arrived by replication has none until this device
   asks for one. Adding a word is the only other trigger, and a replicated word
   was never added here."
  (:require
   [lambdaisland.glogi :as log]
   [utils :as utils]))


;; One wrapper per port. They keep the double call out of the bodies below and
;; keep the knowledge of what a port key is called to one place; the prefix
;; says which port is being asked, so a call site needs no trip back to the
;; signature.

(defn- ^:async examples-of-word
  [{:keys [examples]} word-id]
  ((:examples/of-word examples) word-id))


(defn- ^:async examples-list
  [{:keys [examples]} word-ids]
  ((:examples/list examples) word-ids))


(defn- ^:async examples-request!
  [{:keys [examples]} requests]
  ((:examples/request! examples) requests))


(defn- ^:async collections-list
  [{:keys [collections]}]
  ((:collections/list collections)))


(defn- ^:async words-previews
  [{:keys [words]} word-ids]
  ((:words/previews words) word-ids))


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


(defn ^:async needs-example?
  "Whether this device still has to fetch an example for `word-id` in
   `collection-id`, asked of what it holds for that one entry — an indexed
   read and the same rule a backfill applies.

   A fetch already queued is not read for: the queue holds one task per pair
   by construction, so asking twice writes the same id twice and the second
   write is refused."
  [capabilities word-id collection-id]
  (empty? (visible-in (await (examples-of-word capabilities word-id)) collection-id)))


(defn missing-examples
  "Every pair this device holds no example for, as
   `{:collection-id :collection-name :word}`, out of what it was handed: the
   `:collections` it holds, the `:entries` under consideration and the
   `:examples` it holds for them.

   Every entry in a named collection wants an example carrying that
   collection; an entry in no collection wants one only when it has none at
   all. Both follow from `visible-in`.

   All of them, uncapped: this writes task documents and generates nothing —
   the pace belongs to the task queue, which runs three at a time and backs off
   on the provider's own terms. A pair already queued is named again and the
   write for it is refused, which is cheaper than reading the queue to find
   out.

   Pure, so the rule is checkable without a database."
  [{held-collections :collections held-examples :examples entries :entries}]
  (let [by-word   (group-by :word-id held-examples)
        by-id     (utils/index-by :id entries)
        collected (into #{} (mapcat :word-ids) held-collections)
        missing?  (fn [word-id collection-id]
                    (empty? (visible-in (by-word word-id) collection-id)))
        themed    (for [{:keys [id name word-ids]} held-collections
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
   its id matching one this device holds — no document type is named here.

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


(defn- ^:async request-missing!
  "Queues a fetch for every pair without an example among the entries
   `entries-of` names, given the collections this device holds — nil for every
   entry it holds — and returns how many it queued. One write, however many
   pairs: a first synchronisation is a vocabulary's worth of task documents
   and has no business being that many inserts.

   Contained on purpose: this runs off the back of a replication pass, and a
   pass reports what it replicated whether or not this got anywhere. Queueing
   writes task documents locally and makes no request of its own, so an
   unreachable backend leaves the tasks waiting rather than failing here."
  [capabilities entries-of]
  (try
    (let [held-collections (await (collections-list capabilities))
          entries          (await (words-previews capabilities (entries-of held-collections)))]
      (if (empty? entries)
        0
        (let [requests (missing-examples
                        {:collections held-collections
                         :entries     entries
                         :examples    (await (examples-list capabilities (mapv :id entries)))})]
          (await (examples-request! capabilities requests))
          (when (seq requests)
            (log/info :examples/missing-requested {:count (count requests)}))
          (count requests))))
    ;; `:default` rather than `js/Error`: a rejected PouchDB call answers with
    ;; a plain object, which `js/Error` does not catch (`db.pouch` catches the
    ;; same way for the same reason).
    (catch :default err
      (log/warn :examples/missing-request-failed {:error (ex-message err)})
      0)))


(defn request-all-missing!
  "Asks for every example this device is missing, over every entry it holds.
   What a start does once, and what leaves nothing over."
  [capabilities]
  (request-missing! capabilities (constantly nil)))


(defn request-missing-for!
  "Asks for the examples missing among the entries one replication pass
   brought — `pulled-ids` are the ids that pass wrote here. Ids that name
   neither a word nor a phrase fall out when the entries are read.

   A collection among them is unfolded into the entries it names
   (`entries-of-pass`), so an entry themed on another device gets that theme's
   example now rather than at the next start.

   A pass is not a reason to read the whole vocabulary again: with a throttle
   of half a minute that is regular work proportional to how much the device
   holds."
  [capabilities pulled-ids]
  (request-missing! capabilities #(vec (entries-of-pass pulled-ids %))))


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

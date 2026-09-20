(ns use-cases.collections
  (:require
   [clojure.string :as str]
   [domain.collections :as collections]))


(def max-name-length 80)


(defn ^:async summary
  "What the themes screen shows: the active-id pointer, every collection
   as the repository gives it — `{:id :name :word-ids :created-at}` — and
   the words count for «Всё подряд». Grouping and counting are the
   presenter's; no word or review document is read here."
  [{:keys [collections words]}]
  {:active-id   ((:collections/active-id collections))
   :items       (await ((:collections/list collections)))
   :total-words (await ((:words/count words)))})


(defn scope-word-ids
  "Takes every collection document, as `:collections/list` gives them, and
   the id of one of them; returns the distinct word ids in that one's
   scope — its own and every child's by name (ADR-0013). nil when no
   collection carries that id."
  [all-collections coll-id]
  (when-let [own (some #(when (= coll-id (:id %)) %) all-collections)]
    (->> all-collections
         (filter #(collections/child-of? (:name own) (:name %)))
         (cons own)
         (mapcat :word-ids)
         distinct
         vec)))


(defn ^:async active-word-ids
  "`scope-word-ids` of the active collection, read from the repository;
   nil when no collection is active or the active one is gone."
  [{:keys [collections]}]
  (when-let [coll-id ((:collections/active-id collections))]
    (scope-word-ids (await ((:collections/list collections))) coll-id)))


(defn ^:async create!
  "Creates a new collection from a raw name. Trims whitespace and clamps
   to `max-name-length`. Returns {:ok :created :id id}, {:error :invalid-name}
   for a blank name, or {:noop :duplicate} when a collection with the
   same name (trimmed, case-insensitive) already exists — the call is
   silently ignored so an accidental re-create doesn't fragment the user's
   data."
  [{:keys [collections]} raw-name]
  (let [name (some-> raw-name str/trim)]
    (if (str/blank? name)
      {:error :invalid-name}
      (let [trimmed  (subs name 0 (min (count name) max-name-length))
            existing (await ((:collections/list collections)))]
        (if (some #(collections/same-name? (:name %) trimmed) existing)
          {:noop :duplicate}
          (let [{:keys [id]} (await ((:collections/create! collections) trimmed))]
            {:ok :created :id id}))))))


(defn ^:async delete!
  "Deletes a collection, cascading to its examples in device-db so they
   don't linger as orphans. If the deleted collection was active, the
   active pointer is cleared (the implicit main card becomes active)."
  [{:keys [collections examples]} coll-id]
  (let [active-id ((:collections/active-id collections))]
    (await ((:examples/purge-by-collection! examples) coll-id))
    (await ((:collections/delete! collections) coll-id))
    (when (= active-id coll-id)
      ((:collections/set-active! collections) nil))))


(defn switch-active!
  "Marks `coll-id` (or nil for the implicit main) as the active collection."
  [{:keys [collections]} coll-id]
  ((:collections/set-active! collections) coll-id))


(defn ^:async rename-active!
  "Renames the active collection. Trims input and clamps to
   `max-name-length`; a blank value keeps the current name (no write), and
   so does a name another collection already carries by the equality
   `create!` and the folder lookup use — two documents under one name
   would show as a folder plus a stray tile. Returns {:name final-name},
   with `:noop :duplicate` when the name was taken, or nil when no
   collection is active."
  [{:keys [collections]} new-name]
  (when-let [active-id ((:collections/active-id collections))]
    (when-let [coll (await ((:collections/get collections) active-id))]
      (let [current  (:name coll)
            trimmed  (str/trim (or new-name ""))
            trimmed  (subs trimmed 0 (min (count trimmed) max-name-length))
            existing (await ((:collections/list collections)))
            taken?   (some #(and (not= (:id %) active-id)
                                 (collections/same-name? (:name %) trimmed))
                           existing)
            final    (if (or (str/blank? trimmed) taken?) current trimmed)]
        (when (not= final current)
          (await ((:collections/rename! collections) active-id final)))
        (cond-> {:name final}
          taken? (assoc :noop :duplicate))))))

(ns use-cases.collections
  (:require
   [clojure.string :as str]
   [use-cases.vocabulary :as vocabulary]
   [utils :as utils]))


(def max-name-length 80)


(defn ^:async summary
  "Snapshot of the collections domain: the active-id pointer, every named
   collection with its words resolved (sorted desc), and the implicit
   main bucket which carries the full vocabulary."
  [{:keys [collections] :as capabilities}]
  (let [items       (await ((:collections/list collections)))
        all-words   (:words (await (vocabulary/list capabilities {:order :desc})))
        vocab-index (utils/index-by :_id all-words)]
    {:active-id ((:collections/active-id collections))
     :items     (mapv #(assoc % :words (vec (keep vocab-index (:word-ids %)))) items)
     :main      {:words all-words}}))


(defn ^:async create!
  "Creates a new collection from a raw name. Trims whitespace and clamps
   to `max-name-length`. Returns {:ok :created}, {:error :invalid-name}
   for a blank name, or {:noop :duplicate} when a collection with the
   same name (case-insensitive) already exists — the call is silently
   ignored so an accidental re-create doesn't fragment the user's data."
  [{:keys [collections]} raw-name]
  (let [name (some-> raw-name str/trim)]
    (if (str/blank? name)
      {:error :invalid-name}
      (let [trimmed       (subs name 0 (min (count name) max-name-length))
            lower-trimmed (str/lower-case trimmed)
            existing      (await ((:collections/list collections)))
            duplicate?    (some #(= (str/lower-case (or (:name %) "")) lower-trimmed)
                                existing)]
        (if duplicate?
          {:noop :duplicate}
          (do (await ((:collections/create! collections) trimmed))
              {:ok :created}))))))


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
   `max-name-length`; a blank value keeps the current name (no write).
   Returns {:name final-name} or nil when no collection is active."
  [{:keys [collections]} new-name]
  (when-let [active-id ((:collections/active-id collections))]
    (when-let [coll (await ((:collections/get collections) active-id))]
      (let [current (:name coll)
            trimmed (str/trim (or new-name ""))
            trimmed (subs trimmed 0 (min (count trimmed) max-name-length))
            final   (if (str/blank? trimmed) current trimmed)]
        (when (not= final current)
          (await ((:collections/rename! collections) coll final)))
        {:name final}))))

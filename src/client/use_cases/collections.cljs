(ns use-cases.collections
  (:require
   [clojure.string :as str]
   [domain.collections :as collections]
   [ports.learner :as learner]
   [ports.world :as world]))


(defn listed
  "Every collection the learner's data in memory has, oldest first."
  [memory]
  (->> (learner/collections memory)
       (sort-by :created-at)
       vec))


(defn summary
  "What the themes screen shows, out of memory: every collection —
   `{:id :name :word-ids :created-at}` — and the words count for «Всё
   подряд». Grouping and counting are the presenter's."
  [memory]
  {:items       (listed memory)
   :total-words (learner/word-count memory)})


(defn- sendable-name
  "`raw-name` as `collections/entered-name` makes it. A cut that fell inside
   a character written as two UTF-16 units, such as an emoji, falls before
   it instead: half a character cannot be sent in a URL."
  [raw-name]
  (let [name    (collections/entered-name raw-name)
        cut?    (< (count name) (count (str/trim (or raw-name ""))))
        splits? (and cut?
                     (pos? (count name))
                     (<= 0xD800 (.charCodeAt name (dec (count name))) 0xDBFF))]
    (cond-> name
      splits? (subs 0 (dec (count name))))))


(defn- ^:async name-taken?
  "Whether a collection other than `own-id` is called `name`, by the folder lookup's equality. Asked of
   memory once caught up, so a collection a replication just brought counts."
  [learner own-id name]
  (let [memory (await (learner/fresh-memory learner))]
    (collections/name-taken? (learner/collections memory) own-id name)))


(defn ^:async create!
  "Creates a collection from a raw name, trimmed and clamped to `collections/max-name-length`. Returns
   `{:ok :created :id}`, `{:error :invalid-name}` for a blank name, or `{:noop :duplicate}` when one with the
   same name (trimmed, case-insensitive) exists, so a re-create does not fragment the data."
  [learner world raw-name]
  (let [name (sendable-name raw-name)]
    (cond
      (str/blank? name) {:error :invalid-name}
      (await (name-taken? learner nil name)) {:noop :duplicate}
      :else (let [{:keys [id]} (await (learner/insert! learner
                                                       :entity/collection
                                                       (collections/new-collection name (world/clock world :now-iso))))]
              {:ok :created :id id}))))


(defn delete!
  "Deletes a collection; its words and the examples made for it stay.
   Deleted while active, it is simply gone from memory, and «Всё подряд» is
   active."
  [learner coll-id]
  (learner/change! learner [[:entity/collection coll-id (constantly :entity/deleted)]]))


(defn switch-active!
  "Marks `coll-id` (or nil for the implicit main) as the active collection."
  [learner coll-id]
  (learner/set-active-collection! learner coll-id))


(defn ^:async rename-active!
  "Renames the active collection to the trimmed, clamped name. A blank name, or one another collection
   carries, keeps the current name and writes nothing. Returns `{:name}` with `:renamed? true` or `:noop
   :duplicate`, or nil when none is active."
  [learner world new-name]
  (when-let [{active-id :id current :name} (learner/active-collection learner)]
    (let [trimmed (sendable-name new-name)
          taken?  (await (name-taken? learner active-id trimmed))
          renamed (when-not (or (str/blank? trimmed) taken? (= trimmed current))
                    (when (await (learner/change! learner
                                                  [[:entity/collection
                                                    active-id
                                                    (fn [collection]
                                                      (some-> collection
                                                              (collections/renamed-collection trimmed (world/clock world :now-iso))))]]))
                      (learner/collection (learner/memory learner) active-id)))]
      (cond-> {:name (if renamed (:name renamed) current)}
        renamed (assoc :renamed? true)
        taken?  (assoc :noop :duplicate)))))

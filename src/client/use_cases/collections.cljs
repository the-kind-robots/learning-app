(ns use-cases.collections
  (:require
   [clojure.string :as str]
   [domain.collections :as collections]))


(defn listed
  "Every collection the learner's data in memory has, oldest first."
  [learner memory]
  (->> ((:learner/collections learner) memory)
       (sort-by :created-at)
       vec))


(defn summary
  "What the themes screen shows, out of memory: every collection —
   `{:id :name :word-ids :created-at}` — and the words count for «Всё
   подряд». Grouping and counting are the presenter's."
  [learner memory]
  {:items       (listed learner memory)
   :total-words ((:learner/word-count learner) memory)})


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
  "Whether a collection other than `own-id` is called `name`, by the
   equality that the folder lookup uses. Asked of memory once it has caught
   up with the databases, so a collection a replication brought a moment
   ago counts."
  [learner own-id name]
  (let [memory (await ((:learner/fresh-memory learner)))]
    (collections/name-taken? ((:learner/collections learner) memory) own-id name)))


(defn ^:async create!
  "Creates a new collection from a raw name. Trims whitespace and clamps
   to `collections/max-name-length`. Returns {:ok :created :id id}, {:error :invalid-name}
   for a blank name, or {:noop :duplicate} when a collection with the
   same name (trimmed, case-insensitive) already exists — the call is
   silently ignored so an accidental re-create doesn't fragment the user's
   data."
  [{:keys [learner world]} raw-name]
  (let [name (sendable-name raw-name)]
    (cond
      (str/blank? name) {:error :invalid-name}
      (await (name-taken? learner nil name)) {:noop :duplicate}
      :else (let [{:keys [id]} (await ((:learner/insert! learner)
                                          :entity/collection
                                          (collections/new-collection name ((:clock/now-iso (:world/clock world))))))]
              {:ok :created :id id}))))


(defn delete!
  "Deletes a collection; its words and the examples made for it stay.
   Deleted while active, it is simply gone from memory, and «Всё подряд» is
   active."
  [{:keys [learner]} coll-id]
  ((:learner/change! learner) :entity/collection coll-id (constantly :entity/deleted)))


(defn switch-active!
  "Marks `coll-id` (or nil for the implicit main) as the active collection."
  [{:keys [learner]} coll-id]
  ((:learner/set-active-collection! learner) coll-id))


(defn ^:async rename-active!
  "Renames the active collection. Trims input and clamps to
   `collections/max-name-length`; a blank value keeps the current name (no write), and
   so does a name another collection already carries by the equality
   `create!` and the folder lookup use — two documents under one name
   would show as a folder plus a stray tile. Returns {:name final-name},
   with `:renamed? true` when the document was written, `:noop :duplicate`
   when the name was taken, or nil when no collection is active."
  [{:keys [learner world]} new-name]
  (when-let [{active-id :id current :name} ((:learner/active-collection learner))]
    (let [trimmed (sendable-name new-name)
          taken?  (await (name-taken? learner active-id trimmed))
          renamed (when-not (or (str/blank? trimmed) taken? (= trimmed current))
                    (await ((:learner/change! learner)
                            :entity/collection
                            active-id
                            (fn [collection]
                              (some-> collection
                                      (collections/renamed-collection trimmed ((:clock/now-iso (:world/clock world)))))))))]
      (cond-> {:name (if renamed (:name renamed) current)}
        renamed (assoc :renamed? true)
        taken?  (assoc :noop :duplicate)))))

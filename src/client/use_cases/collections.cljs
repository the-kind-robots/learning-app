(ns use-cases.collections
  (:require
   [clojure.string :as str]
   [domain.collections :as collections]))


(def max-name-length 80)


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


(defn- clamped
  "`raw-name` trimmed and cut to `max-name-length`."
  [raw-name]
  (let [trimmed (str/trim (or raw-name ""))]
    (subs trimmed 0 (min (count trimmed) max-name-length))))


(defn- ^:async name-taken?
  "Whether a collection other than `own-id` is called `name`, by the
   equality that the folder lookup uses. Asked of memory once it has caught
   up with the databases, so a collection a replication brought a moment
   ago counts."
  [learner own-id name]
  (await ((:learner/catch-up! learner)))
  (boolean (some #(and (not= own-id (:id %)) (collections/same-name? (:name %) name))
                 ((:learner/collections learner) ((:learner/memory learner))))))


(defn ^:async create!
  "Creates a new collection from a raw name. Trims whitespace and clamps
   to `max-name-length`. Returns {:ok :created :id id}, {:error :invalid-name}
   for a blank name, or {:noop :duplicate} when a collection with the
   same name (trimmed, case-insensitive) already exists — the call is
   silently ignored so an accidental re-create doesn't fragment the user's
   data."
  [{:keys [learner]} raw-name]
  (let [name (clamped raw-name)]
    (cond
      (str/blank? name) {:error :invalid-name}
      (await (name-taken? learner nil name)) {:noop :duplicate}
      :else (let [{:keys [id]} (await ((:learner/create-collection! learner) name))]
              {:ok :created :id id}))))


(defn delete!
  "Deletes a collection; its words and the examples made for it stay.
   Deleted while active, it is simply gone from memory, and «Всё подряд» is
   active."
  [{:keys [learner]} coll-id]
  ((:learner/delete-collection! learner) coll-id))


(defn switch-active!
  "Marks `coll-id` (or nil for the implicit main) as the active collection."
  [{:keys [learner]} coll-id]
  ((:learner/set-active-collection! learner) coll-id))


(defn ^:async rename-active!
  "Renames the active collection. Trims input and clamps to
   `max-name-length`; a blank value keeps the current name (no write), and
   so does a name another collection already carries by the equality
   `create!` and the folder lookup use — two documents under one name
   would show as a folder plus a stray tile. Returns {:name final-name},
   with `:renamed? true` when the document was written, `:noop :duplicate`
   when the name was taken, or nil when no collection is active."
  [{:keys [learner]} new-name]
  (when-let [{active-id :id current :name} ((:learner/active-collection learner))]
    (let [trimmed (clamped new-name)
          taken?  (await (name-taken? learner active-id trimmed))
          renamed (when-not (or (str/blank? trimmed) taken? (= trimmed current))
                    (await ((:learner/rename-collection! learner) active-id trimmed)))]
      (cond-> {:name (if renamed (:name renamed) current)}
        renamed (assoc :renamed? true)
        taken?  (assoc :noop :duplicate)))))

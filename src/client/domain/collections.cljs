(ns domain.collections
  "What a collection's name says: which names are one collection, and which folder a name belongs to. The
   text before the first `/` is the folder, the rest (trimmed) is the name (ADR-0013)."
  (:require
   [clojure.string :as str]))


(defn canonical
  "The name as it is compared: trimmed and lower-cased."
  [name]
  (str/lower-case (str/trim (or name ""))))


(defn same-name?
  "Trimmed, case-insensitive equality — one rule for the duplicate check on
   create and for finding the parent of a folder."
  [a b]
  (= (canonical a) (canonical b)))


(defn folder-key
  "The text before the first `/`, trimmed, or nil when the name has none."
  [name]
  (when-let [i (some-> name (str/index-of "/"))]
    (str/trim (subs name 0 i))))


(defn child-name
  "The text after the first `/`, trimmed — what a folder row shows."
  [name]
  (str/trim (subs name (inc (str/index-of name "/")))))


(defn child-of?
  "True when `name` sits in the folder named `parent-name`."
  [parent-name name]
  (some-> (folder-key name) (same-name? parent-name)))


(defn compare-names
  "Locale-aware, case-insensitive order for names on screen."
  [a b]
  (.localeCompare (str a) (str b) js/undefined #js {:sensitivity "base"}))


(defn family
  "Takes every collection and the id of one of them; returns that one and
   every child of it by name (ADR-0013). nil when no collection carries
   that id."
  [all-collections coll-id]
  (when-let [own (some #(when (= coll-id (:id %)) %) all-collections)]
    (->> all-collections
         (filter #(child-of? (:name own) (:name %)))
         (cons own)
         vec)))


(defn family-word-ids
  "The distinct word ids in the scope of the collection `id`, its own and every child's by name (ADR-0013);
   nil when no collection carries that id."
  [all-collections coll-id]
  (some->> (family all-collections coll-id)
           (mapcat :word-ids)
           distinct
           vec))


(def max-name-length 80)


(defn entered-name
  "`raw-name` trimmed and cut to `max-name-length` characters."
  [raw-name]
  (let [trimmed (str/trim (or raw-name ""))]
    (subs trimmed 0 (min (count trimmed) max-name-length))))


(defn name-taken?
  "Whether a collection of `all-collections` other than `own-id` is called
   `name`, by the equality that the folder lookup uses."
  [all-collections own-id name]
  (boolean (some #(and (not= own-id (:id %)) (same-name? (:name %) name))
                 all-collections)))


(defn new-collection
  "A collection called `name`, with no words, made at `now`."
  [name now]
  {:created-at now
   :name       name
   :renamed-at now
   :word-ids   []})


(defn renamed-collection
  "`collection` called `name` since `now`; the very same collection when it
   is called that already."
  [collection name now]
  (if (= name (:name collection))
    collection
    (assoc collection :name name :renamed-at now)))


(defn with-word
  "`collection` listing `word-id`; the very same collection when it lists it
   already."
  [collection word-id]
  (if (some #{word-id} (:word-ids collection))
    collection
    (update collection :word-ids conj word-id)))


(defn without-word
  "`collection` not listing `word-id`; the very same collection when it does
   not list it."
  [collection word-id]
  (if (some #{word-id} (:word-ids collection))
    (update collection :word-ids (partial filterv #(not= word-id %)))
    collection))


(defn replace-word
  "`collection` listing `new-id` where it listed `old-id`, once; the very same collection when it does not
   list `old-id`."
  [collection old-id new-id]
  (if (some #{old-id} (:word-ids collection))
    (update collection :word-ids #(vec (distinct (replace {old-id new-id} %))))
    collection))


(defn merged-collection
  "The one of two versions of a collection renamed last, with both versions' word ids sorted; `tie-break`
   decides between two renamed together. The order of versions changes nothing."
  [tie-break a b]
  (let [renamed-at (fn [version]
                     [(or (:renamed-at version) (:created-at version)) (tie-break version)])
        winner     (if (neg? (compare (renamed-at a) (renamed-at b))) b a)]
    (assoc winner :word-ids (->> (concat (:word-ids a) (:word-ids b)) distinct sort vec))))

(ns domain.collections
  "What a collection's name says: which names are the same collection, and
   which folder a name belongs to. A `/` splits a name into the folder —
   the text before the first `/` — and the rest, both trimmed; deeper `/`
   stay in the rest (ADR-0013)."
  (:require
   [clojure.string :as str]))


(defn- comparable
  [name]
  (str/lower-case (str/trim (or name ""))))


(defn same-name?
  "Trimmed, case-insensitive equality — one rule for the duplicate check on
   create and for finding the parent of a folder."
  [a b]
  (= (comparable a) (comparable b)))


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

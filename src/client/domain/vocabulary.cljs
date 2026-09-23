(ns domain.vocabulary
  (:require
   [clojure.string :as str]
   [utils :as utils]))


(defn normalize-value
  [s]
  (utils/normalize-german (str s)))


(defn parse-translations
  "The entered text is one translation. It used to be split on `[,;.]`, which put
   the separator inside the data — `без того, чтобы` became two translations —
   while nothing read the pieces: grading compares against the German value and
   display glued them back with the separator they were cut on. Phrases never
   split; words no longer do either. Still a vector, so documents written before
   this are read as they are."
  [s]
  (let [value (str/trim (str s))]
    (if (str/blank? value)
      []
      [{:lang "ru" :value value}])))


(defn merge-translations
  [existing new-translations]
  (let [seen (set (map :value existing))]
    (into (vec existing)
          (remove #(seen (:value %)) new-translations))))


(def id-prefix
  "What every vocabulary id starts with (ADR-0008). Here because it is the
   naming rule itself, not a storage detail: whoever reads vocabulary by key
   range asks for it rather than spelling it again. It carries no regular
   expression metacharacter, which is what lets `adapters.words` splice it
   into the pattern its view's JavaScript is built from."
  "vocab:")


(defn vocab-id
  [value]
  (str id-prefix (normalize-value value)))


(defn- without-prefix
  [id]
  (cond-> id
    (str/starts-with? id id-prefix) (subs (count id-prefix))))


(def ^:private article
  "A German definite article at the head of a normalised value. Only these
   three and only followed by a space, so `dasselbe` and `Diebstahl` keep
   their first letter."
  #"^(?:der|die|das) ")


(defn filed-under
  "Where a word sits in the alphabetical list, read off its id: the normalised
   value without its article, then the id itself to break the tie between
   `der Zug` and a bare `Zug`.

   The id keeps the article — it is `vocab:der zug` — because it is the
   identity two devices converge on and is frozen (ADR-0008). Ordering on it
   files every noun under its article, so the reader finds no Z for `der Zug`
   (#438). `adapters.words` emits this key from the view's map function; the
   JavaScript there is the same rule said again, and a test holds the two
   together."
  [id]
  [(str/replace (without-prefix id) article "") id])


(defn new-word
  [value translations]
  {:id          (vocab-id value)
   :translation translations
   :value       value})


(defn new-review
  [word-id retained translation]
  {:word-id     word-id
   :retained    retained
   :translation [{:lang "ru" :value translation}]})


(defn update-word
  [doc translation]
  (assoc doc :translation (parse-translations translation)))

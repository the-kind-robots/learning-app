(ns domain.vocabulary
  (:require
   [clojure.string :as str]
   [utils :as utils]))


(defn normalize-value
  [s]
  (utils/normalize-german (str s)))


(defn parse-translations
  "The entered text is one translation: splitting on punctuation would put the separator into the data
   (`без того, чтобы`), and nothing reads the pieces. Still a vector, as stored documents are."
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


(defn translation-state
  "How the vocabulary stands to `translation` of a word: `existing` is nil (`:word-state/unknown-word`), has
   it (`known-with-translation`) or lacks it (`known-missing-translation`)."
  [existing translation]
  (cond
    (nil? existing)
    :word-state/unknown-word

    (some #(= translation (:value %)) (:translation existing))
    :word-state/known-with-translation

    :else
    :word-state/known-missing-translation))


(defn- stamped
  "`word` modified at `now`, and created at `now` when it has no creation time. Every word that is written is
   stamped by the function that builds it."
  [word now]
  (cond-> (assoc word :modified-at now)
    (nil? (:created-at word)) (assoc :created-at now)))


(defn merged-entry
  "`stored`, a word or phrase, with the translations of `entry` added to
   its own, modified at `now`. It keeps its kind and the rest of what it has."
  [stored entry now]
  (-> stored
      (update :translation merge-translations (:translation entry))
      (stamped now)))


(defn merged-word
  "The one word that conflicting `versions` come to: the fields of the last written by `:modified-at`, and
   the translations of every version, so a word added on two devices keeps both meanings."
  [versions]
  (let [latest (reduce (fn [a b]
                         (if (pos? (compare (:modified-at a) (:modified-at b)))
                           a
                           b))
                       versions)]
    (assoc latest :translation (reduce merge-translations [] (map :translation versions)))))


(def id-prefix
  "What every vocabulary id starts with (ADR-0008). Here because it is the
   naming rule itself, not a storage detail: whoever reads vocabulary by key
   range asks for it rather than spelling it again."
  "vocab:")


(defn vocab-id
  [value]
  (str id-prefix (normalize-value value)))


(def ^:private article
  "A German definite article at the head of a normalised value. Only these
   three and only followed by a space, so `dasselbe` and `Diebstahl` keep
   their first letter."
  #"^(?:der|die|das) ")


(defn filed-under
  "Where a word sits in the alphabetical list: the normalised value without its article, then the id. The id
   keeps the article because two devices converge on it (ADR-0008); ordering on it would file nouns under it."
  [id]
  [(str/replace (cond-> id
                  (str/starts-with? id id-prefix) (subs (count id-prefix)))
                article
                "")
   id])


(defn completes?
  "Whether `typed` is a prefix of the completion's lemma, with or without its article, or of one of its `:matched-forms`.
   Comparison is on normalised text."
  [typed {:keys [lemma matched-forms]}]
  (let [typed (normalize-value typed)
        lemma (normalize-value lemma)]
    (boolean (some #(str/starts-with? % typed)
                   (into [lemma (str/replace lemma article "")] matched-forms)))))


(defn search-text
  "What the words filter matches a query against, normalised once: the value and every translation, one per
   line, also without umlauts. A query holds no line break, so it never matches across two lines."
  [{:keys [value translation]}]
  (->> (cons value (map :value translation))
       (mapcat (fn [text]
                 (let [normal (utils/normalize-german text)
                       folded (utils/fold-german text)]
                   (if (= normal folded) [normal] [normal folded]))))
       (str/join "\n")))


(defn query
  "What the words filter looks for, normalised as `search-text` is: once per
   query, not once per word."
  [text]
  (utils/normalize-german text))


(defn matches?
  "Whether a word, by its `search-text`, holds the normalised `query` in its
   value or in one of its translations."
  [search-text query]
  (str/includes? search-text query))


(defn new-word
  [value translations]
  {:id          (vocab-id value)
   :translation translations
   :value       value})


(defn collapsed
  "`s` with every run of whitespace made a single space, and trimmed."
  [s]
  (-> (str s)
      (str/replace #"\s+" " ")
      (str/trim)))


(defn translation-entry
  "The one translation entry that `translation` makes, never split."
  [translation]
  {:lang "ru" :value (collapsed translation)})


(defn new-phrase
  "A phrase shares the vocabulary namespace: the id is the value, so the same
   text is one entry whichever kind it is, and the kind can be corrected later
   without rebuilding the document."
  [value translation]
  {:id          (vocab-id value)
   :kind        "phrase"
   :translation [(translation-entry translation)]
   :value       (collapsed value)})


(defn entered-translation
  "The translation text as it is kept for an entry of `kind`: a phrase's text
   is collapsed to single spaces, because its field is multi-line and the
   line breaks are visual only."
  [kind translation]
  (cond-> translation
    (= :phrase kind) collapsed))


(defn entered-entry
  "The word, or the phrase when `kind` is `:phrase`, that `value` and `translation` make, created at `now`; nil
   when the translation gives no entry. `pos` is the picked suggestion's part of speech; a phrase keeps none."
  [kind value translation pos now]
  (let [translation (entered-translation kind translation)]
    (some-> (if (= :phrase kind)
              (when-not (str/blank? translation)
                (new-phrase value translation))
              (when-let [entries (seq (parse-translations translation))]
                (cond-> (new-word value (vec entries))
                  pos (assoc :pos pos))))
            (stamped now))))


(defn new-review
  [word-id retained translation]
  {:word-id     word-id
   :retained    retained
   :translation [{:lang "ru" :value translation}]})


(defn edited-word
  "`word`, a word or a phrase, with the `value` and `translation` typed into the edit dialog, read as its kind
   reads them, modified at `now`."
  [word value translation now]
  (stamped
   (if (= "phrase" (:kind word))
     (assoc word :translation [(translation-entry translation)] :value (collapsed value))
     (assoc word :translation (parse-translations translation) :value (str/trim value)))
   now))

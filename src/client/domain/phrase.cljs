(ns domain.phrase
  (:require
   [clojure.string :as str]
   [domain.vocabulary :as vocabulary]))


(defn collapsed
  [s]
  (-> (str s)
      (str/replace #"\s+" " ")
      (str/trim)))


(defn phrase-id
  [value]
  (str "phrase:" (vocabulary/normalize-value value)))


(defn phrase-doc?
  [doc]
  (= "phrase" (:type doc)))


(defn translation-entry
  [translation]
  {:lang "ru" :value (collapsed translation)})


(defn new-phrase
  [value translation]
  {:_id         (phrase-id value)
   :translation [(translation-entry translation)]
   :type        "phrase"
   :value       (collapsed value)})


(def ^:private word-pair-prefixes
  "First tokens after which a two-token value still reads as a single word:
   articles (der Tisch) and the reflexive marker (sich freuen)."
  #{"das" "der" "die" "ein" "eine" "sich"})


(defn- word-lemma-match?
  [value {:keys [lemma pos]}]
  (and (not= "phrase" pos)
       (= (vocabulary/normalize-value lemma)
          (vocabulary/normalize-value value))))


(defn phrase-value?
  "True when the entered value reads as a phrase: it contains a space and is
   neither an article/sich pair nor a non-phrase dictionary lemma among the
   completions already fetched."
  [value completions]
  (let [tokens (str/split (collapsed value) #" ")]
    (and (> (count tokens) 1)
         (not (and (= 2 (count tokens))
                   (word-pair-prefixes (str/lower-case (first tokens)))))
         (not (some #(word-lemma-match? value %) completions)))))


(defn phrase-suggestion?
  "True for a completion that represents a dictionary phrase: pos=phrase and
   multi-word. Single-word phrase lemmas (hallo, hey) stay words."
  [{:keys [lemma pos]}]
  (and (= "phrase" pos)
       (str/includes? (str lemma) " ")))


(defn add-mode
  "The add form's mode: an explicit override wins, otherwise the value itself
   decides against the completions on screen."
  [value completions override]
  (or override
      (if (phrase-value? value completions) :phrase :word)))


(defn update-phrase
  "Replaces the phrase translation with one whole entry — never split."
  [doc translation]
  (assoc doc :translation [(translation-entry translation)]))

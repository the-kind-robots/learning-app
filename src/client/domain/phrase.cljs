(ns domain.phrase
  (:require
   [clojure.string :as str]
   [domain.vocabulary :as vocabulary]))


(defn collapsed
  [s]
  (-> (str s)
      (str/replace #"\s+" " ")
      (str/trim)))


(defn phrase-doc?
  "A phrase is a vocabulary document that says so. Documents without `:kind`
   are words — that is what every document written before phrases existed
   looks like."
  [doc]
  (= "phrase" (:kind doc)))


(defn translation-entry
  [translation]
  {:lang "ru" :value (collapsed translation)})


(defn new-phrase
  "A phrase shares the vocabulary namespace: the id is the value, so the same
   text is one entry whichever kind it is, and the kind can be corrected later
   without rebuilding the document."
  [value translation]
  {:_id         (vocabulary/vocab-id value)
   :kind        "phrase"
   :translation [(translation-entry translation)]
   :type        "vocab"
   :value       (collapsed value)})


(def ^:private word-pair-prefixes
  "First tokens after which a two-token value still reads as a single word:
   articles (der Tisch) and the reflexive marker (sich freuen)."
  #{"das" "der" "die" "ein" "eine" "sich"})


(defn- lemma-match?
  [value {:keys [lemma]}]
  (= (vocabulary/normalize-value lemma)
     (vocabulary/normalize-value value)))


(defn- lemma-pos
  "The part of speech the dictionary gives this exact value, when one of the
   completions already on screen is the value itself."
  [value completions]
  (some #(when (lemma-match? value %) (:pos %)) completions))


(defn- word-pair?
  [tokens]
  (and (= 2 (count tokens))
       (word-pair-prefixes (str/lower-case (first tokens)))))


(defn phrase-value?
  "True when the entered value reads as a phrase. A dictionary lemma equal to
   the value settles it either way — otherwise a space makes it a phrase,
   except for an article/`sich` pair. Typing `das heißt` in full and picking it
   from the suggestions have to agree, and the dictionary is the one that knows."
  [value completions]
  (let [tokens (str/split (collapsed value) #" ")]
    (and (> (count tokens) 1)
         (case (lemma-pos value completions)
           "phrase" true
           nil      (not (word-pair? tokens))
           false))))


(defn phrase-suggestion?
  "True for a completion that represents a dictionary phrase: pos=phrase and
   multi-word. Single-word phrase lemmas (hallo, hey) stay words."
  [{:keys [lemma pos]}]
  (and (= "phrase" pos)
       (str/includes? (str lemma) " ")))


(defn add-mode
  "The add form's mode. A picked suggestion settles it for the lemma picked —
   `das heißt` is a phrase the article exception would call a word, and the
   suggestions it came from are gone by then — but only while the value still
   is that lemma. Typing on turns it into something nobody picked, so the value
   decides again against the completions on screen (GH-358). Same lemma means
   the same normalized value: that is what makes one vocabulary document."
  [value completions {picked-mode :mode picked-value :value}]
  (or (when (and picked-mode
                 (= (vocabulary/normalize-value value)
                    (vocabulary/normalize-value picked-value)))
        picked-mode)
      (if (phrase-value? value completions) :phrase :word)))


(defn update-phrase
  "Replaces the phrase translation with one whole entry — never split."
  [doc translation]
  (assoc doc :translation [(translation-entry translation)]))

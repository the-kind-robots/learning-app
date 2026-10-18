(ns domain.phrase
  (:require
   [clojure.string :as str]
   [domain.vocabulary :as vocabulary]))


(defn phrase?
  "A phrase is a vocabulary entry that says so. Entries without `:kind` are
   words — that is what every entry written before phrases existed looks
   like."
  [word]
  (= "phrase" (:kind word)))


(defn phrase-value?
  "True when the entered value reads as a phrase. A dictionary lemma equal to the value settles it; otherwise a
   space makes it a phrase, except for an article/`sich` pair. Typing `das heißt` and picking it must agree."
  [value completions]
  (let [tokens (str/split (vocabulary/collapsed value) #" ")
        ;; The part of speech the dictionary gives this exact value, when a completion on screen is the value itself.
        pos    (some #(when (= (vocabulary/normalize-value (:lemma %))
                               (vocabulary/normalize-value value))
                        (:pos %))
                     completions)]
    (and (> (count tokens) 1)
         (case pos
           "phrase" true
           ;; An article or the reflexive marker with one more word reads as a single word: der Tisch, sich freuen.
           nil      (not (and (= 2 (count tokens))
                              (#{"das" "der" "die" "ein" "eine" "sich"} (str/lower-case (first tokens)))))
           false))))


(defn phrase-suggestion?
  "True for a completion that represents a dictionary phrase: pos=phrase and
   multi-word. Single-word phrase lemmas (hallo, hey) stay words."
  [{:keys [lemma pos]}]
  (and (= "phrase" pos)
       (str/includes? (str lemma) " ")))


(defn current-pick
  "The picked suggestion `{:mode :pos :value}` while `value` still is the lemma it was picked for, else nil:
   typing on makes it something nobody picked. The same lemma is the same normalized value, one document."
  [value pick]
  (when (and pick
             (= (vocabulary/normalize-value value)
                (vocabulary/normalize-value (:value pick))))
    pick))


(defn add-mode
  "The add form's mode. A picked suggestion settles it for the lemma picked (`das heißt` is a phrase the
   article exception would call a word, and its suggestions are gone by then: `current-pick`); otherwise
   the value decides against the completions on screen."
  [value completions mode-override]
  (or (:mode (current-pick value mode-override))
      (if (phrase-value? value completions) :phrase :word)))


(defn update-phrase
  "Replaces the phrase translation with one whole entry — never split."
  [word translation]
  (assoc word :translation [(vocabulary/translation-entry translation)]))

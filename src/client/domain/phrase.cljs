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
  "True when `value` reads as a phrase. The dictionary's part of speech `pos` of the value settles it; without one
   a space makes it a phrase, except for an article/`sich` pair. Typing `das heißt` and picking it must agree."
  [value pos]
  (let [tokens (str/split (vocabulary/collapsed value) #" ")]
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


(defn dictionary-pos
  "The part of speech the dictionary gives `value`: the picked suggestion's while `value` is its lemma (its
   suggestions are gone by then; typing on makes it something nobody picked), else a shown completion's whose
   lemma `value` is, else nil. The same lemma is the same normalized value, one document."
  [value pick completions]
  (let [normalized (vocabulary/normalize-value value)]
    (:pos (some #(when (= normalized (vocabulary/normalize-value (:lemma %))) %)
                (cons pick completions)))))

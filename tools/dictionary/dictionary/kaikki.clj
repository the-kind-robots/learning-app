(ns dictionary.kaikki
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.util.zip GZIPInputStream]))


(defn open-gz-reader
  "Opens a gzipped file and returns an io/reader. Caller must close."
  [gz-path]
  (io/reader (GZIPInputStream. (io/input-stream gz-path))))


(defn- form-of-sense?
  "True if a sense marks an inflected/declined form.
   Checks both the :form_of field (may be null in kaikki data) and the 'form-of' tag."
  [sense]
  (or (seq (:form_of sense))
      (some #(= "form-of" %) (:tags sense))))


(defn lemma-entry?
  "True if this is a real lemma, not a form-of redirect.
   Skips entries where ALL senses indicate a form (via :form_of field or form-of tag)."
  [entry]
  (let [senses (:senses entry)]
    (when (seq senses)
      (not-every? form-of-sense? senses))))


(defn german-entry?
  "True if lang_code is \"de\" and word uses standard German alphabet.
   Allows spaces (for multi-word entries), hyphens, apostrophes, periods."
  [entry]
  (and
   (= "de" (:lang_code entry))
   (re-matches
    #"[a-zA-ZäöüÄÖÜß\u00e0\u00e2\u00e9\u00e8\u00ea\u00eb\u00ee\u00ef\u00f4\u00f9\u00fb\u00e7\u00f1\u00c0\u00c2\u00c9\u00c8\u00ca\u00cb\u00ce\u00cf\u00d4\u00d9\u00db\u00c7\u00d1\s\-'.()]+"
    (:word entry ""))))


(defn extract-article
  "Returns \"der\", \"die\", \"das\", or nil for a noun entry.
   Checks entry-level tags for masculine/feminine/neuter,
   then falls back to the first form's article field."
  [entry]
  (let [tag-set   (set (:tags entry))
        from-tags (cond
                    (tag-set "masculine") "der"
                    (tag-set "feminine")  "die"
                    (tag-set "neuter")    "das")]
    (or
     from-tags
     (-> entry :forms first :article #{"der" "die" "das"}))))


(def ^:private weak-with-article-raw-tag
  "schwache Deklination mit bestimmtem Artikel")


(defn weak-decl-noun-form
  "For a substantivized adjective (e.g. \"Angestellter\", \"Beamter\"),
   returns the weak-declension nominative singular form with article —
   e.g. \"der Angestellte\", \"die Beamte\". nil for regular nouns: the
   form is unique to adjectival declension and absent from agentive
   -er nouns like \"Lehrer\"."
  [entry]
  (->> (:forms entry)
       (some (fn [{:keys [form tags raw_tags]}]
               (let [tags     (set tags)
                     raw-tags (set raw_tags)]
                 (when (and form
                            (tags "nominative")
                            (tags "singular")
                            (raw-tags weak-with-article-raw-tag))
                   form))))))


(defn canonical-noun-form
  "Returns the canonical 'article + lemma' display form for a noun entry.
   For substantivized adjectives uses the weak-decl singular (so
   \"Angestellter\" + masculine becomes \"der Angestellte\", not the
   ungrammatical \"der Angestellter\"). Otherwise falls back to
   `(str article \" \" word)`. nil when no article can be determined."
  [entry]
  (or (weak-decl-noun-form entry)
      (when-let [article (extract-article entry)]
        (str article " " (:word entry)))))


(defn entry-discriminant
  "Distinguishes homographs sharing (word, pos). Only meaningful for verbs:
   returns \"separable\" / \"inseparable\", or nil."
  [entry pos]
  (when (= "verb" pos)
    (let [tag-set (set (:tags entry))]
      (cond
        (tag-set "separable")   "separable"
        (tag-set "inseparable") "inseparable"))))


(defn russian-translations
  "Filter translations for code=\"ru\", return [{:lang \"ru\" :value word}].
   Splits compound values like \"поезд ; [4] сквозняк\" into separate entries."
  [entry]
  (->> (:translations entry)
       (filter #(= "ru" (:lang_code %)))
       (keep :word)
       (mapcat #(str/split % #"\s*;\s*"))
       (map #(str/replace % #"^\s*(\[[^\]]*\]|\(\s*\))\s*" ""))
       (map str/trim)
       (remove empty?)
       (distinct)
       (map (fn [v] {:lang "ru" :value v}))
       (vec)))


(def ^:private german-word-re
  "Single-word pattern: standard German alphabet, hyphens, apostrophes.
   Covers base Latin, umlauts, ß, and common loanword accents (é, è, ê, à, â, ç, ñ)."
  #"[a-zA-ZäöüÄÖÜß\u00e0\u00e2\u00e9\u00e8\u00ea\u00eb\u00ee\u00ef\u00f4\u00f9\u00fb\u00e7\u00f1\u00c0\u00c2\u00c9\u00c8\u00ca\u00cb\u00ce\u00cf\u00d4\u00d9\u00db\u00c7\u00d1\-']+")


(def ^:private auxiliary-forms
  "Auxiliary verb markers that Kaikki lists as forms of other verbs."
  #{"haben" "sein" "werden"})


(def ^:private cross-reference-tags
  "Form-entry tags that signal a cross-reference (related lemma) rather than
   an inflection of the current lemma. Indexing these as surface forms would
   surface unrelated lemmas — e.g. \"Mutter\" tagged feminine on the \"Vater\"
   entry would otherwise make \"mutter\" suggest \"der Vater\"."
  #{"feminine" "masculine" "abbreviation"})


(defn inflectional-form?
  "True when a Kaikki form entry represents an actual inflection of the lemma
   (not a cross-reference like a feminine/masculine counterpart or an
   abbreviation)."
  [{:keys [tags]}]
  (not-any? cross-reference-tags tags))


(defn inflected-forms
  "Extract forms[].form, deduplicate, exclude the lemma itself.
   Only keeps single words using standard German alphabet, at least 2 chars,
   excluding auxiliary verb markers and cross-reference forms (feminine /
   masculine / abbreviation)."
  [entry]
  (let [lemma (:word entry)]
    (->> (:forms entry)
         (filter inflectional-form?)
         (keep :form)
         (filter #(re-matches german-word-re %))
         (remove #(< (count %) 2))
         (remove auxiliary-forms)
         (distinct)
         (remove #{lemma})
         (vec))))

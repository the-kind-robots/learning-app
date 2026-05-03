(ns examples.dictionary
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [db :as db]
   [taoensso.telemere :as t]
   [utils :as utils]))


(defn- log-dictionary-validation-failure!
  [data]
  (t/log!
   {:level :warn
    :id    ::dictionary-validation-failed
    :data  data}
   "Examples dictionary validation failed"))


(def ^:private dictionary-db-name "dictionary-db")


(defn- assert-success!
  "Returns response on HTTP 200; throws ex-info with :status/:body on failure."
  [response]
  (if (= 200 (:status response))
    response
    (throw (ex-info "Dictionary DB error"
                    {:status (:status response)
                     :body   (:body response)}))))


(defn- exact-dictionary-entry-docs
  [normalized]
  (let [response (assert-success!
                  (db/request-sync
                   {:method :post
                    :url    (str dictionary-db-name "/_find")
                    :body   {:selector {"type"                  "dictionary-entry"
                                        "meta.normalized_value" normalized}
                             :limit    20}}))]
    (vec
     (get-in response [:body :docs]))))


(defn- surface-form-lemma-ids
  [normalized]
  (let [response (db/request-sync
                  {:method :get
                   :url    (str dictionary-db-name "/sf:" normalized)})]
    (if (= 404 (:status response))
      []
      (->> (get-in (assert-success! response) [:body :entries])
           (keep #(or (:lemma-id %) (:lemma_id %)))
           (distinct)
           (vec)))))


(defn- dictionary-entry-docs-by-id
  [ids]
  (if (empty? ids)
    []
    (let [response (assert-success!
                    (db/request-sync
                     {:method :post
                      :url    (str dictionary-db-name "/_all_docs")
                      :body   {:keys         ids
                               :include_docs true}}))]
      (->> (get-in response [:body :rows])
           (keep :doc)
           (filter #(= "dictionary-entry" (:type %)))
           (vec)))))


(defn- surface-form-dictionary-entry-docs
  [normalized]
  (dictionary-entry-docs-by-id (surface-form-lemma-ids normalized)))


(defn- merge-dictionary-entry-docs
  [& doc-groups]
  (->> doc-groups
       (remove nil?)
       (apply concat)
       (utils/distinct-by :_id)
       (vec)))


;; =============================================================================
;; Normalization
;; =============================================================================


(defn- normalize-dictionary-form
  [form]
  (some-> form str/trim str/lower-case))


(defn- normalize-russian-text
  [text]
  (some-> text
          utils/sanitize-text
          str/lower-case
          utils/non-blank))


(defn- strip-article
  [form]
  (some-> form
          str/trim
          (str/replace #"(?iu)^(der|die|das)\s+" "")))


(defn- strip-reflexive
  [form]
  (some-> form
          str/trim
          (str/replace #"(?iu)^sich\s+" "")))


(defn- canonical-dictionary-form
  [form]
  (some-> form
          normalize-dictionary-form
          strip-article
          strip-reflexive))


;; =============================================================================
;; Form matching
;; =============================================================================


(defn has-article?
  "Returns true if word is prefixed with a German definite article (der/die/das)."
  [word]
  (boolean
   (re-matches #"(?iu)(der|die|das)\s+.+" (or word ""))))


(defn same-lemma?
  "Returns true if dictionary-form refers to the same lemma as word.
   Article-bearing words (e.g. \"die Leiter\") require an exact match so that
   \"der Leiter\" and \"die Leiter\" stay distinct. Article-free words strip
   articles and reflexive pronouns before comparing, so \"vorstellen\" matches
   \"sich vorstellen\"."
  [word dictionary-form]
  (let [target          (normalize-dictionary-form word)
        dictionary-form (normalize-dictionary-form dictionary-form)]
    (when (and target dictionary-form)
      (if (has-article? word)
        ;; When the target includes an article (e.g. "die Leiter"), require an
        ;; exact match so that "der Leiter" and "die Leiter" stay distinct.
        (= dictionary-form target)
        ;; Without an article, strip articles and reflexive pronouns from both
        ;; sides before comparing so that "vorstellen" matches "sich vorstellen".
        (= (canonical-dictionary-form dictionary-form)
           (canonical-dictionary-form target))))))


;; =============================================================================
;; Translation matching
;; =============================================================================


(defn- translation-variant-set
  [translation]
  (->> (str/split (or translation "") #"[;,/]")
       (map normalize-russian-text)
       (remove nil?)
       set))


(defn- translation-match?
  [expected actual]
  (boolean
   (seq (set/intersection
         (translation-variant-set expected)
         (translation-variant-set actual)))))


;; =============================================================================
;; Dictionary lookup
;; =============================================================================


(defn lookup-dictionary-entries
  "Fetches dictionary entries for dictionary-form from the DB.
   Combines exact matches and surface-form matches for article-free words.
   Returns nil on DB error (treated as lookup unavailable, not a hard failure)."
  [dictionary-form]
  (let [normalized (normalize-dictionary-form dictionary-form)]
    (when (utils/non-blank normalized)
      (try
        (let [exact-docs (exact-dictionary-entry-docs normalized)]
          (if (has-article? dictionary-form)
            exact-docs
            (let [surface-docs (surface-form-dictionary-entry-docs normalized)]
              (cond
                (nil? exact-docs) nil
                (nil? surface-docs) exact-docs
                :else (merge-dictionary-entry-docs exact-docs surface-docs)))))
        (catch Exception error
          (log-dictionary-validation-failure!
           (merge
            {:dictionary-form normalized
             :error           (.getMessage error)}
            (ex-data error)))
          nil)))))


(defn- dictionary-entry-matches-gloss?
  [entry translation]
  (some #(and (= "ru" (:lang %))
              (translation-match? translation (:value %)))
        (:translation entry)))


;; =============================================================================
;; Public API
;; =============================================================================


(defn lookup-word-meta
  "Look up partOfSpeech and cefrLevel for a word from dictionary entries.
   Prefers entries whose Russian gloss matches any of `translations`.
   Returns nil if the lookup fails or produces no results."
  [word translations]
  (let [entries (lookup-dictionary-entries (normalize-dictionary-form word))
        matches-any-gloss? (fn [entry]
                             (some #(dictionary-entry-matches-gloss? entry %)
                                   translations))]
    (when (seq entries)
      (let [best (or (first (filter matches-any-gloss? entries))
                     (first entries))]
        {:partOfSpeech (:pos best)
         :cefrLevel    (get-in best [:meta :cefr_level])}))))


(defn lemma-in-structure?
  "Returns true if structure contains at least one item whose dictionaryForm
   is the same lemma as word."
  [word structure]
  (boolean
   (some #(same-lemma? word (:dictionaryForm %)) structure)))
